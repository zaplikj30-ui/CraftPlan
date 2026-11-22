package cz.craftplan.app

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
    var onAnchor: (PhotoAnchor) -> Unit = {}
    var placement: DimensionPlacement = DimensionPlacement.Idle
        set(value) {
            if (field != value) {
                field = value
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
        viewport.fit(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFF17191C.toInt())
        photoBounds.set(
            viewport.left,
            viewport.top,
            viewport.left + photo.width * viewport.scale,
            viewport.top + photo.height * viewport.scale,
        )
        canvas.drawBitmap(photo, null, photoBounds, paint)
        canvas.save()
        canvas.clipRect(photoBounds)
        when (val current = placement) {
            is DimensionPlacement.AwaitingSecond -> drawAnchor(canvas, current.start)
            is DimensionPlacement.Complete -> drawDimension(canvas, current.dimension)
            else -> Unit
        }
        canvas.restore()
    }

    private fun drawDimension(canvas: Canvas, dimension: PhotoDimension) {
        val x1 = viewport.screenX(dimension.start)
        val y1 = viewport.screenY(dimension.start)
        val dx = viewport.screenX(dimension.end) - x1
        val dy = viewport.screenY(dimension.end) - y1
        val length = hypot(dx, dy)
        canvas.save()
        canvas.translate(x1, y1)
        canvas.rotate(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())

        overlayPaint.color = Color.rgb(247, 220, 39)
        overlayPaint.strokeWidth = 2f * density
        val tick = 6f * density
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

        drawAnchor(canvas, dimension.start)
        drawAnchor(canvas, dimension.end)
        drawUnmeasuredMarker(canvas, x1 + dx / 2f, y1 + dy / 2f)
    }

    private fun drawAnchor(canvas: Canvas, anchor: PhotoAnchor) {
        overlayPaint.color = 0x30FFFFFF
        val x = viewport.screenX(anchor)
        val y = viewport.screenY(anchor)
        val radius = 10f * density
        canvas.drawCircle(x, y, radius, overlayPaint)
        overlayPaint.color = 0xCCFFFFFF.toInt()
        overlayPaint.style = Paint.Style.STROKE
        overlayPaint.strokeWidth = 1.5f * density
        canvas.drawCircle(x, y, radius, overlayPaint)
        overlayPaint.style = Paint.Style.FILL
    }

    private fun drawUnmeasuredMarker(canvas: Canvas, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y - 12f * density)
        canvas.scale(density, density)
        overlayPaint.color = Color.rgb(232, 183, 108)
        canvas.drawRoundRect(-11f, -4f, 11f, 4f, 1.5f, 1.5f, overlayPaint)
        overlayPaint.color = Color.rgb(60, 40, 24)
        overlayPaint.strokeWidth = 1f
        for (tick in -8..4 step 4) {
            canvas.drawLine(tick.toFloat(), -4f, tick.toFloat(), -1f, overlayPaint)
        }
        canvas.rotate(-30f, 8f, 0f)
        overlayPaint.color = Color.rgb(232, 183, 108)
        canvas.drawRoundRect(7f, -4f, 19f, 4f, 1.5f, 1.5f, overlayPaint)
        overlayPaint.color = Color.rgb(60, 40, 24)
        canvas.drawCircle(8f, 0f, 1f, overlayPaint)
        canvas.drawLine(13f, -4f, 13f, -1f, overlayPaint)
        canvas.drawLine(16f, -4f, 16f, -1f, overlayPaint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
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
                    performClick()
                    if (placement is DimensionPlacement.AwaitingFirst ||
                        placement is DimensionPlacement.AwaitingSecond
                    ) {
                        viewport.toPhotoAnchor(event.x, event.y)?.let(onAnchor)
                    }
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
}
