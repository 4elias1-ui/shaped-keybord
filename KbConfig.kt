package com.arabickb.shaped

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color

/** كل إعدادات الكيبورد تُقرأ من هنا (من SharedPreferences). */
class KbConfig(
    val themeMode: Int,      // 0 نظام، 1 فاتح، 2 داكن، 3 أسود AMOLED
    val accent: Int,
    val radius: Float,
    val gap: Float,
    val keyHeight: Float,
    val fontScale: Float,
    val widthPct: Int,
    val align: Int,          // 0 يسار، 1 وسط، 2 يمين
    val numbers: Boolean,
    val hints: Boolean,
    val flip: Boolean,
    val haptic: Boolean,
    val sound: Boolean,
    val longPressMs: Int,
    val repeatMs: Int,
    val swipeSpace: Boolean,
    val doubleSpace: Boolean,
    val edge: Boolean,
    val preview: Boolean,
    val digits: Int,         // 0 لاتيني، 1 عربي، 2 فارسي
    val funcRound: Boolean,
    val bgMode: Int,         // 0 لون الثيم، 1 تدرّج، 2 صورة
    val bgGrad: Int,
    val bgDim: Int,
    val bgSplit: Boolean,    // خلفية مختلفة للكتابة المعكوسة
    val bgModeR: Int,
    val bgGradR: Int,
    val bgDimR: Int,
    val keyAlpha: Int,
    val previewRow: Boolean,
    val split: Boolean,      // كيبورد منقسم
    val splitGap: Int,       // عرض الفراغ الأوسط %
    val wrapLen: Int,        // تقسيم الأسطر الطويلة (0 = معطّل)
    val suggest: Boolean,    // اقتراح الكلمات
    val autoFix: Boolean,    // تصحيح الأخطاء الشائعة
    val cKey: Int?,
    val cFunc: Int?,
    val cBg: Int?,
    val cAccent: Int?,
    val cText: Int?,
    val night: Boolean,
) {
    val amoled: Boolean get() = themeMode == 3

    companion object {
        fun systemNight(ctx: Context): Boolean =
            (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        private fun opt(p: SharedPreferences, k: String): Int? = if (p.contains(k)) p.getInt(k, 0) else null

        fun load(p: SharedPreferences, ctx: Context): KbConfig {
            val mode = p.getInt("theme", 0)
            val night = when (mode) {
                1 -> false
                2, 3 -> true
                else -> systemNight(ctx)
            }
            return KbConfig(
                themeMode = mode,
                accent = p.getInt("accent", 5),
                radius = p.getInt("radius", 10).toFloat(),
                gap = p.getInt("gap", 6).toFloat(),
                keyHeight = p.getInt("height", 56).toFloat(),
                fontScale = p.getInt("font", 100) / 100f,
                widthPct = p.getInt("width", 100),
                align = p.getInt("align", 1),
                numbers = p.getBoolean("numbers", false),
                hints = p.getBoolean("hints", true),
                flip = p.getBoolean("flip", false),
                haptic = p.getBoolean("haptic", true),
                sound = p.getBoolean("sound", false),
                longPressMs = p.getInt("longms", 380),
                repeatMs = p.getInt("repeatms", 45),
                swipeSpace = p.getBoolean("swipe", true),
                doubleSpace = p.getBoolean("dspace", true),
                edge = p.getBoolean("edge", true),
                preview = p.getBoolean("preview", true),
                digits = p.getInt("digits", 1),
                funcRound = p.getBoolean("fround", true),
                bgMode = p.getInt("bgmode", 0),
                bgGrad = p.getInt("bggrad", 0),
                bgDim = p.getInt("bgdim", 30),
                bgSplit = p.getBoolean("bgsplit", false),
                bgModeR = p.getInt("bgmode_r", 0),
                bgGradR = p.getInt("bggrad_r", 0),
                bgDimR = p.getInt("bgdim_r", 30),
                keyAlpha = p.getInt("keyalpha", 100),
                previewRow = p.getBoolean("prow", true),
                split = p.getBoolean("split", false),
                splitGap = p.getInt("splitgap", 20),
                wrapLen = p.getInt("wrap", 0),
                suggest = p.getBoolean("suggest", true),
                autoFix = p.getBoolean("autofix", true),
                cKey = opt(p, "c_key"),
                cFunc = opt(p, "c_func"),
                cBg = opt(p, "c_bg"),
                cAccent = opt(p, "c_accent"),
                cText = opt(p, "c_text"),
                night = night,
            )
        }
    }
}

object Backgrounds {
    val NAMES = listOf("غروب", "محيط", "غابة", "ليل", "وردي", "ثلجي")
    val GRADS: Array<IntArray> = arrayOf(
        intArrayOf(0xFF3A1C71.toInt(), 0xFFD76D77.toInt(), 0xFFFFAF7B.toInt()),
        intArrayOf(0xFF0F2027.toInt(), 0xFF2C5364.toInt()),
        intArrayOf(0xFF0B3D2E.toInt(), 0xFF1B8A5A.toInt()),
        intArrayOf(0xFF0F0C29.toInt(), 0xFF302B63.toInt(), 0xFF24243E.toInt()),
        intArrayOf(0xFFFF9A9E.toInt(), 0xFFFAD0C4.toInt()),
        intArrayOf(0xFFE0EAFC.toInt(), 0xFFCFDEF3.toInt()),
    )
}

class Palette(
    val bg: Int, val key: Int, val func: Int, val pressed: Int,
    val text: Int, val hint: Int, val edge: Int, val accent: Int, val onAccent: Int,
) {
    companion object {
        private val DAY = intArrayOf(
            0xFF3F5EFB.toInt(), 0xFF12A56A.toInt(), 0xFFE0457B.toInt(), 0xFF7B4DFF.toInt(),
            0xFFE8650A.toInt(), 0xFF0097A7.toInt(),
        )
        private val NIGHT = intArrayOf(
            0xFF7C9BFF.toInt(), 0xFF4FD6A0.toInt(), 0xFFFF8FB3.toInt(), 0xFFB39BFF.toInt(),
            0xFFFFB066.toInt(), 0xFF22D3EE.toInt(),
        )

        private fun shade(c: Int, f: Float): Int =
            Color.argb(255, (Color.red(c) * f).toInt().coerceIn(0, 255), (Color.green(c) * f).toInt().coerceIn(0, 255), (Color.blue(c) * f).toInt().coerceIn(0, 255))

        private fun blend(a: Int, b: Int, t: Float): Int =
            Color.argb(
                255,
                (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
                (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
                (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt(),
            )

        private fun contrastOn(c: Int): Int {
            val lum = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0
            return if (lum > 0.6) 0xFF101418.toInt() else 0xFFFFFFFF.toInt()
        }

        private fun base(c: KbConfig): Palette {
            val i = c.accent.coerceIn(0, DAY.size - 1)
            return when {
                c.amoled -> Palette(
                    0xFF000000.toInt(), 0xFF1A1A1D.toInt(), 0xFF2A2A2E.toInt(), 0xFF3C3C42.toInt(),
                    0xFFFFFFFF.toInt(), 0xFF8A8F9C.toInt(), 0xFF0A0A0C.toInt(), NIGHT[i], 0xFF06202A.toInt(),
                )
                c.night -> Palette(
                    0xFF121315.toInt(), 0xFF212224.toInt(), 0xFF2E3033.toInt(), 0xFF45484D.toInt(),
                    0xFFE6E7EA.toInt(), 0xFF8A8F9C.toInt(), 0xFF0A0A0B.toInt(), NIGHT[i], 0xFF06202A.toInt(),
                )
                else -> Palette(
                    0xFFE4E7EE.toInt(), 0xFFFFFFFF.toInt(), 0xFFCBD0DB.toInt(), 0xFFB4BBCB.toInt(),
                    0xFF16181D.toInt(), 0xFF7B8190.toInt(), 0xFFB5BBC9.toInt(), DAY[i], 0xFFFFFFFF.toInt(),
                )
            }
        }

        /** الألوان الأساسية للثيم، ثم تُطبَّق فوقها الألوان المخصصة إن وُجدت. */
        fun of(c: KbConfig): Palette {
            val b = base(c)
            val ck = c.cKey
            val cf = c.cFunc
            val cb = c.cBg
            val ca = c.cAccent
            val ct = c.cText
            if (ck == null && cf == null && cb == null && ca == null && ct == null) return b
            val key = ck ?: b.key
            val func = cf ?: b.func
            val bg = cb ?: b.bg
            val accent = ca ?: b.accent
            val text = ct ?: b.text
            val pressed = if (ck != null || cf != null) blend(func, text, 0.2f) else b.pressed
            val edge = if (ck != null) shade(key, 0.75f) else b.edge
            val hint = if (ct != null) blend(text, bg, 0.45f) else b.hint
            val onAccent = if (ca != null) contrastOn(accent) else b.onAccent
            return Palette(bg, key, func, pressed, text, hint, edge, accent, onAccent)
        }
    }
}
