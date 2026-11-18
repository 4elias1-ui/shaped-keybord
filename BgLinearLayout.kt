package com.arabickb.shaped

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.widget.LinearLayout
import java.io.File

/** حفظ وتحميل صورة خلفية الكيبورد (تُنسخ إلى ملفات التطبيق فلا تحتاج أذونات). */
object BgImage {
    private var cached: Bitmap? = null
    private var cachedStamp = -1L

    private fun file(ctx: Context) = File(ctx.filesDir, "kb_bg.jpg")

    fun exists(ctx: Context) = file(ctx).exists()

    fun delete(ctx: Context) {
        file(ctx).delete()
        cached = null
    }

    fun load(ctx: Context): Bitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        val c = cached
        if (c != null && cachedStamp == f.lastModified()) return c
        val b = try { BitmapFactory.decodeFile(f.absolutePath) } catch (e: Throwable) { null }
        cached = b
        cachedStamp = f.lastModified()
        return b
    }

    fun saveFrom(ctx: Context, uri: Uri): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var s = 1
            while (bounds.outWidth / s > 1600 || bounds.outHeight / s > 1600) s *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = s }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (bmp == null) {
                false
            } else {
                file(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                cached = null
                true
            }
        } catch (e: Throwable) {
            false
        }
    }
}

/** حاوية عمودية ترسم خلفية الكيبورد: لون الثيم أو تدرّج أو صورة (مع تعتيم). */
class BgLinearLayout(context: Context) : LinearLayout(context) {
    private var cfg: KbConfig? = null
    private var pal: Palette? = null
    private var bmp: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val m = Matrix()

    init { setWillNotDraw(false) }

    fun applyBg(c: KbConfig) {
        cfg = c
        pal = Palette.of(c)
        bmp = if (c.bgMode == 2) BgImage.load(context) else null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val c = cfg ?: return
        val p = pal ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val b = bmp
        if (c.bgMode == 2 && b != null) {
            val s = maxOf(w / b.width, h / b.height)
            m.reset()
            m.postScale(s, s)
            m.postTranslate((w - b.width * s) / 2f, (h - b.height * s) / 2f)
            canvas.drawBitmap(b, m, paint)
            canvas.drawColor(Color.argb(c.bgDim.coerceIn(0, 90) * 255 / 100, 0, 0, 0))
        } else if (c.bgMode == 1) {
            val cols = Backgrounds.GRADS[c.bgGrad.coerceIn(0, Backgrounds.GRADS.size - 1)]
            paint.shader = LinearGradient(0f, 0f, 0f, h, cols, null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        } else {
            canvas.drawColor(p.bg)
        }
    }
}
