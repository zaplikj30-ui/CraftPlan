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

internal class PhotoEditorView(context: Context, private val photo: Bitmap, media: StoredPhoto) : View(context) {
    private val viewport = photoDimensions(media.width, media.height, media.storedOrientation).let { (w, h) ->
        PhotoViewport(w, h)
    }
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
        val inlineDragMode: InlineLabelDragMode? = null,
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
    // Fixed toolbar top in this View's pixels; the extra row is deliberately ignored.
    var fixedToolbarTopPx: Int? = null
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
        val text: String?,
        val geometry: DimensionLabelGeometry,
    )
    private val labelHeight = labelPaint.descent() - labelPaint.ascent()
    private val emptyAboveLabel = DimensionLabel(null, dimensionLabelGeometry(density, null, labelHeight, false, false))
    private val emptyInlineLabel = emptyAboveLabel.copy(geometry = emptyAboveLabel.geometry.centeredOnLine())
    private val emptyLabel get() = if (appearance.inline) emptyInlineLabel else emptyAboveLabel
    var appearance = DimensionAppearance()
        set(value) {
            if (field == value) return
            val layoutChanged = field.inline != value.inline
            field = value
            if (layoutChanged) {
                clearDimensionGesture()
                updateDimensionLabels()
            }
            invalidate()
        }
    // Keep the 15sp layout cached. Scale the entire decoration (including icons,
    // background and spacing) together without rebuilding labels on each pinch frame.
    private val dimensionLabelScale: Float
        get() = viewport.dimensionLabelScale(density)
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
            viewport.photoRight,
            viewport.photoBottom,
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
            drawMagnifier(canvas, viewport.screenPoint(target), lensStart, drag?.end ?: edited?.end, edited?.id)
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

    private fun drawCommittedDimension(
        canvas: Canvas, dimension: PhotoDimension, selected: Boolean, inMagnifier: Boolean = false,
    ) = drawDimension(canvas, dimension.start, dimension.end, selected,
        labels.getValue(dimension.id), dimension.labelT, inMagnifier)

    private fun dimensionBoundsVisible(
        canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, padding: Float, inMagnifier: Boolean,
    ): Boolean = if (inMagnifier) {
        // The lens samples screen coordinates around its target, not around its overlay position.
        // Use the transformed Canvas clip, including when the sample extends past a View edge.
        !canvas.quickReject(minOf(x1, x2) - padding, minOf(y1, y2) - padding,
            maxOf(x1, x2) + padding, maxOf(y1, y2) + padding)
    } else {
        boundsMayBeVisible(x1, y1, x2, y2, padding, width, height)
    }

    private fun drawDimension(
        canvas: Canvas, start: PhotoAnchor, end: PhotoAnchor, selected: Boolean, decoration: DimensionLabel,
        labelT: Float = 0f, inMagnifier: Boolean = false,
    ): Boolean {
        val x1 = viewport.screenX(start)
        val y1 = viewport.screenY(start)
        val x2 = viewport.screenX(end)
        val y2 = viewport.screenY(end)
        val dx = x2 - x1
        val dy = y2 - y1
        val length = hypot(dx, dy)
        val labelScale = dimensionLabelScale
        val labelPadding = maxOf(24f * density, decoration.geometry.cullingRadius + labelPaint.textSize) *
            decoration.geometry.cullingScale(labelScale)
        val strokeDensity = density * viewport.dimensionStrokeScale
        val handlePadding = anchorRadius + 0.7f * density
        // Reject distant objects before constructing inline spans; the outside
        // label can extend by at most half the line plus its full group width.
        val broadPadding = if (appearance.inline)
            length * 0.46f + 3f * labelPadding + handlePadding + 14f * strokeDensity + 2f * density
            else maxOf(labelPadding, handlePadding, 14f * strokeDensity)
        if (!dimensionBoundsVisible(canvas, x1, y1, x2, y2, broadPadding, inMagnifier)) return false
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val placement = labelPlacement(decoration.geometry, length, angle, labelT)
        val fraction = if (length > 0f) placement.distance / length else 0.5f
        val labelX = x1 + dx * fraction
        val labelY = y1 + dy * fraction
        val extension = placement.bodySpans?.maxOfOrNull { maxOf(-it.start, it.end - length, 0f) } ?: 0f
        val showStroke = dimensionBoundsVisible(canvas, x1, y1, x2, y2,
            14f * strokeDensity + extension, inMagnifier)
        val showStart = selected && dimensionBoundsVisible(canvas, x1, y1, x1, y1, handlePadding, inMagnifier)
        val showEnd = selected && dimensionBoundsVisible(canvas, x2, y2, x2, y2, handlePadding, inMagnifier)
        val showLabel = dimensionBoundsVisible(canvas, labelX, labelY, labelX, labelY,
            labelPadding, inMagnifier)
        if (!showStroke && !showStart && !showEnd && !showLabel) return false
        if (showStroke) drawScreenDimensionLine(canvas, x1, y1, length, angle, selected,
            strokeDensity, placement.bodySpans)
        if (showStart) drawAnchor(canvas, start)
        if (showEnd) drawAnchor(canvas, end)
        if (!showLabel) return true
        canvas.save()
        canvas.translate(labelX, labelY)
        canvas.rotate(readableDegrees(angle))
        canvas.scale(labelScale, labelScale)
        val label = decoration.text
        val geometry = decoration.geometry
        if (geometry.ruler != null) {
            canvas.save()
            val rulerRatio = geometry.rulerScale(labelScale) / labelScale
            canvas.scale(rulerRatio, rulerRatio)
            drawUnmeasuredMarker(canvas, geometry.ruler)
            canvas.restore()
        }
        // Move only adjacent decorations to keep the gap beside the larger ruler.
        canvas.translate(geometry.rulerNeighborOffset(labelScale) / labelScale, 0f)
        val textBounds = geometry.text
        if (label != null && textBounds != null) {
            if (!appearance.transparent) {
                overlayPaint.color = WORKSPACE_COLOR
                canvas.drawRoundRect(textBounds.left, textBounds.top, textBounds.right, textBounds.bottom,
                    3f * density, 3f * density, overlayPaint)
            }
            val baseline = textBounds.bottom - labelPaint.descent() - 2f * density
            canvas.save()
            canvas.translate(textBounds.centerX, baseline)
            // Use the same cached text metrics for both rendering passes.
            labelPaint.color = Color.BLACK
            labelPaint.style = Paint.Style.STROKE
            labelPaint.strokeJoin = Paint.Join.ROUND
            labelPaint.strokeWidth = 2f * density
            canvas.drawText(label, 0f, 0f, labelPaint)
            labelPaint.style = Paint.Style.FILL
            labelPaint.color = Color.WHITE
            canvas.drawText(label, 0f, 0f, labelPaint)
            canvas.restore()
        }
        geometry.notebook?.let { bounds ->
            // A tiny notebook marker; the note text itself stays off the photograph.
            val x = bounds.left
            val top = bounds.top
            overlayPaint.color = Color.rgb(247, 220, 39)
            canvas.drawRoundRect(x, top, bounds.right, bounds.bottom,
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

    private fun labelPlacement(
        geometry: DimensionLabelGeometry, length: Float, angle: Float, labelT: Float,
    ): DimensionLabelPlacement {
        val strokeDensity = density * viewport.dimensionStrokeScale
        // Protect the full existing selected handle and the inward arrowheads,
        // accounting for square caps and the selected-body highlight as well.
        val halfStroke = maxOf(viewport.dimensionOutlineWidth, 5f) * strokeDensity / 2f
        val clearance = maxOf(anchorRadius, minOf(9f * strokeDensity, length / 4f) + halfStroke)
        return placeDimensionLabel(geometry, length, labelT, dimensionLabelScale,
            angle > 90f || angle <= -90f, appearance.inline, clearance, halfStroke + 2f * density)
    }

    private fun labelHitDistances(
        point: EditorPoint, start: EditorPoint, end: EditorPoint, decoration: DimensionLabel,
        placement: DimensionLabelPlacement,
    ): List<LabelCandidate> {
        val axis = end - start
        val length = hypot(axis.x, axis.y)
        val fraction = if (length > 0f) placement.distance / length else 0.5f
        val x = point.x - (start.x + axis.x * fraction)
        val y = point.y - (start.y + axis.y * fraction)
        val angle = Math.toRadians(readableAngle(axis).toDouble())
        val along = (x * cos(angle) + y * sin(angle)).toFloat()
        val across = (-x * sin(angle) + y * cos(angle)).toFloat()
        val labelScale = dimensionLabelScale
        val geometry = decoration.geometry
        val candidates = mutableListOf<LabelCandidate>()
        val neighborOffset = geometry.rulerNeighborOffset(labelScale)
        fun candidate(bounds: LabelRect?, touch: LabelRect?, scale: Float, offset: Float,
            notebook: Boolean = false) {
            if (bounds == null || touch == null) return
            bounds.hit(along - offset, across, scale, touch)?.let {
                candidates += LabelCandidate(it, notebook)
            }
        }
        candidate(geometry.ruler, geometry.rulerTouch, geometry.rulerScale(labelScale), 0f)
        candidate(geometry.text, geometry.textTouch, labelScale, neighborOffset)
        candidate(geometry.notebook, geometry.notebookTouch, labelScale, neighborOffset, notebook = true)
        return candidates
    }

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
        val geometry = dimensionLabelGeometry(density, text?.let { textWidth },
            labelHeight, besideRuler, notebook)
        return DimensionLabel(text, if (appearance.inline) geometry.centeredOnLine() else geometry)
    }

    private fun drawDimensionLine(canvas: Canvas, start: PhotoAnchor, end: PhotoAnchor, selected: Boolean) {
        val x1 = viewport.screenX(start)
        val y1 = viewport.screenY(start)
        val dx = viewport.screenX(end) - x1
        val dy = viewport.screenY(end) - y1
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        drawScreenDimensionLine(canvas, x1, y1, hypot(dx, dy), angle, selected, density * viewport.dimensionStrokeScale)
    }

    private fun drawScreenDimensionLine(
        canvas: Canvas, x1: Float, y1: Float, length: Float, angle: Float, selected: Boolean, strokeDensity: Float,
        bodySpans: List<DimensionLineSpan>? = null,
    ) {
        canvas.save()
        canvas.translate(x1, y1)
        canvas.rotate(angle)

        fun drawBody() {
            if (bodySpans == null) canvas.drawLine(0f, 0f, length, 0f, overlayPaint)
            else for (span in bodySpans) canvas.drawLine(span.start, 0f, span.end, 0f, overlayPaint)
        }
        if (selected) {
            overlayPaint.color = 0x8CFFFFFF.toInt()
            overlayPaint.strokeWidth = 5f * strokeDensity
            drawBody()
        }
        writeDimensionSegments(dimensionSegments, length, strokeDensity, viewport.dimensionTickScale)
        // Matching square caps keep a contrasting border on the ends without round bulges.
        overlayPaint.color = Color.BLACK
        overlayPaint.strokeWidth = viewport.dimensionOutlineWidth * strokeDensity
        overlayPaint.strokeCap = Paint.Cap.SQUARE
        if (bodySpans == null) canvas.drawLines(dimensionSegments, overlayPaint)
        else {
            drawBody()
            canvas.drawLines(dimensionSegments, 4, 24, overlayPaint)
        }
        overlayPaint.color = Color.rgb(247, 220, 39)
        overlayPaint.strokeWidth = viewport.dimensionFillWidth * strokeDensity
        if (bodySpans == null) canvas.drawLines(dimensionSegments, overlayPaint)
        else {
            drawBody()
            canvas.drawLines(dimensionSegments, 4, 24, overlayPaint)
        }
        overlayPaint.strokeCap = Paint.Cap.BUTT
        canvas.restore()
    }

    private val anchorRadius: Float
        get() = dimensionHandleRadius(density, viewport.dimensionStrokeScale,
            viewport.dimensionTickScale, viewport.dimensionOutlineWidth)

    private fun drawAnchor(canvas: Canvas, anchor: PhotoAnchor) {
        overlayPaint.color = 0x30FFFFFF
        val x = viewport.screenX(anchor)
        val y = viewport.screenY(anchor)
        val radius = anchorRadius
        canvas.drawCircle(x, y, radius, overlayPaint)
        overlayPaint.color = 0xBAFFFFFF.toInt()
        overlayPaint.style = Paint.Style.STROKE
        overlayPaint.strokeWidth = 1.4f * density
        canvas.drawCircle(x, y, radius, overlayPaint)
        overlayPaint.style = Paint.Style.FILL
    }

    private fun drawUnmeasuredMarker(canvas: Canvas, bounds: LabelRect) {
        canvas.save()
        canvas.translate(bounds.centerX, (bounds.top + bounds.bottom) / 2f)
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

    private fun drawMagnifier(
        canvas: Canvas, target: EditorPoint, start: PhotoAnchor, end: PhotoAnchor?, editedId: UUID?,
    ) {
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
        for (dimension in scene.dimensions) {
            // The active preview replaces its committed geometry; never show both.
            if (dimension.id != editedId) {
                drawCommittedDimension(canvas, dimension, selected = false, inMagnifier = true)
            }
        }
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
        inlineDragMode == InlineLabelDragMode.BODY -> DiagnosticAction.EDIT_BODY
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
            // Above-line labels and notebooks keep their established gestures.
            // Inline movable labels defer direction arbitration until the drag threshold.
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

    // Read-only arbitration before Compose claims the top grip. Reuse the
    // normal editor resolver and input profile; do not broaden hit tolerances.
    internal fun hasToolbarGesturePriority(x: Float, y: Float, input: EditorPointerKind): Boolean {
        if (!editable || x !in 0f..width.toFloat() || y !in 0f..height.toFloat()) return false
        if (scene.tool != DimensionTool.SELECT) return true
        return targetAt(EditorPoint(x, y), editorInputProfile(input, density), recordDiagnostics = false) != null
    }

    private fun targetAt(
        point: EditorPoint, profile: EditorInputProfile, recordDiagnostics: Boolean = true,
    ): DimensionTarget? {
        val recorder = if (recordDiagnostics) activeDiagnostics else null
        val started = if (recorder != null) System.nanoTime() else 0L
        // Keep every eligible part. A nearby handle must not erase a direct label or line hit.
        val frontToBack = buildList {
            scene.find(scene.selectedId)?.let { add(it) }
            addAll(scene.dimensions.asReversed().filterNot { it.id == scene.selectedId })
        }
        val strokeScale = viewport.dimensionStrokeScale
        val tickScale = viewport.dimensionTickScale
        val targets = frontToBack.flatMap { dimension ->
            val start = viewport.screenPoint(dimension.start)
            val end = viewport.screenPoint(dimension.end)
            val decoration = labels.getValue(dimension.id)
            val axis = end - start
            val angle = Math.toDegrees(atan2(axis.y.toDouble(), axis.x.toDouble())).toFloat()
            val placement = labelPlacement(decoration.geometry, hypot(axis.x, axis.y), angle, dimension.labelT)
            val stroke = dimensionStrokeTargets(point, start, end, profile.hitDensity, strokeScale, tickScale,
                placement.bodySpans)
            val labelsAtPoint = labelHitDistances(point, start, end, decoration, placement)
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
            val result = DimensionPlacement.Ready(scene.photoId)
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
                val resolved = resolveEditDrag(edit, contact)
                onSelectDimension(edit.original.id)
                val changed = editedDimension(resolved, contact)
                val applied = onChangeDimension(edit.original, changed)
                val result = when {
                    applied -> DiagnosticResult.APPLIED
                    edit.original == changed -> DiagnosticResult.UNCHANGED
                    else -> DiagnosticResult.REJECTED
                }
                activeDiagnostics?.action(resolved.diagnosticAction(), result, edit.original.id, edit.down.distanceTo(contact))
            } else {
                activeDiagnostics?.action(
                    edit.diagnosticAction(), DiagnosticResult.UNCHANGED, edit.original.id, edit.down.distanceTo(contact),
                )
                selectTarget(edit.tapTarget)
            }
        }
    }

    private fun resolveEditDrag(draft: DimensionEdit, contact: EditorPoint): DimensionEdit {
        if (!appearance.inline || !draft.movesLabel || draft.inlineDragMode != null) return draft
        val axis = viewport.screenPoint(draft.original.end) - viewport.screenPoint(draft.original.start)
        val mode = resolveInlineLabelDrag(null, axis, contact - draft.down, draft.dragThreshold, labelsLocked)
            ?: return draft
        return draft.copy(inlineDragMode = mode)
    }

    private fun editedDimension(draft: DimensionEdit, contact: EditorPoint): PhotoDimension {
        val delta = contact - viewport.screenPoint(draft.downAnchor)
        val original = draft.original
        if (draft.tapTarget.notebook) return original
        if (!draft.movesLabel) return viewport.moveDimension(original, draft.hit, delta)
        if (appearance.inline) {
            when (draft.inlineDragMode) {
                InlineLabelDragMode.BODY -> return viewport.moveDimension(original, DimensionHit.BODY, delta)
                InlineLabelDragMode.LABEL -> Unit
                null -> return original
            }
        }
        val axis = viewport.screenPoint(original.end) - viewport.screenPoint(original.start)
        if (!appearance.inline) return original.shiftLabel(axis, delta, labelsLocked)
        val length = hypot(axis.x, axis.y)
        val angle = Math.toDegrees(atan2(axis.y.toDouble(), axis.x.toDouble())).toFloat()
        val placement = labelPlacement(labels.getValue(original.id).geometry, length, angle, original.labelT)
        return original.shiftPlacedLabel(axis, delta, labelsLocked, placement)
    }

    private fun updateDraft(contact: EditorPoint) {
        lensContact = contact
        dimensionDrag = dimensionDrag?.let { it.copy(end = viewport.toPhotoAnchor(contact.x, contact.y)) }
        dimensionEdit = dimensionEdit?.let {
            val resolved = resolveEditDrag(it, contact)
            // Do not create a preview until the inline direction is resolved.
            if (appearance.inline && resolved.movesLabel && resolved.inlineDragMode == null) resolved
            else resolved.copy(preview = editedDimension(resolved, contact))
        }
        invalidate()
    }

    private fun autoPanVelocity(contact: EditorPoint): EditorPoint {
        // Keep the usable top edge reachable without requiring a system-bar gesture.
        // The stable inset changes neither photo coordinates nor the actual viewport.
        return EditorPoint(
            edgePanSpeed(contact.x, width, 48f * density, 440f * density),
            verticalAutoPanSpeed(
                contact.y, height, safeOverlayTopPx, fixedToolbarTopPx, density,
                isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
                editingEndpoint = dimensionEdit?.let { it.hit != DimensionHit.BODY } == true,
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
