package com.arabickb.shaped

enum class Kind { CHAR, SHIFT, EXT, DELETE, ENTER, SPACE, SYMBOLS, NUMBERS, LETTERS, SYM_PAGE, SPACER }
enum class Layer { AR, AR_EXT, EN, NUM, SYM1, SYM2 }

data class Key(
    val label: String,
    val text: String = label,
    val kind: Kind = Kind.CHAR,
    val weight: Float = 1f,
    val alts: List<String> = emptyList(),
    val pill: Boolean = false,
)

object Layouts {
    private const val AI = "١٢٣٤٥٦٧٨٩٠"
    private const val FA = "۱۲۳۴۵۶۷۸۹۰"
    private const val LA = "1234567890"

    private val AR_ALTS: Map<Char, List<String>> = mapOf(
        'ق' to listOf("ڨ"),
        'ف' to listOf("ڤ", "ڥ", "ڦ"),
        'ه' to listOf("ة", "ە", "ہ", "ھ", "ۀ"),
        'ج' to listOf("چ", "ڄ", "ڃ", "ڇ"),
        'د' to listOf("ڈ", "ڌ", "ڍ", "ڎ"),
        'ي' to listOf("ى", "ئ", "ی", "ێ", "ې", "ۓ", "ے"),
        'ب' to listOf("پ", "ٻ", "ڀ"),
        'ل' to listOf("لا", "لأ", "لإ", "لآ", "ڵ"),
        'ا' to listOf("أ", "إ", "آ", "ٱ"),
        'ت' to listOf("ٹ", "ٺ", "ٿ"),
        'ن' to listOf("ں", "ڻ"),
        'ك' to listOf("گ", "ک", "ڭ", "ڳ", "ڱ"),
        'ر' to listOf("ڕ", "ڑ"),
        'ز' to listOf("ژ"),
        'و' to listOf("ؤ", "ۆ", "ۇ", "ۈ", "ۋ", "ۅ", "ۉ"),
        'ؤ' to listOf("ۆ", "ۇ", "ۈ", "ۋ"),
        'ة' to listOf("ە", "ہ", "ھ"),
        'ى' to listOf("ي", "ئ", "ی"),
        'ء' to listOf("ئ", "ؤ"),
    )
    private val EN_ALTS: Map<Char, String> = mapOf(
        'a' to "àáâäãåæā", 'e' to "èéêëēę", 'i' to "ìíîïī", 'o' to "òóôöõøō",
        'u' to "ùúûüū", 'c' to "çćč", 'n' to "ñń", 's' to "ßśš", 'y' to "ýÿ", 'z' to "žźż",
    )

    private fun key(c: String, alts: List<String> = emptyList(), w: Float = 1f, pill: Boolean = false) =
        Key(c, c, Kind.CHAR, w, alts, pill)

    private fun digitChar(i: Int, style: Int): String = when (style) {
        1 -> AI[i].toString()
        2 -> FA[i].toString()
        else -> LA[i].toString()
    }

    /** صف أرقام مستقل: الرقم الأساسي حسب الأسلوب، والبقية بدائل. */
    private fun digitsRow(w: Float, style: Int): List<Key> = (0 until 10).map { i ->
        val all = listOf(LA[i].toString(), AI[i].toString(), FA[i].toString())
        val main = digitChar(i, style)
        key(main, all.filter { it != main }, w)
    }

    private fun plain(s: String, w: Float = 1f) = s.map { key(it.toString(), emptyList(), w) }

    /** صف حروف عربية؛ digitStart >= 0 يعني أن أول تلميح لكل مفتاح هو رقم. */
    private fun arRow(s: String, digitStart: Int, style: Int, w: Float = 1f, extraLast: String? = null): List<Key> =
        s.mapIndexed { j, ch ->
            val alts = ArrayList<String>()
            val di = j + digitStart
            if (digitStart >= 0 && di in 0..9) alts.add(digitChar(di, style))
            else if (digitStart >= 0 && j == s.length - 1 && extraLast != null) alts.add(extraLast)
            alts.addAll(AR_ALTS[ch] ?: emptyList())
            key(ch.toString(), alts, w)
        }

    private fun del(w: Float) = Key("⌫", "", Kind.DELETE, w, emptyList(), true)
    private fun enter() = Key("↵", "\n", Kind.ENTER, 1.6f, emptyList(), true)
    private fun space(label: String) = Key(label, " ", Kind.SPACE, 5.2f)

    private fun bottomLetters(spaceLabel: String, c1: Key, c2: Key) = listOf(
        Key("@؟!", "", Kind.SYMBOLS, 1.3f, emptyList(), true),
        Key("123", "", Kind.NUMBERS, 1.3f, emptyList(), true),
        c1, space(spaceLabel), c2, enter(),
    )

    private fun bottomSym(letters: Key, other: Key) = listOf(
        letters.copy(weight = 1.3f, pill = true),
        other.copy(weight = 1.3f, pill = true),
        key(",", listOf(";", ":"), 1f, true), space(""), key(".", listOf("?", "!", "…"), 1f, true), enter(),
    )

    fun rows(layer: Layer, caps: Boolean, lastLetters: Layer, cfg: KbConfig): List<List<Key>> {
        val ds = cfg.digits
        val hintDigits = !cfg.numbers
        val toLetters = Key(if (lastLetters == Layer.EN) "ABC" else "أبج", "", Kind.LETTERS)
        val arC1 = key("،", listOf("؛", ",", ":"), 1f, true)
        val arC2 = key(".", listOf("؟", "!", "…"), 1f, true)
        var rows: List<List<Key>> = when (layer) {
            Layer.AR -> listOf(
                digitsRow(1.1f, ds),
                arRow("ضصثقفغعهخحج", if (hintDigits) 0 else -1, ds, 1f, "@"),
                arRow("شسيبلاتنمكط", -1, ds),
                arRow("ذءؤرىةوزظد", -1, ds) + del(1.3f),
                bottomLetters("العربية", arC1, arC2),
            )
            Layer.AR_EXT -> listOf(
                "\u064E\u064F\u0650\u064B\u064C\u064D\u0651\u0652\u0670".map { Key("ـ$it", it.toString(), Kind.CHAR, 1.1f) } +
                    Key("ـ", "ـ", Kind.CHAR, 1.1f),
                plain("پچژگڤڥڨکیھہں"),
                plain("ٹڈڑےېۇۈۆۋێەڕ"),
                listOf(Key("أب", "", Kind.EXT)) + plain("أإآٱڭڵٻڀٺٿ") + del(1.3f),
                bottomLetters("العربية", arC1, arC2),
            )
            Layer.EN -> {
                fun e(s: String, w: Float, digitStart: Int) = s.mapIndexed { j, ch ->
                    val c = if (caps) ch.uppercaseChar() else ch
                    val alts = ArrayList<String>()
                    if (digitStart >= 0 && j + digitStart in 0..9) alts.add(digitChar(j + digitStart, 0))
                    val acc = (EN_ALTS[ch] ?: "").let { x -> if (caps) x.uppercase() else x }
                    acc.forEach { alts.add(it.toString()) }
                    key(c.toString(), alts, w)
                }
                listOf(
                    digitsRow(1.1f, 0),
                    e("qwertyuiop", 1.1f, if (hintDigits) 0 else -1),
                    listOf(Key("", "", Kind.SPACER, 0.55f)) + e("asdfghjkl", 1.1f, -1) + Key("", "", Kind.SPACER, 0.55f),
                    listOf(Key("⇧", "", Kind.SHIFT, 1.65f)) + e("zxcvbnm", 1.1f, -1) + del(1.65f),
                    bottomLetters("English", key(",", listOf(";", ":"), 1f, true), key(".", listOf("?", "!", "…"), 1f, true)),
                )
            }
            Layer.NUM -> listOf(
                digitsRow(1.1f, ds),
                plain("+-×÷=/%^().", 1f),
                plain(AI, 1.1f),
                plain(FA, 1.1f) + del(1.3f),
                bottomSym(toLetters, Key("@؟!", "", Kind.SYMBOLS)),
            )
            Layer.SYM1 -> listOf(
                digitsRow(1.1f, ds),
                plain("@#$%&*-+()/!"),
                plain("\"':;_=~`|\\?."),
                listOf(Key("1/2", "", Kind.SYM_PAGE)) + plain("،؛؟«»٪٫€£¥") + del(1.3f),
                bottomSym(toLetters, Key("123", "", Kind.NUMBERS)),
            )
            Layer.SYM2 -> listOf(
                digitsRow(1.1f, ds),
                plain("°•…©®™✓×÷±≈≠"),
                plain("<>^{}[]√π∞¿¡"),
                listOf(Key("2/2", "", Kind.SYM_PAGE)) + plain("‹›“”‘’„‚–—") + del(1.3f),
                bottomSym(toLetters, Key("123", "", Kind.NUMBERS)),
            )
        }
        if (!cfg.numbers && (layer == Layer.AR || layer == Layer.EN)) rows = rows.drop(1)
        if (cfg.flip && rows.size >= 2) {
            val m = rows.toMutableList()
            // الصف الأخير: انعكاس كامل
            m[m.size - 1] = m[m.size - 1].reversed()
            // صف الحذف: ننقل مفاتيح الوظائف إلى الجهة الأخرى
            val r = m[m.size - 2].toMutableList()
            if (r.size >= 2) {
                if (r.first().kind != Kind.CHAR && r.first().kind != Kind.SPACER && r.last().kind != Kind.CHAR) {
                    val f = r[0]; r[0] = r[r.size - 1]; r[r.size - 1] = f
                } else if (r.last().kind == Kind.DELETE) {
                    val d = r.removeAt(r.size - 1)
                    r.add(0, d)
                }
                m[m.size - 2] = r
            }
            rows = m
        }
        return rows
    }
}
