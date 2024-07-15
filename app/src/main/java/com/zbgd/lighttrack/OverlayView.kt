package com.zbgd.lighttrack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = 0xFF00FF00.toInt() // Green color
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val paint2 = Paint().apply {
        color = 0xFFFF0000.toInt() // Red color
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var drawing = false
    private var selectionRect: Rect? = null
    private var trackRect: Rect? = null



    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                endX = startX
                endY = startY
                drawing = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (drawing) {
                    endX = event.x
                    endY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (drawing) {
                    endX = event.x
                    endY = event.y
                    drawing = false
                    selectionRect = Rect(
                        Math.min(startX.toInt(), endX.toInt()),
                        Math.min(startY.toInt(), endY.toInt()),
                        Math.max(startX.toInt(), endX.toInt()),
                        Math.max(startY.toInt(), endY.toInt())
                    )
                    invalidate()
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trackRect != null){
            canvas.drawRect(trackRect!!, paint2)
        }
        if (selectionRect != null) {
            canvas.drawRect(selectionRect!!, paint)

        } else if (drawing) {
            canvas.drawRect(startX, startY, endX, endY, paint)
        }
    }

    fun getSelectionRect(): Rect? {
        return selectionRect
    }

    fun clearSelection() {
        selectionRect = null
        invalidate()
    }

    fun setTrackRect(rect: Rect) {
        trackRect = rect
        invalidate()
    }

    fun clearTarget() {
        trackRect = null
        invalidate()
    }

}