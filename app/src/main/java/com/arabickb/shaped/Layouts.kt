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

    /** الحركات الإعرابية: تظهر عند الضغط على الفارزة والسحب للأعلى. */
    val HARAKAT: List<String> = listOf(
        "\u064E", "\u064F", "\u0650", "\u0652", "\u0651", "\u064B", "\u064C", "\u064D", "\u0670",
    )

    private fun row(s: String): List<String> = s.split(" ")

    /** تخطيطات الحروف العربية (3 صفوف). */
    private val AR_LAYOUTS: List<List<List<String>>> = listOf(
        // 0: حديث (مثل لوحات الجوال)
        listOf(
            row("ض ص ث ق ف غ ع ه خ ح ج"),
            row("ش س ي ب ل ا ت ن م ك ط"),
            row("ذ ء ؤ ر ى ة و ز ظ د"),
        ),
        // 1: Windows القياسي (العربية 101)
        listOf(
            row("ض ص ث ق ف غ ع ه خ ح ج د"),
            row("ش س ي ب ل ا ت ن م ك ط ذ"),
            row("ئ ء ؤ ر لا ى ة و ز ظ"),
        ),
        // 2: أبجدي (حسب ترتيب الحروف)
        listOf(
            row("ا ب ت ث ج ح خ د ذ ر ز"),
            row("س ش ص ض ط ظ ع غ ف ق ك"),
            row("ل م ن ه و ي ة ء ؤ ئ"),
        ),
        // 3: صوتي (على مواضع QWERTY حسب أقرب صوت)
        listOf(
            row("ق و ع ر ت ي ء ئ ؤ ة"),
            row("ا س د ف غ ه ج ك ل"),
            row("ز خ ث ڤ ب ن م"),
        ),
        // 4: فارسي (ISIRI 9147 القياسي)
        listOf(
            row("ض ص ث ق ف غ ع ه خ ح ج چ"),
            row("ش س ی ب ل ا ت ن م ک گ"),
            row("ظ ط ز ر ذ د پ و ژ ئ"),
        ),
    )

    /** تخطيطات الحروف الإنجليزية (3 صفوف). */
    private val EN_LAYOUTS: List<List<String>> = listOf(
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),       // QWERTY
        listOf("azertyuiop", "qsdfghjklm", "wxcvbn"),       // AZERTY
        listOf("qwertzuiop", "asdfghjkl", "yxcvbnm"),       // QWERTZ
        listOf("',.pyfgcrl", "aoeuidhtns", ";qjkxbmwvz"),   // Dvorak
        listOf("qwfpgjluy;", "arstdhneio", "zxcvbkm"),      // Colemak
    )

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
    /** في التخطيط الصوتي بعض الحروف غير ظاهرة على المفاتيح فتكون بدائل لأقرب مفتاح صوتياً. */
    private val PH_ALTS: Map<Char, List<String>> = mapOf(
        'س' to listOf("ش", "ص"),
        'د' to listOf("ض", "ذ"),
        'ت' to listOf("ط", "ٹ", "ٺ", "ٿ"),
        'ز' to listOf("ظ", "ژ"),
        'ه' to listOf("ح", "ة", "ە", "ہ", "ھ", "ۀ"),
        'ا' to listOf("أ", "إ", "آ", "ٱ"),
    )
    private val FA_ALTS: Map<Char, List<String>> = mapOf(
        'ک' to listOf("ك"),
        'ی' to listOf("ي", "ى", "ئ", "ێ"),
        'ا' to listOf("آ", "أ", "إ", "ٱ"),
        'و' to listOf("ؤ", "ۆ", "ۇ"),
        'ه' to listOf("ة", "ە", "ھ", "ہ"),
        'ر' to listOf("ڕ", "ڑ"),
        'ن' to listOf("ں", "ڻ"),
        'ئ' to listOf("ء", "ؤ"),
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

    /**
     * أسلوب الأرقام الفعلي: إن كان الإعداد "حسب اللغة" فالإنجليزية ← لاتينية،
     * والعربية ← عربية (أو فارسية مع التخطيط الفارسي).
     */
    private fun effDigits(layer: Layer, lastLetters: Layer, cfg: KbConfig): Int {
        if (cfg.digits != 3) return cfg.digits
        val english = layer == Layer.EN ||
            ((layer == Layer.NUM || layer == Layer.SYM1 || layer == Layer.SYM2) && lastLetters == Layer.EN)
        if (english) return 0
        return if (cfg.arLayout == 4) 2 else 1
    }

    private fun digitsRow(w: Float, style: Int): List<Key> = (0 until 10).map { i ->
        val all = listOf(LA[i].toString(), AI[i].toString(), FA[i].toString())
        val main = digitChar(i, style)
        key(main, all.filter { it != main }, w)
    }

    private fun plain(s: String, w: Float = 1f) = s.map { key(it.toString(), emptyList(), w) }

    private fun altsFor(tok: String, layout: Int): List<String> {
        if (tok.length != 1) return emptyList()
        val ch = tok[0]
        val m: List<String>? = when (layout) {
            3 -> PH_ALTS[ch] ?: AR_ALTS[ch]
            4 -> FA_ALTS[ch] ?: AR_ALTS[ch]
            else -> AR_ALTS[ch]
        }
        return m ?: emptyList()
    }

    private fun del(w: Float) = Key("⌫", "", Kind.DELETE, w, emptyList(), true)
    private fun enter() = Key("↵", "\n", Kind.ENTER, 1.6f, emptyList(), true)
    private fun space(label: String) = Key(label, " ", Kind.SPACE, 5.2f)
    private fun spacer(w: Float) = Key("", "", Kind.SPACER, w)

    /** يضيف فراغاً جانبياً ليصبح مجموع أوزان الصف قريباً من target. */
    private fun pad(keys: List<Key>, target: Float, leftOnly: Boolean): List<Key> {
        val sum = keys.sumOf { it.weight.toDouble() }.toFloat()
        val gap = target - sum
        if (gap < 0.4f) return keys
        return if (leftOnly) listOf(spacer(gap)) + keys else listOf(spacer(gap / 2)) + keys + spacer(gap / 2)
    }

    private fun arRows(cfg: KbConfig, ds: Int, hintDigits: Boolean): List<List<Key>> {
        val li = cfg.arLayout.coerceIn(0, AR_LAYOUTS.size - 1)
        val lay = AR_LAYOUTS[li]
        fun keys(tokens: List<String>, digits: Boolean): List<Key> = tokens.mapIndexed { j, tok ->
            val a = ArrayList<String>()
            if (digits && j < 10) a.add(digitChar(j, ds))
            a.addAll(altsFor(tok, li))
            key(tok, a)
        }
        val r1 = keys(lay[0], hintDigits)
        val r2 = keys(lay[1], false)
        val r3 = keys(lay[2], false)
        val dw = 1.3f
        val target = maxOf(11f, r1.size.toFloat(), r2.size.toFloat(), r3.size + dw)
        return listOf(pad(r1, target, false), pad(r2, target, false), pad(r3 + del(dw), target, true))
    }

    private fun enRows(caps: Boolean, cfg: KbConfig, hintDigits: Boolean): List<List<Key>> {
        val lay = EN_LAYOUTS[cfg.enLayout.coerceIn(0, EN_LAYOUTS.size - 1)]
        fun e(s: String, digitStart: Int): List<Key> = s.mapIndexed { j, ch ->
            val c = if (caps) ch.uppercaseChar() else ch
            val alts = ArrayList<String>()
            if (digitStart >= 0 && j + digitStart in 0..9) alts.add(digitChar(j + digitStart, 0))
            val acc = (EN_ALTS[ch] ?: "").let { x -> if (caps) x.uppercase() else x }
            acc.forEach { alts.add(it.toString()) }
            key(c.toString(), alts, 1.1f)
        }
        val r1 = e(lay[0], if (hintDigits) 0 else -1)
        val n2 = lay[1].length
        val r2raw = e(lay[1], -1)
        val sp = ((11f - n2 * 1.1f) / 2f)
        val r2 = if (sp >= 0.2f) listOf(spacer(sp)) + r2raw + spacer(sp) else r2raw
        val n3 = lay[2].length
        val fw = ((11f - n3 * 1.1f) / 2f).coerceAtLeast(1.0f)
        val r3 = listOf(Key("⇧", "", Kind.SHIFT, fw)) + e(lay[2], -1) + del(fw)
        return listOf(r1, r2, r3)
    }

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
        val ds = effDigits(layer, lastLetters, cfg)
        val hintDigits = !cfg.numbers
        val head: List<List<Key>> = if (cfg.numbers) listOf(digitsRow(1.1f, ds)) else emptyList()
        val toLetters = Key(if (lastLetters == Layer.EN) "ABC" else "أبج", "", Kind.LETTERS)
        // الفارزة العربية: الضغط عليها والسحب للأعلى يعرض الحركات الإعرابية
        val arC1 = key("،", Layouts.HARAKAT + listOf("؛", ",", ":"), 1f, true)
        val arC2 = key(".", listOf("؟", "!", "…"), 1f, true)
        val arName = if (cfg.arLayout == 4) "فارسی" else "العربية"
        var rows: List<List<Key>> = when (layer) {
            Layer.AR -> head + arRows(cfg, ds, hintDigits) + listOf(bottomLetters(arName, arC1, arC2))
            Layer.AR_EXT -> listOf(
                "\u064E\u064F\u0650\u064B\u064C\u064D\u0651\u0652\u0670".map { Key("ـ$it", it.toString(), Kind.CHAR, 1.1f) } +
                    Key("ـ", "ـ", Kind.CHAR, 1.1f),
                plain("پچژگڤڥڨکیھہں"),
                plain("ٹڈڑےېۇۈۆۋێەڕ"),
                listOf(Key("أب", "", Kind.EXT)) + plain("أإآٱڭڵٻڀٺٿ") + del(1.3f),
                bottomLetters(arName, arC1, arC2),
            )
            Layer.EN -> head + enRows(caps, cfg, hintDigits) +
                listOf(bottomLetters("English", key(",", listOf(";", ":"), 1f, true), key(".", listOf("?", "!", "…"), 1f, true)))
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
        if (cfg.flip && rows.size >= 2) {
            val m = rows.toMutableList()
            // الصف الأخير: انعكاس كامل
            m[m.size - 1] = m[m.size - 1].reversed()
            // صف الحذف: ننقل مفاتيح الوظائف إلى الجهة الأخرى
            val r = m[m.size - 2].toMutableList()
            if (r.size >= 2) {
                if (r.first().kind != Kind.CHAR && r.last().kind != Kind.CHAR) {
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
