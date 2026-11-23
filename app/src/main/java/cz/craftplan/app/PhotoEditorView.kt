package cz.craftplan.app

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.atan2
import kotlin.math.hypot

internal class PhotoEditorView(context: Context) : View(context) {
    private val photo = BitmapFactory.decodeResource(resources, R.drawable.synthetic_workbench)
    private val viewport = PhotoViewport(photo.width, photo.height)
    private val photoBounds = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
        val preview: PhotoDimension? = null,
    )

    private val lensBounds = RectF()
    private val lensOutline = Path()
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
    var placement: DimensionPlacement = DimensionPlacement.Idle
        set(value) {
            if (field != value) {
                if (value is DimensionPlacement.Complete && field !is DimensionPlacement.Complete) {
                    dimensionSelected = true
                }
                field = value
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
        clearDimensionGesture()
        viewport.resize(width, height)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) clearDimensionGesture()
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
            else drawDimension(canvas, start, end, drag != null || dimensionSelected)
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

    private fun drawDimension(canvas: Canvas, start: PhotoAnchor, end: PhotoAnchor, selected: Boolean) {
        drawDimensionLine(canvas, start, end, selected)
        if (selected) {
            drawAnchor(canvas, start)
            drawAnchor(canvas, end)
        }
        val origin = viewport.screenPoint(start)
        val axis = viewport.screenPoint(end) - origin
        drawUnmeasuredMarker(canvas, origin.x + axis.x / 2f, origin.y + axis.y / 2f, readableAngle(axis))
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

    private fun drawUnmeasuredMarker(canvas: Canvas, x: Float, y: Float, angle: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle)
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

        drawCrosshair(canvas, centerX, centerY, 0x99000000.toInt(), 1.5f * density)
        drawCrosshair(canvas, centerX, centerY, 0xCCFFFFFF.toInt(), 0.75f * density)
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
        canvas.drawLine(x - arm, y, x + arm, y, crosshairPaint)
        canvas.drawLine(x, y - arm, x, y + arm, crosshairPaint)
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
                val hit = hitDimension(
                    down, viewport.screenPoint(dimension.start), viewport.screenPoint(dimension.end), density,
                ) ?: return false
                dimensionEdit = DimensionEdit(dimension, hit, pointerId, down, downAnchor)
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
                    null
                }
            }
            else -> null
        }
        clearDimensionGesture(result?.dimension ?: edit?.original)
        result?.let(onDimension)
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
        edgePanSpeed(contact.y, height, 48f * density, 440f * density),
    )

    private fun resumeAutoPanIfDragging() {
        if (panPointerId != null || autoPanTime != null) return
        val contact = lensContact ?: return
        val drag = dimensionDrag
        val edit = dimensionEdit
        val canPan = if (drag != null) {
            passesDragThreshold(viewport.screenPoint(drag.start), contact, touchSlop.toFloat())
        } else {
            edit?.preview != null && edit.hit != DimensionHit.BODY
        }
        if (!canPan || autoPanVelocity(contact) == EditorPoint(0f, 0f)) return
        autoPanTime = SystemClock.uptimeMillis()
        postOnAnimation(autoPan)
    }

    private fun stopAutoPan() {
        autoPanTime = null
        removeCallbacks(autoPan)
    }

    private fun stepAutoPan() {
        val previous = autoPanTime ?: return
        autoPanTime = null
        val contact = lensContact ?: return
        val velocity = autoPanVelocity(contact)
        val now = SystemClock.uptimeMillis()
        val seconds = (now - previous).coerceIn(1L, 32L) / 1000f
        val left = viewport.left
        val top = viewport.top
        viewport.transform(0f, 0f, velocity.x * seconds, velocity.y * seconds, extendingForDrag = true)
        if (viewport.left != left || viewport.top != top) {
            updateDraft(contact)
            autoPanTime = now
            postOnAnimation(autoPan)
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
