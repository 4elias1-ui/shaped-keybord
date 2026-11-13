package com.arabickb.shaped

enum class Kind { CHAR, SHIFT, EXT, DELETE, ENTER, SPACE, SYMBOLS, LETTERS, SYM_PAGE, LANG, SPACER }
enum class Layer { AR, AR_EXT, EN, SYM1, SYM2 }

data class Key(
    val label: String,
    val text: String = label,
    val kind: Kind = Kind.CHAR,
    val weight: Float = 1f,
    val alts: List<String> = emptyList(),
)

object Layouts {
    private const val AI = "١٢٣٤٥٦٧٨٩٠"
    private const val FA = "۱۲۳۴۵۶۷۸۹۰"

    private val AR_ALTS = mapOf(
        'ق' to "ڨ", 'ف' to "ڤڥڦ", 'ه' to "ةەہھۀ", 'ج' to "چڄڃڇ", 'د' to "ڈڌڍڎ",
        'ي' to "ىئیێېۓے", 'ب' to "پٻڀ", 'ل' to "ڵ", 'ا' to "أإآٱ", 'ت' to "ٹٺٿ",
        'ن' to "ںڻ", 'ك' to "کگڭڳڱ", 'ر' to "ڕڑ", 'ز' to "ژ", 'و' to "ۆۇۈۋۅۉؤ",
        'ؤ' to "ۆۇۈۋ", 'ة' to "ەہھ", 'ى' to "يئی",
    )
    private val EN_ALTS = mapOf(
        'a' to "àáâäãåæā", 'e' to "èéêëēę", 'i' to "ìíîïī", 'o' to "òóôöõøō",
        'u' to "ùúûüū", 'c' to "çćč", 'n' to "ñń", 's' to "ßśš", 'y' to "ýÿ", 'z' to "žźż",
    )

    private fun k(c: String, alts: String = "", w: Float = 1f) =
        Key(c, c, Kind.CHAR, w, alts.map { it.toString() })

    private fun digits(w: Float) = "1234567890".mapIndexed { i, d -> k(d.toString(), "${AI[i]}${FA[i]}", w) }
    private fun plain(s: String, w: Float = 1f) = s.map { k(it.toString(), "", w) }
    private fun arRow(s: String) = s.map { k(it.toString(), AR_ALTS[it] ?: "") }
    private val del = Key("⌫", "", Kind.DELETE, 1f)

    private fun bottom(space: String, c1: String, c2: String, c3: String, left: Key) = listOf(
        left.copy(weight = 1.5f),
        Key("", "", Kind.LANG, 1f),
        Key(space, " ", Kind.SPACE, 5f),
        k(c1), k(c2), k(c3),
        Key("↵", "\n", Kind.ENTER, 1.5f),
    )

    fun rows(layer: Layer, caps: Boolean, lastLetters: Layer): List<List<Key>> {
        val toSym = Key("؟123", "", Kind.SYMBOLS)
        val toLetters = Key(if (lastLetters == Layer.EN) "ABC" else "أبج", "", Kind.LETTERS)
        return when (layer) {
            Layer.AR -> listOf(
                digits(1.2f),
                arRow("ضصثقفغعهخحجد"),
                arRow("شسيبلاتنمكطذ"),
                listOf(Key("پچ", "", Kind.EXT)) + arRow("ئءؤر") + k("لا") + arRow("ىةوزظ") + del,
                bottom("العربية", "،", ".", "؟", toSym),
            )
            Layer.AR_EXT -> listOf(
                "\u064E\u064F\u0650\u064B\u064C\u064D\u0651\u0652\u0670".map { Key("ـ$it", it.toString(), Kind.CHAR, 1.2f) } +
                    Key("ـ", "ـ", Kind.CHAR, 1.2f),
                plain("پچژگڤڥڨکیھہں"),
                plain("ٹڈڑےېۇۈۆۋێەڕ"),
                listOf(Key("أب", "", Kind.EXT)) + plain("أإآٱڭڵٻڀٺٿ") + del,
                bottom("العربية", "،", ".", "؟", toSym),
            )
            Layer.EN -> {
                fun e(s: String, w: Float) = s.map {
                    val c = if (caps) it.uppercaseChar() else it
                    val a = (EN_ALTS[it] ?: "").let { x -> if (caps) x.uppercase() else x }
                    k(c.toString(), a, w)
                }
                listOf(
                    digits(1.2f),
                    e("qwertyuiop", 1.2f),
                    listOf(Key("", "", Kind.SPACER, 0.6f)) + e("asdfghjkl", 1.2f) + Key("", "", Kind.SPACER, 0.6f),
                    listOf(Key("⇧", "", Kind.SHIFT, 1.8f)) + e("zxcvbnm", 1.2f) + del.copy(weight = 1.8f),
                    bottom("English", ",", ".", "?", toSym),
                )
            }
            Layer.SYM1 -> listOf(
                digits(1.2f),
                plain("@#$%&*-+()/!"),
                plain("\"':;_=~`|\\?."),
                listOf(Key("1/2", "", Kind.SYM_PAGE)) + plain("،؛؟«»٪٫€£¥") + del,
                bottom("", ",", ".", "!", toLetters),
            )
            Layer.SYM2 -> listOf(
                AI.map { k(it.toString(), "", 1.2f) },
                FA.map { k(it.toString(), "", 1.2f) },
                plain("°•…©®™✓×÷±≈≠"),
                listOf(Key("2/2", "", Kind.SYM_PAGE)) + plain("<>^{}[]√π∞") + del,
                bottom("", ",", ".", "!", toLetters),
            )
        }
    }
}
