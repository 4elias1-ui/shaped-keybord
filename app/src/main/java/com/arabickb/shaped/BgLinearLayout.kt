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

/** حفظ وتحميل صورة خلفية الكيبورد. الصور تُنسخ إلى ملفات التطبيق فلا تحتاج أذونات. rev = خلفية الكتابة المعكوسة. */
object BgImage {
    private val cached = arrayOfNulls<Bitmap>(2)
    private val stamps = longArrayOf(-1L, -1L)

    private fun file(ctx: Context, rev: Boolean) = File(ctx.filesDir, if (rev) "kb_bg_r.jpg" else "kb_bg.jpg")
    private fun idx(rev: Boolean) = if (rev) 1 else 0

    fun delete(ctx: Context, rev: Boolean) {
        file(ctx, rev).delete()
        cached[idx(rev)] = null
    }

    fun load(ctx: Context, rev: Boolean): Bitmap? {
        val f = file(ctx, rev)
        if (!f.exists()) return null
        val i = idx(rev)
        val c = cached[i]
        if (c != null && stamps[i] == f.lastModified()) return c
        val b = try { BitmapFactory.decodeFile(f.absolutePath) } catch (e: Throwable) { null }
        cached[i] = b
        stamps[i] = f.lastModified()
        return b
    }

    fun saveFrom(ctx: Context, uri: Uri, rev: Boolean): Boolean {
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
                file(ctx, rev).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                cached[idx(rev)] = null
                true
            }
        } catch (e: Throwable) {
            false
        }
    }
}

/** حاوية عمودية ترسم خلفية الكيبورد: لون الثيم أو تدرّج أو صورة (مع تعتيم)، ولكل وضع كتابة خلفيته. */
class BgLinearLayout(context: Context) : LinearLayout(context) {
    private var mode = 0
    private var grad = 0
    private var dim = 30
    private var bg = 0xFF121315.toInt()
    private var bmp: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val m = Matrix()

    init { setWillNotDraw(false) }

    fun applyBg(c: KbConfig, reversed: Boolean) {
        val r = reversed && c.bgSplit
        mode = if (r) c.bgModeR else c.bgMode
        grad = if (r) c.bgGradR else c.bgGrad
        dim = if (r) c.bgDimR else c.bgDim
        bg = Palette.of(c).bg
        bmp = if (mode == 2) BgImage.load(context, r) else null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val b = bmp
        if (mode == 2 && b != null) {
            val s = maxOf(w / b.width, h / b.height)
            m.reset()
            m.postScale(s, s)
            m.postTranslate((w - b.width * s) / 2f, (h - b.height * s) / 2f)
            canvas.drawBitmap(b, m, paint)
            canvas.drawColor(Color.argb(dim.coerceIn(0, 90) * 255 / 100, 0, 0, 0))
        } else if (mode == 1) {
            val cols = Backgrounds.GRADS[grad.coerceIn(0, Backgrounds.GRADS.size - 1)]
            paint.shader = LinearGradient(0f, 0f, 0f, h, cols, null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        } else {
            canvas.drawColor(bg)
        }
    }
}
