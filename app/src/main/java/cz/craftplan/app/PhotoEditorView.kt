package cz.craftplan.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal class PhotoEditorView(context: Context) : View(context) {
    private val photo = BitmapFactory.decodeResource(resources, R.drawable.synthetic_workbench)
    private val viewport = PhotoViewport(photo.width, photo.height)
    private val photoBounds = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
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
    )

    private data class DimensionEdit(
        val original: PhotoDimension,
        val hit: DimensionHit,
        val pointerId: Int,
        val down: EditorPoint,
        val downAnchor: PhotoAnchor,
        val opensValueOnTap: Boolean,
        val preview: PhotoDimension? = null,
    )

    private val lensBounds = RectF()
    private val lensOutline = Path()
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f)
    }
    private var dimensionDrag: DimensionDrag? = null
    private var dimensionEdit: DimensionEdit? = null
    private var dimensionSelected = false
    private var lensOnRight: Boolean? = null
    private var lensContact: EditorPoint? = null
    private var ownsDimensionStream = false
    private var panPointerId: Int? = null
    private var panContact: EditorPoint? = null
    private var autoPanTime: Long? = null
    private val autoPan = Runnable { stepAutoPan() }
    var onDimension: (DimensionPlacement.Complete) -> Unit = {}
    var onEditValue: (PhotoDimension) -> Unit = {}
    private var valueLabel: String? = null
    private var labelBesideRuler = false
    private var hasNote = false
    var showNotes = true
        set(value) {
            if (field == value) return
            field = value
            updateDimensionLabel()
            invalidate()
        }
    var placement: DimensionPlacement = DimensionPlacement.Idle
        set(value) {
            if (field != value) {
                if (value is DimensionPlacement.Complete && field !is DimensionPlacement.Complete) {
                    dimensionSelected = true
                }
                field = value
                updateDimensionLabel()
                clearDimensionGesture()
                invalidate()
            }
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
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                viewport.transform(focusX, focusY, detector.focusX, detector.focusY, detector.scaleFactor)
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
        updateDimensionLabel()
        clearDimensionGesture()
        viewport.resize(width, height)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) clearDimensionGesture()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) clearDimensionGesture()
    }

    override fun onDetachedFromWindow() {
        clearDimensionGesture()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
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
        val dimension = edit?.preview ?: (placement as? DimensionPlacement.Complete)?.dimension
        val start = drag?.start ?: dimension?.start
        val end = drag?.end ?: dimension?.end
        if (start != null) {
            if (end == null || start == end) drawAnchor(canvas, start)
            else drawDimension(canvas, start, end, drag != null || dimensionSelected, valueLabel, hasNote)
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
        if (target != null && start != null) {
            drawMagnifier(canvas, viewport.screenPoint(target), start, end)
        }
        canvas.restoreToCount(saveCount)
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

    private fun drawDimension(
        canvas: Canvas, start: PhotoAnchor, end: PhotoAnchor, selected: Boolean, label: String?,
        noteIndicator: Boolean,
    ) {
        drawDimensionLine(canvas, start, end, selected)
        if (selected) {
            drawAnchor(canvas, start)
            drawAnchor(canvas, end)
        }
        val origin = viewport.screenPoint(start)
        val axis = viewport.screenPoint(end) - origin
        canvas.save()
        canvas.translate(origin.x + axis.x / 2f, origin.y + axis.y / 2f)
        canvas.rotate(readableAngle(axis))
        if (label == null || labelBesideRuler) drawUnmeasuredMarker(canvas)
        if (label != null) {
            val halfWidth = labelPaint.measureText(label) / 2f + 5f * density
            val center = labelCenter(halfWidth)
            val bottom = -5f * density
            val top = bottom - (labelPaint.descent() - labelPaint.ascent()) - 4f * density
            overlayPaint.color = WORKSPACE_COLOR
            canvas.drawRoundRect(center - halfWidth, top, center + halfWidth, bottom, 3f * density, 3f * density, overlayPaint)
            canvas.drawText(label, center, bottom - labelPaint.descent() - 2f * density, labelPaint)
        }
        if (noteIndicator) {
            // A tiny notebook marker; the note text itself stays off the photograph.
            val x = notebookLeft(label)
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
    }

    private fun hitsValue(point: EditorPoint, start: EditorPoint, end: EditorPoint): Boolean {
        val axis = end - start
        val x = point.x - (start.x + axis.x / 2f)
        val y = point.y - (start.y + axis.y / 2f)
        val angle = Math.toRadians(readableAngle(axis).toDouble())
        val along = (x * cos(angle) + y * sin(angle)).toFloat()
        val across = (-x * sin(angle) + y * cos(angle)).toFloat()
        val height = maxOf(36f * density, labelPaint.descent() - labelPaint.ascent() + 12f * density)
        if (across in -height..12f * density) {
            if (along in (-24f * density)..(24f * density)) return true
            valueLabel?.let { label ->
                val textHalfWidth = labelPaint.measureText(label) / 2f + 5f * density
                if (kotlin.math.abs(along - labelCenter(textHalfWidth)) <= textHalfWidth + 3f * density) return true
            }
        }
        if (!hasNote) return false
        val left = notebookLeft(valueLabel)
        return along in (left - 8f * density)..(left + 23f * density) &&
            across in (-32f * density)..(1f * density)
    }

    private fun notebookLeft(label: String?): Float =
        (label?.let { labelPaint.measureText(it) / 2f } ?: 14f * density) + 10f * density

    private fun labelCenter(halfWidth: Float): Float =
        if (labelBesideRuler) 18f * density + halfWidth else 0f

    private fun updateDimensionLabel() {
        val dimension = (placement as? DimensionPlacement.Complete)?.dimension
        val base = dimension?.displayLabel()
        val note = dimension?.visibleNote(showNotes)?.trim()?.replace(Regex("\\s+"), " ")
        val limit = minOf(280f * density, width * 0.65f)
        val baseWidth = base?.let(labelPaint::measureText) ?: 0f
        val noteLimit = limit - baseWidth - if (base == null) 18f * density else labelPaint.measureText(" ")
        val displayNote = if (note != null && noteLimit > 0f) {
            TextUtils.ellipsize(note, labelPaint, noteLimit, TextUtils.TruncateAt.END).toString().ifEmpty { null }
        } else null
        valueLabel = listOfNotNull(base, displayNote).joinToString(" ").ifEmpty { null }
        labelBesideRuler = base == null && displayNote != null
        hasNote = !dimension?.note.isNullOrBlank() && displayNote == null
    }

    private fun drawDimensionLine(canvas: Canvas, start: PhotoAnchor, end: PhotoAnchor, selected: Boolean) {
        val x1 = viewport.screenX(start)
        val y1 = viewport.screenY(start)
        val dx = viewport.screenX(end) - x1
        val dy = viewport.screenY(end) - y1
        val length = hypot(dx, dy)
        canvas.save()
        canvas.translate(x1, y1)
        canvas.rotate(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())

        if (selected) {
            overlayPaint.color = 0x8CFFFFFF.toInt()
            overlayPaint.strokeWidth = 5f * density
            canvas.drawLine(0f, 0f, length, 0f, overlayPaint)
        }
        overlayPaint.color = Color.rgb(247, 220, 39)
        overlayPaint.strokeWidth = 2f * density
        val tick = 11f * density
        val arrow = minOf(9f * density, length / 4f)
        canvas.drawLine(0f, 0f, length, 0f, overlayPaint)
        canvas.drawLine(0f, -tick, 0f, tick, overlayPaint)
        canvas.drawLine(length, -tick, length, tick, overlayPaint)
        for (side in -1..1 step 2) {
            val wing = side * arrow / 2f
            canvas.drawLine(0f, 0f, arrow, wing, overlayPaint)
            canvas.drawLine(length, 0f, length - arrow, wing, overlayPaint)
        }
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
        val size = minOf(136f * density, width - 2f * margin, height - 2f * margin)
        if (size <= 0f) return
        val onRight = magnifierOnRight(contact.x, width, lensOnRight)
        lensOnRight = onRight
        val oppositeX = if (onRight) width - margin - size else margin
        val otherX = if (onRight) margin else width - margin - size
        // Keep the magnifier above the hand: select only a top corner.
        if (!positionLens(oppositeX, margin, size, target, contact) &&
            !positionLens(otherX, margin, size, target, contact)
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
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            clearDimensionGesture()
            ownsDimensionStream = beginDimensionGesture(event)
        }
        if (ownsDimensionStream) {
            handleHeldDimension(event)
            return true
        }

        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startTracking(event, 0)
                clickCandidate = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                clickCandidate = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) {
                    startTracking(event, 1 - event.actionIndex)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) pan(event)
            }
            MotionEvent.ACTION_UP -> {
                if (clickCandidate && hypot(event.x - lastX, event.y - lastY) < touchSlop) {
                    dimensionSelected = false
                    invalidate()
                    performClick()
                }
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

    private fun beginDimensionGesture(event: MotionEvent): Boolean {
        val down = EditorPoint(event.x, event.y)
        val downAnchor = viewport.toPhotoAnchor(down.x, down.y) ?: return false
        val pointerId = event.getPointerId(0)
        when (val current = placement) {
            is DimensionPlacement.Ready -> {
                lensContact = down
                dimensionDrag = DimensionDrag(pointerId, downAnchor)
                invalidate()
            }
            is DimensionPlacement.Complete -> {
                val dimension = current.dimension
                val start = viewport.screenPoint(dimension.start)
                val end = viewport.screenPoint(dimension.end)
                val hitsLabel = hitsValue(down, start, end)
                val hit = hitDimension(down, start, end, density)
                    ?: DimensionHit.BODY.takeIf { hitsLabel } ?: return false
                dimensionEdit = DimensionEdit(dimension, hit, pointerId, down, downAnchor, hitsLabel)
            }
            DimensionPlacement.Idle -> return false
        }
        clickCandidate = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        return true
    }

    private fun handleHeldDimension(event: MotionEvent) {
        val heldId = dimensionDrag?.pointerId ?: dimensionEdit?.pointerId ?: return
        val canceled = event.flags and MotionEvent.FLAG_CANCELED != 0
        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL -> clearDimensionGesture()
            MotionEvent.ACTION_POINTER_DOWN -> if (!canceled && panPointerId == null) {
                dimensionEdit = dimensionEdit?.copy(opensValueOnTap = false)
                val index = event.actionIndex
                panPointerId = event.getPointerId(index)
                panContact = viewport.clampContact(event.getX(index), event.getY(index))
                stopAutoPan()
            }
            MotionEvent.ACTION_MOVE -> {
                val heldIndex = event.findPointerIndex(heldId)
                if (canceled || heldIndex < 0) {
                    clearDimensionGesture()
                    return
                }
                val contact = viewport.clampContact(event.getX(heldIndex), event.getY(heldIndex)) ?: return
                val cameraMoved = panWithSecondPointer(event)
                val edit = dimensionEdit
                if (edit == null || cameraMoved || edit.preview != null ||
                    passesDragThreshold(edit.down, contact, 7f * density)
                ) {
                    if (edit != null) dimensionSelected = true
                    updateDraft(contact)
                    resumeAutoPanIfDragging()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                when (heldPointerRelease(heldId, panPointerId, event.getPointerId(index), canceled)) {
                    HeldPointerRelease.FINISH -> finishHeldDimension(
                        viewport.clampContact(event.getX(index), event.getY(index)),
                    )
                    HeldPointerRelease.CANCEL -> clearDimensionGesture()
                    HeldPointerRelease.END_PAN -> {
                        panPointerId = null
                        panContact = null
                        resumeAutoPanIfDragging()
                    }
                    HeldPointerRelease.IGNORE -> Unit
                }
            }
        }
    }

    private fun panWithSecondPointer(event: MotionEvent): Boolean {
        val index = panPointerId?.let(event::findPointerIndex) ?: return false
        if (index < 0) return false
        val contact = viewport.clampContact(event.getX(index), event.getY(index)) ?: return false
        val previous = panContact
        panContact = contact
        if (previous == null || previous == contact) return false
        val left = viewport.left
        val top = viewport.top
        viewport.transform(previous.x, previous.y, contact.x, contact.y, extendingForDrag = true)
        return left != viewport.left || top != viewport.top
    }

    private fun finishHeldDimension(contact: EditorPoint?) {
        val draft = dimensionDrag
        val ready = placement as? DimensionPlacement.Ready
        val edit = dimensionEdit
        var valueTarget: PhotoDimension? = null
        val result = when {
            contact == null -> null
            draft != null && ready != null -> {
                ready.finishDrag(draft.start, viewport.dragEnd(draft.start, contact.x, contact.y, touchSlop))
            }
            edit != null -> {
                dimensionSelected = true
                invalidate()
                if (edit.preview != null || passesDragThreshold(edit.down, contact, 7f * density)) {
                    editedDimension(edit, contact).takeIf { it != edit.original }
                        ?.let { DimensionPlacement.Complete(it) }
                } else {
                    performClick()
                    if (edit.opensValueOnTap && hitsValue(
                            contact, viewport.screenPoint(edit.original.start), viewport.screenPoint(edit.original.end),
                        )
                    ) valueTarget = edit.original
                    null
                }
            }
            else -> null
        }
        clearDimensionGesture(result?.dimension ?: edit?.original)
        result?.let(onDimension)
        valueTarget?.let(onEditValue)
    }

    private fun editedDimension(draft: DimensionEdit, contact: EditorPoint): PhotoDimension =
        viewport.moveDimension(draft.original, draft.hit, contact - viewport.screenPoint(draft.downAnchor))

    private fun updateDraft(contact: EditorPoint) {
        lensContact = contact
        dimensionDrag = dimensionDrag?.let { it.copy(end = viewport.toPhotoAnchor(contact.x, contact.y)) }
        dimensionEdit = dimensionEdit?.let { it.copy(preview = editedDimension(it, contact)) }
        invalidate()
    }

    private fun autoPanVelocity(contact: EditorPoint): EditorPoint = EditorPoint(
        edgePanSpeed(contact.x, width, 48f * density, 440f * density),
        // Straight in the middle; progressively more diagonal toward top/bottom.
        edgePanSpeed(
            contact.y, height,
            verticalAutoPanEdgeSize(
                height, density, resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            ),
            440f * density,
        ),
    )

    private fun resumeAutoPanIfDragging() {
        val contact = lensContact
        val drag = dimensionDrag
        val edit = dimensionEdit
        val canPan = ownsDimensionStream && panPointerId == null && contact != null &&
            if (drag != null) {
                passesDragThreshold(viewport.screenPoint(drag.start), contact, touchSlop.toFloat())
            } else {
                edit?.preview != null && edit.hit != DimensionHit.BODY
            }
        if (!canPan || autoPanVelocity(contact!!) == EditorPoint(0f, 0f)) {
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
        if (!ownsDimensionStream || panPointerId != null ||
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

    private fun clearDimensionGesture(
        dimension: PhotoDimension? = (placement as? DimensionPlacement.Complete)?.dimension,
    ) {
        val needsRedraw = dimensionDrag != null || dimensionEdit != null
        dimensionDrag = null
        dimensionEdit = null
        lensOnRight = null
        lensContact = null
        panPointerId = null
        panContact = null
        ownsDimensionStream = false
        stopAutoPan()
        viewport.updateContentBounds(dimension, 24f * density)
        if (needsRedraw) invalidate()
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

        dragging = true
        clickCandidate = false
        viewport.transform(lastX, lastY, x, y)
        lastX = x
        lastY = y
        invalidate()
    }

    private companion object {
        val WORKSPACE_COLOR = 0xFF17191C.toInt()
    }
}
