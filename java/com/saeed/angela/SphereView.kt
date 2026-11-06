package com.saeed.angela

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class SphereView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var active = false
    private var t = 0f
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#39FF88") }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5539FF88")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        t += if (active) 0.045f else 0.015f
        val cx = width / 2f
        val cy = height / 2f
        val pulse = if (active) 1f + 0.06f * sin(t * 6f) else 1f + 0.02f * sin(t * 2f)
        val r = minOf(width, height) * 0.36f * pulse

        glow.shader = RadialGradient(
            cx, cy, r * 1.5f,
            intArrayOf(Color.parseColor("#6639FF88"), Color.parseColor("#2200C060"), Color.TRANSPARENT),
            floatArrayOf(0.2f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 1.5f, glow)
        canvas.drawCircle(cx, cy, r, ring)
        canvas.drawCircle(cx, cy, r * 1.12f, ring)

        val lat = 9
        val lon = 16
        for (i in 1 until lat) {
            val phi = Math.PI * i / lat
            for (j in 0 until lon) {
                val theta = 2 * Math.PI * j / lon + t
                val x = (sin(phi) * cos(theta)).toFloat()
                val y = cos(phi).toFloat()
                val z = (sin(phi) * sin(theta)).toFloat()
                val depth = (z + 1f) / 2f
                dot.alpha = (60 + 195 * depth).toInt()
                canvas.drawCircle(cx + x * r, cy + y * r, 2f + 4f * depth, dot)
            }
        }
        postInvalidateOnAnimation()
    }
}
