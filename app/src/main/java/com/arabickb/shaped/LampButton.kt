package com.arabickb.shaped

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View

/** زر واحد بمصباح صغير في زاويته: يشتعل عند تفعيل الكتابة المعكوسة ويُطفأ في الكتابة العادية. */
class LampButton(context: Context) : View(context) {
    var label: String = "معكوس"
    private var lit = false
    private var glow = 0f
    private var animator: ValueAnimator? = null
    private var bgColor = 0xFF2E3033.toInt()
    private var textColor = 0xFFE6E7EA.toInt()
    private var accentColor = 0xFF22D3EE.toInt()
    private var lampOffColor = 0xFF55595F.toInt()
    private val lampOnColor = 0xFF39F58A.toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val d = resources.displayMetrics.density

    fun style(bg: Int, text: Int, accent: Int, lampOff: Int) {
        bgColor = bg
        textColor = text
        accentColor = accent
        lampOffColor = lampOff
        invalidate()
    }

    /** يشعل أو يطفئ المصباح؛ animate = false لتعيين الحالة فوراً. */
    fun setLit(v: Boolean, animate: Boolean = true) {
        if (lit == v && animate) return
        lit = v
        animator?.cancel()
        val target = if (v) 1f else 0f
        if (!animate) {
            glow = target
            invalidate()
            return
        }
        val a = ValueAnimator.ofFloat(glow, target)
        a.duration = 240
        a.addUpdateListener {
            glow = it.animatedValue as Float
            invalidate()
        }
        animator = a
        a.start()
    }

    private fun lerp(a: Int, b: Int, t: Float): Int = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = bgColor
        canvas.drawRoundRect(rect, h / 2, h / 2, paint)

        // المصباح في الزاوية العليا
        val cx = w - 13 * d
        val cy = h * 0.3f
        if (glow > 0.01f) {
            val gr = 12 * d
            paint.shader = RadialGradient(
                cx, cy, gr,
                intArrayOf(Color.argb((170 * glow).toInt(), 57, 245, 138), Color.argb(0, 57, 245, 138)),
                null, Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, gr, paint)
            paint.shader = null
        }
        paint.color = lerp(lampOffColor, lampOnColor, glow)
        canvas.drawCircle(cx, cy, 4 * d, paint)
        paint.color = Color.argb((120 + 100 * glow).toInt(), 255, 255, 255)
        canvas.drawCircle(cx - 1.2f * d, cy - 1.2f * d, 1.2f * d, paint)

        // النص
        paint.color = lerp(textColor, accentColor, glow)
        paint.textSize = 14f * resources.displayMetrics.scaledDensity
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        canvas.drawText(label, w / 2 - 6 * d, h / 2 - (fm.ascent + fm.descent) / 2 + 2 * d, paint)
    }
}
