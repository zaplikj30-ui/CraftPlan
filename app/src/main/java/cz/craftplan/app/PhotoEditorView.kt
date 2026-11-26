package cz.craftplan.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal class PhotoEditorView(context: Context, private val photo: Bitmap) : View(context) {
    private val viewport = PhotoViewport(photo.width, photo.height)
    private val photoBounds = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dimensionSegments = FloatArray(28)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 120, 131, 142)
    }
    private val density = resources.displayMetrics.density
    private data class DimensionDrag(
        val pointerId: Int,
        val start: PhotoAnchor,
        val end: PhotoAnchor? = null,
        val tap: EditorPoint? = null,
        val tapTarget: DimensionTarget? = null,
    )

    private data class DimensionEdit(
        val original: PhotoDimension,
        val hit: DimensionHit,
        val pointerId: Int,
        val down: EditorPoint,
        val downAnchor: PhotoAnchor,
        val tapTarget: DimensionTarget,
        val movesLabel: Boolean = false,
        val dragThreshold: Float,
        val preview: PhotoDimension? = null,
    )

    private val lensBounds = RectF()
    private val lensOutline = Path()
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f)
    }
    private var dimensionDrag: DimensionDrag? = null
    private var dimensionEdit: DimensionEdit? = null
    private var lensOnRight: Boolean? = null
    private var lensContact: EditorPoint? = null
    private var ownsDimensionStream = false
    private val touchStream = EditorTouchStream()
    // Relative to this View, stable even while the system bar is temporarily revealed.
    var safeOverlayTopPx = 0
        set(value) {
            val next = value.coerceAtLeast(0)
            if (field == next) return
            field = next
            invalidate()
        }
    var diagnostics: EditorDiagnostics? = null
    var onDrawCounts: (Int, Int) -> Unit = { _, _ -> }
    private val activeDiagnostics get() = diagnostics?.takeIf { it.recording }
    private var pinchRecognized = false
    private var pinchChanged = false
    private var panChanged = false
    private var touchInProgress = false
    private var autoPanTime: Long? = null
    private val autoPan = Runnable { stepAutoPan() }
    var onCreateDimension: (PhotoDimension) -> Boolean = { false }
    var onChangeDimension: (PhotoDimension, PhotoDimension) -> Boolean = { _, _ -> false }
    var onSelectDimension: (UUID?) -> Unit = {}
    var onEditValue: (PhotoDimension) -> Unit = {}
    var onGestureEditing: (Boolean) -> Unit = {}
    var editable = true
    private data class DimensionLabel(
        val text: String? = null,
        val besideRuler: Boolean = false,
        val notebook: Boolean = false,
        val textWidth: Float = 0f,
        val cullingRadius: Float = 0f,
    )
    private val emptyLabel = DimensionLabel()
    private val labelHeight = labelPaint.descent() - labelPaint.ascent()
    private val noteWhitespace = Regex("\\s+")
    private var labels = emptyMap<UUID, DimensionLabel>()
    var labelsLocked = false
        set(value) {
            if (field == value) return
            field = value
            if (dimensionEdit?.movesLabel == true) clearDimensionGesture()
        }
    var fitRequest = 0
        set(value) {
            if (field == value) return
            field = value
            clearDimensionGesture()
            if (width > 0 && height > 0) viewport.fit(width, height)
            invalidate()
        }
    var showNotes = true
        set(value) {
            if (field == value) return
            field = value
            updateDimensionLabels()
            invalidate()
        }
    var scene = DimensionScene()
        set(value) {
            if (field == value) return
            val changedContent = field.dimensions !== value.dimensions
            val changedTool = field.tool != value.tool
            field = value
            if (changedContent || changedTool) {
                clearDimensionGesture()
            }
            if (changedContent) {
                val recorder = activeDiagnostics
                val started = if (recorder != null) System.nanoTime() else 0L
                updateDimensionLabels()
                viewport.updateContentBounds(value.dimensions, 24f * density)
                recorder?.let {
                    val elapsed = System.nanoTime() - started
                    it.metrics.prepareScene(elapsed)
                    it.operation(DiagnosticAction.PREPARATION, elapsed)
                }
            }
            invalidate()
        }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var clickCandidate = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var focusX = 0f
            private var focusY = 0f

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                focusX = detector.focusX
                focusY = detector.focusY
                if (!pinchRecognized) activeDiagnostics?.action(DiagnosticAction.PINCH, DiagnosticResult.START)
                pinchRecognized = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val left = viewport.left
                val top = viewport.top
                val scale = viewport.scale
                viewport.transform(focusX, focusY, detector.focusX, detector.focusY, detector.scaleFactor)
                pinchChanged = pinchChanged || left != viewport.left || top != viewport.top || scale != viewport.scale
                focusX = detector.focusX
                focusY = detector.focusY
                invalidate()
                return true
            }
        },
    ).apply {
        isQuickScaleEnabled = false
        isStylusScaleEnabled = false
    }

    init {
        contentDescription = context.getString(R.string.sample_description)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        updateDimensionLabels()
        if (touchInProgress && ownsDimensionStream) recordGestureCancellation()
        clearDimensionGesture()
        viewport.resize(width, height)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        activeDiagnostics?.action(DiagnosticAction.ORIENTATION, DiagnosticResult.APPLIED, detail = if (landscape) 1 else 0)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            if (touchInProgress) recordGestureCancellation()
            touchInProgress = false
            clearDimensionGesture()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) {
            if (touchInProgress) recordGestureCancellation()
            touchInProgress = false
            clearDimensionGesture()
        }
    }

    override fun onDetachedFromWindow() {
        clearDimensionGesture()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val recorder = activeDiagnostics
        val started = if (recorder != null) System.nanoTime() else 0L
        // AndroidView can draw on the Compose canvas without a clip at the View bounds.
        val saveCount = canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.drawColor(WORKSPACE_COLOR)
        // Always visible on the empty workspace; the opaque photo covers the dots.
        drawWorkspaceGrid(canvas)
        photoBounds.set(
            viewport.left,
            viewport.top,
            viewport.left + photo.width * viewport.scale,
            viewport.top + photo.height * viewport.scale,
        )
        canvas.drawBitmap(photo, null, photoBounds, paint)
        val drag = dimensionDrag
        val edit = dimensionEdit
        val edited = edit?.preview
        val selectedId = edited?.id ?: scene.selectedId
        var rendered = 0
        for (dimension in scene.dimensions) {
            if (dimension.id != selectedId && drawCommittedDimension(canvas, dimension, false)) rendered++
        }
        (edited ?: scene.find(selectedId))?.let {
            if (drawCommittedDimension(canvas, it, true)) rendered++
        }
        val visibleCommitted = rendered
        if (drag != null) {
            val end = drag.end
            if (end == null || drag.start == end) {
                drawAnchor(canvas, drag.start)
            } else if (drawDimension(canvas, drag.start, end, true, emptyLabel)) rendered++
        }

        val target = when {
            drag != null -> drag.end ?: drag.start
            edit?.preview != null -> when (edit.hit) {
                DimensionHit.START -> edit.preview.start
                DimensionHit.END -> edit.preview.end
                DimensionHit.BODY -> null
            }
            else -> null
        }
        val lensStart = drag?.start ?: edited?.start
        if (target != null && lensStart != null) {
            drawMagnifier(canvas, viewport.screenPoint(target), lensStart, drag?.end ?: edited?.end)
        }
        canvas.restoreToCount(saveCount)
        recorder?.let {
            val finished = System.nanoTime()
            onDrawCounts(rendered, visibleCommitted)
            it.metrics.draw(started, finished, touchInProgress)
            it.operation(DiagnosticAction.DRAW, finished - started)
        }
    }

    private fun drawWorkspaceGrid(canvas: Canvas) {
        // A visual motion cue only; not a ruler or a measurement coordinate system.
        // Keep it tied to the moving workspace, rather than to screen coordinates.
        val spacing = 44f * density
        if (spacing <= 0f) return
        val radius = 1.4f * density
        val x0 = workspaceGridOffset(viewport.left, spacing)
        val y0 = workspaceGridOffset(viewport.top, spacing)
        var y = y0
        while (y < height) {
            var x = x0
            while (x < width) {
                canvas.drawCircle(x, y, radius, gridPaint)
                x += spacing
            }
            y += spacing
        }
    }

    private fun drawCommittedDimension(canvas: Canvas, dimension: PhotoDimension, selected: Boolean) =
        drawDimension(canvas, dimension.start, dimension.end, selected, labels.getValue(dimension.id), dimension.labelT)

    private fun drawDimension(
        canvas: Canvas, start: PhotoAnchor, end: PhotoAnchor, selected: Boolean, decoration: DimensionLabel,
        labelT: Float = 0f,
    ): Boolean {
        val x1 = viewport.screenX(start)
        val y1 = viewport.screenY(start)
        val x2 = viewport.screenX(end)
        val y2 = viewport.screenY(end)
        val dx = x2 - x1
        val dy = y2 - y1
        val fraction = (1f + labelT) / 2f
        val labelX = x1 + dx * fraction
        val labelY = y1 + dy * fraction
        val showStroke = boundsMayBeVisible(x1, y1, x2, y2, 14f * density, width, height)
        val showStart = selected && boundsMayBeVisible(x1, y1, x1, y1, 16f * density, width, height)
        val showEnd = selected && boundsMayBeVisible(x2, y2, x2, y2, 16f * density, width, height)
        val showLabel = boundsMayBeVisible(
            labelX, labelY, labelX, labelY, maxOf(24f * density, decoration.cullingRadius), width, height,
        )
        if (!showStroke && !showStart && !showEnd && !showLabel) return false
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (showStroke) drawScreenDimensionLine(canvas, x1, y1, hypot(dx, dy), angle, selected)
        if (showStart) drawAnchor(canvas, start)
        if (showEnd) drawAnchor(canvas, end)
        if (!showLabel) return true
        canvas.save()
        canvas.translate(labelX, labelY)
        canvas.rotate(readableDegrees(angle))
        val label = decoration.text
        if (label == null || decoration.besideRuler) drawUnmeasuredMarker(canvas)
        if (label != null) {
            val halfWidth = decoration.textWidth / 2f + 5f * density
            val center = labelCenter(halfWidth, decoration)
            val bottom = -5f * density
            val top = bottom - labelHeight - 4f * density
            overlayPaint.color = WORKSPACE_COLOR
            canvas.drawRoundRect(center - halfWidth, top, center + halfWidth, bottom, 3f * density, 3f * density, overlayPaint)
            canvas.drawText(label, center, bottom - labelPaint.descent() - 2f * density, labelPaint)
        }
        if (decoration.notebook) {
            // A tiny notebook marker; the note text itself stays off the photograph.
            val x = notebookLeft(decoration)
            val top = -24f * density
            overlayPaint.color = Color.rgb(247, 220, 39)
            canvas.drawRoundRect(x, top, x + 15f * density, top + 17f * density,
                2f * density, 2f * density, overlayPaint)
            overlayPaint.color = Color.rgb(53, 61, 66)
            overlayPaint.strokeWidth = 1.3f * density
            for (line in 1..3) {
                val y = top + (line * 4f + 1f) * density
                canvas.drawLine(x + 4f * density, y, x + 12f * density, y, overlayPaint)
            }
        }
        canvas.restore()
        return true
    }

    private data class LabelCandidate(val hit: LabelHitDistance, val notebook: Boolean)

    private fun labelHitDistances(
        point: EditorPoint, start: EditorPoint, end: EditorPoint, decoration: DimensionLabel, labelT: Float,
    ): List<LabelCandidate> {
        val axis = end - start
        val fraction = (1f + labelT) / 2f
        val x = point.x - (start.x + axis.x * fraction)
        val y = point.y - (start.y + axis.y * fraction)
        val angle = Math.toRadians(readableAngle(axis).toDouble())
        val along = (x * cos(angle) + y * sin(angle)).toFloat()
        val across = (-x * sin(angle) + y * cos(angle)).toFloat()
        val height = maxOf(36f * density, labelHeight + 12f * density)
        val candidates = mutableListOf<LabelCandidate>()
        fun candidate(left: Float, top: Float, right: Float, bottom: Float, notebook: Boolean = false) {
            candidates += LabelCandidate(labelRectangleDistance(along, across, left, top, right, bottom), notebook)
        }
        // Retain the old generous bounds, but rank by the actual rendered rectangles.
        if (across in -height..(-2f * density)) {
            if ((decoration.text == null || decoration.besideRuler) && along in (-24f * density)..(24f * density)) {
                candidate(-14f * density, -17f * density, 14f * density, -7f * density)
            }
            decoration.text?.let {
                val half = decoration.textWidth / 2f + 5f * density
                val center = labelCenter(half, decoration)
                if (kotlin.math.abs(along - center) <= half + 3f * density) {
                    val bottom = -5f * density
                    candidate(center - half, bottom - labelHeight - 4f * density,
                        center + half, bottom)
                }
            }
        }
        if (decoration.notebook) {
            val left = notebookLeft(decoration)
            if (along in (left - 8f * density)..(left + 23f * density) &&
                across in (-32f * density)..(1f * density)
            ) candidate(left, -24f * density, left + 15f * density, -7f * density, notebook = true)
        }
        return candidates
    }

    private fun notebookLeft(decoration: DimensionLabel): Float =
        (if (decoration.text == null) 14f * density else decoration.textWidth / 2f) + 10f * density

    private fun labelCenter(halfWidth: Float, decoration: DimensionLabel): Float =
        if (decoration.besideRuler) 18f * density + halfWidth else 0f

    private fun updateDimensionLabels() {
        labels = scene.dimensions.associate { it.id to dimensionLabel(it) }
    }

    private fun dimensionLabel(dimension: PhotoDimension): DimensionLabel {
        val base = dimension.displayLabel()
        val note = dimension.visibleNote(showNotes)?.trim()?.replace(noteWhitespace, " ")
        val limit = minOf(280f * density, width * 0.65f)
        val baseWidth = base?.let(labelPaint::measureText) ?: 0f
        val noteLimit = limit - baseWidth - if (base == null) 18f * density else labelPaint.measureText(" ")
        val displayNote = if (note != null && noteLimit > 0f) {
            TextUtils.ellipsize(note, labelPaint, noteLimit, TextUtils.TruncateAt.END).toString().ifEmpty { null }
        } else null
        val text = listOfNotNull(base, displayNote).joinToString(" ").ifEmpty { null }
        val besideRuler = base == null && displayNote != null
        val notebook = dimension.note.isNotBlank() && displayNote == null
        val textWidth = text?.let(labelPaint::measureText) ?: 0f
        // Rotation cannot take the rectangle outside this radius; include glyph overhang.
        val extentX = maxOf(
            14f * density,
            if (besideRuler) textWidth + 28f * density else textWidth / 2f + 5f * density,
            if (notebook) (if (text == null) 14f * density else textWidth / 2f) + 25f * density else 0f,
        )
        val radius = extentX + maxOf(24f * density, labelHeight + 9f * density) + labelPaint.textSize
        return DimensionLabel(text, besideRuler, notebook, textWidth, radius)
    }

    private fun drawDimensionLine(canvas: Canvas, start: PhotoAnchor, end: PhotoAnchor, selected: Boolean) {
        val x1 = viewport.screenX(start)
        val y1 = viewport.screenY(start)
        val dx = viewport.screenX(end) - x1
        val dy = viewport.screenY(end) - y1
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        drawScreenDimensionLine(canvas, x1, y1, hypot(dx, dy), angle, selected)
    }

    private fun drawScreenDimensionLine(canvas: Canvas, x1: Float, y1: Float, length: Float, angle: Float, selected: Boolean) {
        canvas.save()
        canvas.translate(x1, y1)
        canvas.rotate(angle)

        if (selected) {
            overlayPaint.color = 0x8CFFFFFF.toInt()
            overlayPaint.strokeWidth = 5f * density
            canvas.drawLine(0f, 0f, length, 0f, overlayPaint)
        }
        overlayPaint.color = Color.rgb(247, 220, 39)
        overlayPaint.strokeWidth = 2f * density
        writeDimensionSegments(dimensionSegments, length, density)
        canvas.drawLines(dimensionSegments, overlayPaint)
        canvas.restore()
    }

    private fun drawAnchor(canvas: Canvas, anchor: PhotoAnchor) {
        overlayPaint.color = 0x30FFFFFF
        val x = viewport.screenX(anchor)
        val y = viewport.screenY(anchor)
        val radius = 14f * density
        canvas.drawCircle(x, y, radius, overlayPaint)
        overlayPaint.color = 0xBAFFFFFF.toInt()
        overlayPaint.style = Paint.Style.STROKE
        overlayPaint.strokeWidth = 1.4f * density
        canvas.drawCircle(x, y, radius, overlayPaint)
        overlayPaint.style = Paint.Style.FILL
    }

    private fun drawUnmeasuredMarker(canvas: Canvas) {
        canvas.save()
        canvas.translate(0f, -12f * density)
        canvas.scale(density, density)
        overlayPaint.color = Color.rgb(65, 72, 77)
        canvas.drawRoundRect(-14f, -5f, 14f, 5f, 1.5f, 1.5f, overlayPaint)
        overlayPaint.color = Color.rgb(198, 204, 208)
        canvas.drawRoundRect(-13f, -4f, 13f, 4f, 1f, 1f, overlayPaint)
        overlayPaint.color = Color.rgb(53, 61, 66)
        overlayPaint.strokeWidth = 1f
        for (tick in -10..10 step 4) {
            val bottom = if (tick == -10 || tick == 2 || tick == 10) 1f else -1f
            canvas.drawLine(tick.toFloat(), -4f, tick.toFloat(), bottom, overlayPaint)
        }
        canvas.restore()
    }

    private fun drawMagnifier(canvas: Canvas, target: EditorPoint, start: PhotoAnchor, end: PhotoAnchor?) {
        val contact = lensContact ?: return
        val margin = 8f * density
        val top = safeOverlayTopPx + margin
        val size = minOf(136f * density, width - 2f * margin, height - top - margin)
        if (size <= 0f) return
        val onRight = magnifierOnRight(contact.x, width, lensOnRight)
        lensOnRight = onRight
        val oppositeX = if (onRight) width - margin - size else margin
        val otherX = if (onRight) margin else width - margin - size
        // Keep the magnifier above the hand: select only a top corner.
        if (!positionLens(oppositeX, top, size, target, contact) &&
            !positionLens(otherX, top, size, target, contact)
        ) return

        val centerX = lensBounds.centerX()
        val centerY = lensBounds.centerY()
        val zoom = 2f
        val radius = size * (32f / 136f)
        lensOutline.rewind()
        lensOutline.addRoundRect(lensBounds, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(lensOutline)
        canvas.drawColor(WORKSPACE_COLOR)
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.scale(zoom, zoom)
        canvas.translate(-target.x, -target.y)
        canvas.drawBitmap(photo, null, photoBounds, paint)
        if (end != null && start != end) drawDimensionLine(canvas, start, end, selected = false)
        canvas.restore()

        drawCrosshair(canvas, centerX, centerY, Color.BLACK, 2.4f * density)
        drawCrosshair(canvas, centerX, centerY, Color.WHITE, 1f * density)
        canvas.restore()

        overlayPaint.color = Color.WHITE
        overlayPaint.style = Paint.Style.STROKE
        overlayPaint.strokeWidth = density
        canvas.drawRoundRect(lensBounds, radius, radius, overlayPaint)
        overlayPaint.style = Paint.Style.FILL
    }

    private fun drawCrosshair(canvas: Canvas, x: Float, y: Float, color: Int, stroke: Float) {
        crosshairPaint.color = color
        crosshairPaint.strokeWidth = stroke
        val arm = lensBounds.width() / 2f
        // Start each dashed arm at the exact target so the intersection stays visible.
        canvas.drawLine(x, y, x - arm, y, crosshairPaint)
        canvas.drawLine(x, y, x + arm, y, crosshairPaint)
        canvas.drawLine(x, y, x, y - arm, crosshairPaint)
        canvas.drawLine(x, y, x, y + arm, crosshairPaint)
    }

    private fun positionLens(
        left: Float, top: Float, size: Float, target: EditorPoint, contact: EditorPoint,
    ): Boolean {
        lensBounds.set(left, top, left + size, top + size)
        val clearance = 14f * density
        fun overlaps(point: EditorPoint): Boolean =
            point.x in (left - clearance)..(left + size + clearance) &&
                point.y in (top - clearance)..(top + size + clearance)
        return !overlaps(target) && !overlaps(contact)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        activeDiagnostics?.let { recordTouch(it, event) }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            activeDiagnostics?.metrics?.beginGesture()
            touchInProgress = true
            panChanged = false
            touchStream.begin()
            clearDimensionGesture()
            if (event.flags and MotionEvent.FLAG_CANCELED != 0) {
                touchInProgress = false
                clickCandidate = false
                activeDiagnostics?.action(DiagnosticAction.TAP, DiagnosticResult.CANCELED)
                return true
            }
            ownsDimensionStream = editable && beginDimensionGesture(event)
        }
        val canceled = event.flags and MotionEvent.FLAG_CANCELED != 0
        if (event.actionMasked == MotionEvent.ACTION_CANCEL ||
            (canceled && event.actionMasked != MotionEvent.ACTION_POINTER_UP)
        ) recordGestureCancellation()
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            touchInProgress = false
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            touchStream.beginPinch {
                if (ownsDimensionStream || dragging) recordGestureCancellation()
                pinchRecognized = false
                pinchChanged = false
                activeDiagnostics?.action(DiagnosticAction.PINCH_ATTEMPT, DiagnosticResult.START, detail = event.pointerCount)
                clearDimensionGesture()
                clickCandidate = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        // Feed the detector the original DOWN even when a dimension owns the one-finger gesture.
        scaleDetector.onTouchEvent(event)
        if (touchStream.pinchOnly) {
            if (event.actionMasked == MotionEvent.ACTION_UP && !canceled) {
                val result = when {
                    pinchChanged -> DiagnosticResult.APPLIED
                    pinchRecognized -> DiagnosticResult.UNCHANGED
                    else -> DiagnosticResult.REJECTED
                }
                activeDiagnostics?.action(
                    if (pinchRecognized) DiagnosticAction.PINCH else DiagnosticAction.PINCH_ATTEMPT, result,
                )
            }
            return true
        }
        if (ownsDimensionStream) {
            handleHeldDimension(event)
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startTracking(event, 0)
                clickCandidate = event.flags and MotionEvent.FLAG_CANCELED == 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) pan(event)
            }
            MotionEvent.ACTION_UP -> {
                if (event.flags and MotionEvent.FLAG_CANCELED == 0 &&
                    clickCandidate && hypot(event.x - lastX, event.y - lastY) < touchSlop
                ) {
                    activeDiagnostics?.action(DiagnosticAction.TAP, DiagnosticResult.MISSED)
                    onSelectDimension(null)
                    invalidate()
                    performClick()
                }
                if (dragging && event.flags and MotionEvent.FLAG_CANCELED == 0) activeDiagnostics?.action(
                    DiagnosticAction.PAN, if (panChanged) DiagnosticResult.APPLIED else DiagnosticResult.UNCHANGED,
                )
                clickCandidate = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
            MotionEvent.ACTION_CANCEL -> {
                clickCandidate = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }

    private fun inputKind(event: MotionEvent, index: Int): EditorPointerKind = when (event.getToolType(index)) {
        MotionEvent.TOOL_TYPE_FINGER -> EditorPointerKind.FINGER
        MotionEvent.TOOL_TYPE_STYLUS -> EditorPointerKind.STYLUS
        MotionEvent.TOOL_TYPE_ERASER -> EditorPointerKind.ERASER
        MotionEvent.TOOL_TYPE_MOUSE -> EditorPointerKind.MOUSE
        else -> EditorPointerKind.OTHER
    }

    private fun recordTouch(recorder: EditorDiagnostics, event: MotionEvent) {
        val kind = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> DiagnosticTouch.DOWN
            MotionEvent.ACTION_MOVE -> DiagnosticTouch.MOVE
            MotionEvent.ACTION_UP -> DiagnosticTouch.UP
            MotionEvent.ACTION_POINTER_DOWN -> DiagnosticTouch.POINTER_DOWN
            MotionEvent.ACTION_POINTER_UP -> DiagnosticTouch.POINTER_UP
            MotionEvent.ACTION_CANCEL -> DiagnosticTouch.CANCEL
            else -> return
        }
        val index = event.actionIndex
        if (index !in 0 until event.pointerCount) return
        recorder.touch(
            kind, inputKind(event, index), event.getX(index), event.getY(index), event.pointerCount,
            width, height, resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            (SystemClock.uptimeMillis() - event.eventTime).coerceAtLeast(0),
            event.flags and MotionEvent.FLAG_CANCELED != 0,
        )
    }

    private fun DimensionEdit.diagnosticAction(): DiagnosticAction = when {
        tapTarget.label -> DiagnosticAction.EDIT_LABEL
        hit == DimensionHit.BODY -> DiagnosticAction.EDIT_BODY
        hit == DimensionHit.START -> DiagnosticAction.EDIT_START
        else -> DiagnosticAction.EDIT_END
    }

    private fun recordGestureCancellation() {
        val recorder = activeDiagnostics ?: return
        val edit = dimensionEdit
        when {
            edit != null -> recorder.action(edit.diagnosticAction(), DiagnosticResult.CANCELED, edit.original.id)
            dimensionDrag != null -> recorder.action(DiagnosticAction.DRAWING, DiagnosticResult.CANCELED)
            touchStream.pinchOnly -> recorder.action(
                if (pinchRecognized) DiagnosticAction.PINCH else DiagnosticAction.PINCH_ATTEMPT, DiagnosticResult.CANCELED,
            )
            dragging -> recorder.action(DiagnosticAction.PAN, DiagnosticResult.CANCELED)
            else -> recorder.action(DiagnosticAction.TAP, DiagnosticResult.CANCELED)
        }
    }

    private fun beginDimensionGesture(event: MotionEvent): Boolean {
        val down = EditorPoint(event.x, event.y)
        val downAnchor = viewport.toPhotoAnchor(down.x, down.y) ?: run {
            activeDiagnostics?.action(DiagnosticAction.TAP, DiagnosticResult.REJECTED)
            return false
        }
        val pointerId = event.getPointerId(0)
        val profile = editorInputProfile(inputKind(event, 0), density)
        val target = if (scene.tool == DimensionTool.SINGLE) null else targetAt(down, profile)
        if (target != null && (scene.tool == DimensionTool.SELECT || target.dimension.id == scene.selectedId)) {
            val dimension = target.dimension
            // Label and notebook gestures never fall through to moving the whole dimension.
            val span = viewport.screenPoint(dimension.start).distanceTo(viewport.screenPoint(dimension.end))
            val threshold = if (target.label) profile.editDragThreshold else dimensionDragThreshold(
                span, profile.hitDensity, profile.editDragThreshold,
            )
            dimensionEdit = DimensionEdit(dimension, target.hit, pointerId, down, downAnchor, target,
                movesLabel = target.label && !target.notebook, dragThreshold = threshold).also {
                activeDiagnostics?.let { recorder ->
                    recorder.action(it.diagnosticAction(), DiagnosticResult.START, dimension.id, threshold)
                    recorder.action(DiagnosticAction.EDIT_SPAN, DiagnosticResult.START, dimension.id, span)
                }
            }
        } else if (scene.tool != DimensionTool.SELECT) {
            activeDiagnostics?.action(DiagnosticAction.DRAWING, DiagnosticResult.START)
            lensContact = down
            dimensionDrag = DimensionDrag(
                pointerId, downAnchor, tap = down.takeIf { scene.tool == DimensionTool.REPEAT },
                tapTarget = target,
            )
            invalidate()
        } else {
            return false
        }
        onGestureEditing(true)
        clickCandidate = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        return true
    }

    private fun targetAt(point: EditorPoint, profile: EditorInputProfile): DimensionTarget? {
        val recorder = activeDiagnostics
        val started = if (recorder != null) System.nanoTime() else 0L
        // Keep every eligible part. A nearby handle must not erase a direct label or line hit.
        val frontToBack = buildList {
            scene.find(scene.selectedId)?.let { add(it) }
            addAll(scene.dimensions.asReversed().filterNot { it.id == scene.selectedId })
        }
        val targets = frontToBack.flatMap { dimension ->
            val start = viewport.screenPoint(dimension.start)
            val end = viewport.screenPoint(dimension.end)
            val stroke = dimensionStrokeTargets(point, start, end, profile.hitDensity)
            val labelsAtPoint = labelHitDistances(point, start, end, labels.getValue(dimension.id), dimension.labelT)
            stroke.map {
                DimensionTarget(dimension, it.hit, false, it.distance, it.centerDistance, it.direct, gripCore = it.gripCore)
            } + labelsAtPoint.map {
                DimensionTarget(dimension, DimensionHit.BODY, true, it.hit.distance, it.hit.centerDistance,
                    direct = it.hit.distance == 0f, notebook = it.notebook)
            }
        }
        val result = preferredDimensionTarget(targets, scene.selectedId, profile.hitDensity)
        recorder?.let {
            val elapsed = System.nanoTime() - started
            it.metrics.hit(elapsed)
            it.action(DiagnosticAction.HIT_TEST, if (result == null) DiagnosticResult.MISSED else DiagnosticResult.APPLIED,
                result?.dimension?.id, elapsed)
        }
        return result
    }

    private fun selectTarget(targetAtDown: DimensionTarget?) {
        // Use the target captured at DOWN, not a different neighbor at UP.
        val currentTarget = targetAtDown?.takeIf { scene.find(it.dimension.id) == it.dimension }
        val decision = decideDimensionTap(currentTarget, scene.selectedId)
        if (decision == null) {
            activeDiagnostics?.action(DiagnosticAction.TAP, DiagnosticResult.MISSED)
            onSelectDimension(null)
            return
        }
        val target = decision.target.dimension
        val wasSelected = target.id == scene.selectedId
        onSelectDimension(target.id)
        activeDiagnostics?.action(
            DiagnosticAction.SELECT, if (wasSelected) DiagnosticResult.UNCHANGED else DiagnosticResult.APPLIED, target.id,
        )
        if (decision.openValue) onEditValue(target)
        performClick()
    }

    private fun handleHeldDimension(event: MotionEvent) {
        val heldId = dimensionDrag?.pointerId ?: dimensionEdit?.pointerId ?: return
        val canceled = event.flags and MotionEvent.FLAG_CANCELED != 0
        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL -> clearDimensionGesture()
            MotionEvent.ACTION_MOVE -> {
                val heldIndex = event.findPointerIndex(heldId)
                if (canceled || heldIndex < 0) {
                    if (!canceled) recordGestureCancellation()
                    clearDimensionGesture()
                    return
                }
                val contact = viewport.clampContact(event.getX(heldIndex), event.getY(heldIndex)) ?: return
                val edit = dimensionEdit
                if (edit == null || edit.preview != null ||
                    passesDragThreshold(edit.down, contact, edit.dragThreshold)
                ) {
                    if (edit != null || dimensionDrag?.tap?.let {
                            passesDragThreshold(it, contact, touchSlop.toFloat())
                        } == true
                    ) {
                        dimensionDrag = dimensionDrag?.copy(tap = null)
                    }
                    updateDraft(contact)
                    resumeAutoPanIfDragging()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (canceled || event.getPointerId(0) != heldId) clearDimensionGesture()
                else finishHeldDimension(viewport.clampContact(event.x, event.y))
            }
        }
    }

    private fun finishHeldDimension(contact: EditorPoint?) {
        val draft = dimensionDrag
        val edit = dimensionEdit
        clearDimensionGesture()
        if (contact == null) {
            activeDiagnostics?.action(DiagnosticAction.TAP, DiagnosticResult.REJECTED)
            return
        }
        if (draft != null && scene.tool != DimensionTool.SELECT) {
            val result = DimensionPlacement.Ready(PhotoId.SYNTHETIC_WORKBENCH)
                .finishDrag(draft.start, viewport.dragEnd(draft.start, contact.x, contact.y, touchSlop))
            if (result != null) {
                val applied = onCreateDimension(result.dimension)
                activeDiagnostics?.action(
                    DiagnosticAction.DRAWING, if (applied) DiagnosticResult.APPLIED else DiagnosticResult.REJECTED,
                    result.dimension.id,
                )
            } else if (draft.tap != null && !passesDragThreshold(draft.tap, contact, touchSlop.toFloat())) {
                activeDiagnostics?.action(DiagnosticAction.DRAWING, DiagnosticResult.UNCHANGED)
                selectTarget(draft.tapTarget)
            } else activeDiagnostics?.action(DiagnosticAction.DRAWING, DiagnosticResult.REJECTED)
        } else if (edit != null) {
            if (edit.preview != null || passesDragThreshold(edit.down, contact, edit.dragThreshold)) {
                onSelectDimension(edit.original.id)
                val changed = editedDimension(edit, contact)
                val applied = onChangeDimension(edit.original, changed)
                val result = when {
                    applied -> DiagnosticResult.APPLIED
                    edit.original == changed -> DiagnosticResult.UNCHANGED
                    else -> DiagnosticResult.REJECTED
                }
                activeDiagnostics?.action(edit.diagnosticAction(), result, edit.original.id, edit.down.distanceTo(contact))
            } else {
                activeDiagnostics?.action(
                    edit.diagnosticAction(), DiagnosticResult.UNCHANGED, edit.original.id, edit.down.distanceTo(contact),
                )
                selectTarget(edit.tapTarget)
            }
        }
    }

    private fun editedDimension(draft: DimensionEdit, contact: EditorPoint): PhotoDimension {
        val delta = contact - viewport.screenPoint(draft.downAnchor)
        val original = draft.original
        if (draft.tapTarget.notebook) return original
        if (!draft.movesLabel) return viewport.moveDimension(original, draft.hit, delta)
        val axis = viewport.screenPoint(original.end) - viewport.screenPoint(original.start)
        return original.shiftLabel(axis, delta, labelsLocked)
    }

    private fun updateDraft(contact: EditorPoint) {
        lensContact = contact
        dimensionDrag = dimensionDrag?.let { it.copy(end = viewport.toPhotoAnchor(contact.x, contact.y)) }
        dimensionEdit = dimensionEdit?.let { it.copy(preview = editedDimension(it, contact)) }
        invalidate()
    }

    private fun autoPanVelocity(contact: EditorPoint): EditorPoint {
        // Keep the usable top edge reachable without requiring a system-bar gesture.
        // The stable inset changes neither photo coordinates nor the actual viewport.
        val top = safeOverlayTopPx.coerceIn(0, height.coerceAtLeast(0))
        val usableHeight = height - top
        return EditorPoint(
            edgePanSpeed(contact.x, width, 48f * density, 440f * density),
            edgePanSpeed(
                contact.y - top, usableHeight,
                verticalAutoPanEdgeSize(
                    usableHeight, density, resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
                ),
                440f * density,
            ),
        )
    }

    private fun resumeAutoPanIfDragging() {
        val contact = lensContact
        val drag = dimensionDrag
        val edit = dimensionEdit
        val canPan = ownsDimensionStream && contact != null &&
            if (drag != null) {
                passesDragThreshold(viewport.screenPoint(drag.start), contact, touchSlop.toFloat())
            } else {
                edit?.preview != null && edit.hit != DimensionHit.BODY
            }
        if (!canPan || autoPanVelocity(contact) == EditorPoint(0f, 0f)) {
            stopAutoPan()
            return
        }
        if (autoPanTime != null) return
        autoPanTime = SystemClock.uptimeMillis()
        invalidate()
        postOnAnimation(autoPan)
    }

    private fun stopAutoPan() {
        val wasMoving = autoPanTime != null
        autoPanTime = null
        removeCallbacks(autoPan)
        if (wasMoving) invalidate()
    }

    private fun stepAutoPan() {
        val previous = autoPanTime ?: return
        // A scheduled callback must never continue without an active held pointer.
        if (!ownsDimensionStream ||
            (dimensionDrag == null && dimensionEdit == null)
        ) {
            stopAutoPan()
            return
        }
        autoPanTime = null
        val contact = lensContact ?: run {
            invalidate()
            return
        }
        val velocity = autoPanVelocity(contact)
        if (velocity == EditorPoint(0f, 0f)) {
            invalidate()
            return
        }
        val now = SystemClock.uptimeMillis()
        val seconds = (now - previous).coerceIn(1L, 32L) / 1000f
        val left = viewport.left
        val top = viewport.top
        viewport.transform(0f, 0f, velocity.x * seconds, velocity.y * seconds, extendingForDrag = true)
        if (viewport.left != left || viewport.top != top) {
            updateDraft(contact)
            autoPanTime = now
            postOnAnimation(autoPan)
        } else {
            invalidate()
        }
    }

    private fun clearDimensionGesture() {
        val needsRedraw = dimensionDrag != null || dimensionEdit != null
        dimensionDrag = null
        dimensionEdit = null
        lensOnRight = null
        lensContact = null
        ownsDimensionStream = false
        stopAutoPan()
        if (needsRedraw) {
            onGestureEditing(false)
            invalidate()
        }
    }

    override fun performClick(): Boolean = super.performClick()

    private fun startTracking(event: MotionEvent, index: Int) {
        activePointerId = event.getPointerId(index)
        lastX = event.getX(index)
        lastY = event.getY(index)
        dragging = false
    }

    private fun pan(event: MotionEvent) {
        val index = event.findPointerIndex(activePointerId)
        if (index < 0) return
        val x = event.getX(index)
        val y = event.getY(index)
        if (!dragging && hypot(x - lastX, y - lastY) < touchSlop) return

        if (!dragging) activeDiagnostics?.action(DiagnosticAction.PAN, DiagnosticResult.START)
        dragging = true
        clickCandidate = false
        val previousLeft = viewport.left
        val previousTop = viewport.top
        viewport.transform(lastX, lastY, x, y)
        panChanged = panChanged || previousLeft != viewport.left || previousTop != viewport.top
        lastX = x
        lastY = y
        invalidate()
    }

    private companion object {
        val WORKSPACE_COLOR = 0xFF17191C.toInt()
    }
}
