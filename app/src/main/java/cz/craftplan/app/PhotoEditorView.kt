package cz.craftplan.app

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
    private var placingDimension = false
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
        viewport.fit(width, height)
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
        val bottom = height - margin - size

        if (!positionLens(oppositeX, margin, size, target, contact) &&
            !positionLens(otherX, margin, size, target, contact) &&
            !positionLens(oppositeX, bottom, size, target, contact) &&
            !positionLens(otherX, bottom, size, target, contact)
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

        drawCrosshair(canvas, centerX, centerY, Color.BLACK, 1.6f * density)
        drawCrosshair(canvas, centerX, centerY, Color.WHITE, 0.8f * density)
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
        for (mark in 2..10 step 4) {
            val near = mark * density
            val far = near + 2f * density
            canvas.drawLine(x - far, y, x - near, y, crosshairPaint)
            canvas.drawLine(x + near, y, x + far, y, crosshairPaint)
            canvas.drawLine(x, y - far, x, y - near, crosshairPaint)
            canvas.drawLine(x, y + near, x, y + far, crosshairPaint)
        }
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
            placingDimension = placement is DimensionPlacement.Ready
        }
        if (placingDimension) {
            if (interruption(event, creating = true) == DimensionInterruption.CANCEL) {
                clearDimensionGesture()
            } else {
                handleDimensionTouch(event)
            }
            return true
        }

        scaleDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val dimension = (placement as? DimensionPlacement.Complete)?.dimension
            val down = EditorPoint(event.x, event.y)
            if (dimension != null && viewport.toPhotoAnchor(down.x, down.y) != null) {
                val hit = hitDimension(down, viewport.screenPoint(dimension.start), viewport.screenPoint(dimension.end), density)
                if (hit != null) {
                    dimensionEdit = DimensionEdit(dimension, hit, event.getPointerId(0), down)
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    clickCandidate = false
                    return true
                }
            }
        }
        if (dimensionEdit != null) {
            val interruption = interruption(event, creating = false)
            if (interruption == DimensionInterruption.CONTINUE) {
                handleEditTouch(event)
                return true
            }
            clearDimensionGesture()
            if (interruption != DimensionInterruption.HAND_OFF_TO_PINCH) return true
        }
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

    private fun interruption(event: MotionEvent, creating: Boolean): DimensionInterruption =
        dimensionInterruption(
            canceled = event.actionMasked == MotionEvent.ACTION_CANCEL ||
                event.flags and MotionEvent.FLAG_CANCELED != 0,
            secondPointer = event.actionMasked == MotionEvent.ACTION_POINTER_DOWN,
            creating = creating,
        )

    private fun handleEditTouch(event: MotionEvent) {
        val draft = dimensionEdit ?: return

        val index = event.findPointerIndex(draft.pointerId)
        if (index < 0) {
            clearDimensionGesture()
            return
        }
        val contact = EditorPoint(event.getX(index), event.getY(index))
        if (!viewport.contains(contact.x, contact.y)) {
            if (event.actionMasked == MotionEvent.ACTION_UP) clearDimensionGesture()
            return
        }
        val moved = draft.preview != null || passesDragThreshold(draft.down, contact, 7f * density)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (moved) {
                dimensionSelected = true
                lensContact = contact
                val preview = viewport.moveDimension(draft.original, draft.hit, contact - draft.down)
                dimensionEdit = draft.copy(preview = preview)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                dimensionSelected = true
                clearDimensionGesture()
                invalidate()
                if (moved) {
                    val result = viewport.moveDimension(draft.original, draft.hit, contact - draft.down)
                    if (result != draft.original) onDimension(DimensionPlacement.Complete(result))
                } else {
                    performClick()
                }
            }
        }
    }

    private fun handleDimensionTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lensContact = EditorPoint(event.x, event.y)
                dimensionDrag = viewport.toPhotoAnchor(event.x, event.y)?.let { start ->
                    DimensionDrag(event.getPointerId(0), start)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val draft = dimensionDrag ?: return
                val index = event.findPointerIndex(draft.pointerId)
                if (index < 0) {
                    clearDimensionGesture()
                    return
                }
                val contact = EditorPoint(event.getX(index), event.getY(index))
                val end = viewport.toPhotoAnchor(contact.x, contact.y) ?: return
                lensContact = contact
                dimensionDrag = draft.copy(end = end)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val draft = dimensionDrag
                clearDimensionGesture()
                val ready = placement as? DimensionPlacement.Ready ?: return
                if (draft == null || event.getPointerId(0) != draft.pointerId) return

                val end = viewport.dragEnd(draft.start, event.x, event.y, touchSlop)
                ready.finishDrag(draft.start, end)?.let(onDimension)
            }
        }
    }

    private fun clearDimensionGesture() {
        val needsRedraw = dimensionDrag != null || dimensionEdit != null
        dimensionDrag = null
        dimensionEdit = null
        lensOnRight = null
        lensContact = null
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
