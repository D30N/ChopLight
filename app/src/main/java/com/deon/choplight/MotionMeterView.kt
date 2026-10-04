package com.deon.choplight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Live bar meter showing linear-acceleration magnitude with a threshold tick. */
class MotionMeterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var maxScale = 30f
    private var levelMag = 0f
    private var thresholdMag = ChopDetector.DEFAULT_THRESHOLD

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A2A2A") }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B3261E") }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 3f
    }
    private val rect = RectF()
    private val fillRect = RectF()

    fun setLevel(magnitude: Float) {
        levelMag = magnitude.coerceIn(0f, maxScale)
        invalidate()
    }

    fun setThreshold(magnitude: Float) {
        thresholdMag = magnitude.coerceIn(0f, maxScale)
        invalidate()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val r = dp(8f)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, bgPaint)

        val frac = (levelMag / maxScale).coerceIn(0f, 1f)
        fillRect.set(0f, 0f, width * frac, height.toFloat())
        canvas.drawRoundRect(fillRect, r, r, fillPaint)

        val tx = width * (thresholdMag / maxScale).coerceIn(0f, 1f)
        canvas.drawLine(tx, 0f, tx, height.toFloat(), tickPaint)
    }
}
