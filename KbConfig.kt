package com.arabickb.shaped

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

/** كل إعدادات الكيبورد تُقرأ من هنا (من SharedPreferences). */
class KbConfig(
    val themeMode: Int,      // 0 نظام، 1 فاتح، 2 داكن، 3 أسود AMOLED
    val accent: Int,         // رقم لون التمييز
    val radius: Float,       // استدارة المفاتيح dp
    val gap: Float,          // المسافة بين المفاتيح dp
    val keyHeight: Float,    // ارتفاع المفتاح dp
    val fontScale: Float,    // حجم الخط (1.0 = عادي)
    val widthPct: Int,       // عرض الكيبورد %
    val align: Int,          // 0 يسار، 1 وسط، 2 يمين
    val numbers: Boolean,    // صف أرقام مستقل (وإلا تظهر الأرقام كتلميحات على الصف الأول)
    val hints: Boolean,      // إظهار تلميحات البدائل
    val flip: Boolean,       // قلب أماكن مفاتيح الوظائف
    val haptic: Boolean,
    val sound: Boolean,
    val longPressMs: Int,
    val repeatMs: Int,
    val swipeSpace: Boolean, // تحريك المؤشر بالسحب على المسافة
    val doubleSpace: Boolean,// مسافتان = نقطة
    val toolbar: Int,        // محجوز
    val edge: Boolean,       // حافة سفلية للمفاتيح
    val preview: Boolean,    // فقاعة معاينة الحرف
    val digits: Int,         // 0 لاتيني، 1 عربي، 2 فارسي
    val funcRound: Boolean,  // مفاتيح الوظائف بيضاوية
    val bgMode: Int,         // 0 لون الثيم، 1 تدرّج، 2 صورة
    val bgGrad: Int,
    val bgDim: Int,          // تعتيم الصورة %
    val keyAlpha: Int,       // شفافية المفاتيح %
    val previewRow: Boolean, // سطر المعاينة
    val night: Boolean,
) {
    val amoled: Boolean get() = themeMode == 3

    companion object {
        fun systemNight(ctx: Context): Boolean =
            (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

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
                toolbar = p.getInt("toolbar", 0),
                edge = p.getBoolean("edge", true),
                preview = p.getBoolean("preview", true),
                digits = p.getInt("digits", 1),
                funcRound = p.getBoolean("fround", true),
                bgMode = p.getInt("bgmode", 0),
                bgGrad = p.getInt("bggrad", 0),
                bgDim = p.getInt("bgdim", 30),
                keyAlpha = p.getInt("keyalpha", 100),
                previewRow = p.getBoolean("prow", true),
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

        fun of(c: KbConfig): Palette {
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
    }
}
