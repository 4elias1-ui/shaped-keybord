package com.arabickb.shaped

/**
 * يحوّل النص العربي إلى "أشكال العرض" (Presentation Forms) ويقلب الترتيب ليصبح مرئياً
 * من اليسار لليمين، فيظهر صحيحاً في التطبيقات/الألعاب التي لا تدعم العربية.
 * ترتيب الأشكال في الجدول: [منفصل، نهائي، ابتدائي، وسطي]
 */
object ArabicShaper {
    private val FORMS: Map<Int, IntArray> = mapOf(
        0x0621 to intArrayOf(0xFE80, 0xFE80, 0xFE80, 0xFE80),
        0x0622 to intArrayOf(0xFE81, 0xFE82, 0xFE81, 0xFE82),
        0x0623 to intArrayOf(0xFE83, 0xFE84, 0xFE83, 0xFE84),
        0x0624 to intArrayOf(0xFE85, 0xFE86, 0xFE85, 0xFE86),
        0x0625 to intArrayOf(0xFE87, 0xFE88, 0xFE87, 0xFE88),
        0x0626 to intArrayOf(0xFE89, 0xFE8A, 0xFE8B, 0xFE8C),
        0x0627 to intArrayOf(0xFE8D, 0xFE8E, 0xFE8D, 0xFE8E),
        0x0628 to intArrayOf(0xFE8F, 0xFE90, 0xFE91, 0xFE92),
        0x0629 to intArrayOf(0xFE93, 0xFE94, 0xFE93, 0xFE94),
        0x062A to intArrayOf(0xFE95, 0xFE96, 0xFE97, 0xFE98),
        0x062B to intArrayOf(0xFE99, 0xFE9A, 0xFE9B, 0xFE9C),
        0x062C to intArrayOf(0xFE9D, 0xFE9E, 0xFE9F, 0xFEA0),
        0x062D to intArrayOf(0xFEA1, 0xFEA2, 0xFEA3, 0xFEA4),
        0x062E to intArrayOf(0xFEA5, 0xFEA6, 0xFEA7, 0xFEA8),
        0x062F to intArrayOf(0xFEA9, 0xFEAA, 0xFEA9, 0xFEAA),
        0x0630 to intArrayOf(0xFEAB, 0xFEAC, 0xFEAB, 0xFEAC),
        0x0631 to intArrayOf(0xFEAD, 0xFEAE, 0xFEAD, 0xFEAE),
        0x0632 to intArrayOf(0xFEAF, 0xFEB0, 0xFEAF, 0xFEB0),
        0x0633 to intArrayOf(0xFEB1, 0xFEB2, 0xFEB3, 0xFEB4),
        0x0634 to intArrayOf(0xFEB5, 0xFEB6, 0xFEB7, 0xFEB8),
        0x0635 to intArrayOf(0xFEB9, 0xFEBA, 0xFEBB, 0xFEBC),
        0x0636 to intArrayOf(0xFEBD, 0xFEBE, 0xFEBF, 0xFEC0),
        0x0637 to intArrayOf(0xFEC1, 0xFEC2, 0xFEC3, 0xFEC4),
        0x0638 to intArrayOf(0xFEC5, 0xFEC6, 0xFEC7, 0xFEC8),
        0x0639 to intArrayOf(0xFEC9, 0xFECA, 0xFECB, 0xFECC),
        0x063A to intArrayOf(0xFECD, 0xFECE, 0xFECF, 0xFED0),
        0x0641 to intArrayOf(0xFED1, 0xFED2, 0xFED3, 0xFED4),
        0x0642 to intArrayOf(0xFED5, 0xFED6, 0xFED7, 0xFED8),
        0x0643 to intArrayOf(0xFED9, 0xFEDA, 0xFEDB, 0xFEDC),
        0x0644 to intArrayOf(0xFEDD, 0xFEDE, 0xFEDF, 0xFEE0),
        0x0645 to intArrayOf(0xFEE1, 0xFEE2, 0xFEE3, 0xFEE4),
        0x0646 to intArrayOf(0xFEE5, 0xFEE6, 0xFEE7, 0xFEE8),
        0x0647 to intArrayOf(0xFEE9, 0xFEEA, 0xFEEB, 0xFEEC),
        0x0648 to intArrayOf(0xFEED, 0xFEEE, 0xFEED, 0xFEEE),
        0x0649 to intArrayOf(0xFEEF, 0xFEF0, 0xFEF3, 0xFEF4),
        0x064A to intArrayOf(0xFEF1, 0xFEF2, 0xFEF3, 0xFEF4),
        0x0671 to intArrayOf(0xFB50, 0xFB51, 0xFB50, 0xFB51),
        0x0679 to intArrayOf(0xFB66, 0xFB67, 0xFB68, 0xFB69),
        0x067A to intArrayOf(0xFB5E, 0xFB5F, 0xFB60, 0xFB61),
        0x067B to intArrayOf(0xFB52, 0xFB53, 0xFB54, 0xFB55),
        0x067E to intArrayOf(0xFB56, 0xFB57, 0xFB58, 0xFB59),
        0x067F to intArrayOf(0xFB62, 0xFB63, 0xFB64, 0xFB65),
        0x0680 to intArrayOf(0xFB5A, 0xFB5B, 0xFB5C, 0xFB5D),
        0x0683 to intArrayOf(0xFB76, 0xFB77, 0xFB78, 0xFB79),
        0x0684 to intArrayOf(0xFB72, 0xFB73, 0xFB74, 0xFB75),
        0x0686 to intArrayOf(0xFB7A, 0xFB7B, 0xFB7C, 0xFB7D),
        0x0687 to intArrayOf(0xFB7E, 0xFB7F, 0xFB80, 0xFB81),
        0x0688 to intArrayOf(0xFB88, 0xFB89, 0xFB88, 0xFB89),
        0x068C to intArrayOf(0xFB84, 0xFB85, 0xFB84, 0xFB85),
        0x068D to intArrayOf(0xFB82, 0xFB83, 0xFB82, 0xFB83),
        0x068E to intArrayOf(0xFB86, 0xFB87, 0xFB86, 0xFB87),
        0x0691 to intArrayOf(0xFB8C, 0xFB8D, 0xFB8C, 0xFB8D),
        0x0698 to intArrayOf(0xFB8A, 0xFB8B, 0xFB8A, 0xFB8B),
        0x06A4 to intArrayOf(0xFB6A, 0xFB6B, 0xFB6C, 0xFB6D),
        0x06A6 to intArrayOf(0xFB6E, 0xFB6F, 0xFB70, 0xFB71),
        0x06A9 to intArrayOf(0xFB8E, 0xFB8F, 0xFB90, 0xFB91),
        0x06AD to intArrayOf(0xFBD3, 0xFBD4, 0xFBD5, 0xFBD6),
        0x06AF to intArrayOf(0xFB92, 0xFB93, 0xFB94, 0xFB95),
        0x06B1 to intArrayOf(0xFB9A, 0xFB9B, 0xFB9C, 0xFB9D),
        0x06B3 to intArrayOf(0xFB96, 0xFB97, 0xFB98, 0xFB99),
        0x06BA to intArrayOf(0xFB9E, 0xFB9F, 0xFB9E, 0xFB9F),
        0x06BB to intArrayOf(0xFBA0, 0xFBA1, 0xFBA2, 0xFBA3),
        0x06BE to intArrayOf(0xFBAA, 0xFBAB, 0xFBAC, 0xFBAD),
        0x06C0 to intArrayOf(0xFBA4, 0xFBA5, 0xFBA4, 0xFBA5),
        0x06C1 to intArrayOf(0xFBA6, 0xFBA7, 0xFBA8, 0xFBA9),
        0x06C5 to intArrayOf(0xFBE0, 0xFBE1, 0xFBE0, 0xFBE1),
        0x06C6 to intArrayOf(0xFBD9, 0xFBDA, 0xFBD9, 0xFBDA),
        0x06C7 to intArrayOf(0xFBD7, 0xFBD8, 0xFBD7, 0xFBD8),
        0x06C8 to intArrayOf(0xFBDB, 0xFBDC, 0xFBDB, 0xFBDC),
        0x06C9 to intArrayOf(0xFBE2, 0xFBE3, 0xFBE2, 0xFBE3),
        0x06CB to intArrayOf(0xFBDE, 0xFBDF, 0xFBDE, 0xFBDF),
        0x06CC to intArrayOf(0xFBFC, 0xFBFD, 0xFBFE, 0xFBFF),
        0x06D0 to intArrayOf(0xFBE4, 0xFBE5, 0xFBE6, 0xFBE7),
        0x06D2 to intArrayOf(0xFBAE, 0xFBAF, 0xFBAE, 0xFBAF),
        0x06D3 to intArrayOf(0xFBB0, 0xFBB1, 0xFBB0, 0xFBB1),
    )

    /** لام + ألف: [منفصل، نهائي] */
    private val LAM_ALEF: Map<Int, IntArray> = mapOf(
        0x0622 to intArrayOf(0xFEF5, 0xFEF6),
        0x0623 to intArrayOf(0xFEF7, 0xFEF8),
        0x0625 to intArrayOf(0xFEF9, 0xFEFA),
        0x0627 to intArrayOf(0xFEFB, 0xFEFC),
    )

    /** حروف ليس لها أشكال عرض في يونيكود: تُستبدل بأقرب حرف (من حيث الصوت/الشكل). */
    private val FALLBACK: Map<Int, Int> = mapOf(
        0x06A5 to 0x06A4, // ڥ -> ڤ
        0x06A8 to 0x06AF, // ڨ -> گ
        0x0695 to 0x0631, // ڕ -> ر
        0x06B5 to 0x0644, // ڵ -> ل
        0x06CE to 0x06CC, // ێ -> ی
    )

    private val MIRROR: Map<Char, Char> = mapOf(
        '(' to ')', ')' to '(', '[' to ']', ']' to '[', '{' to '}', '}' to '{',
        '<' to '>', '>' to '<', '«' to '»', '»' to '«',
    )

    private const val R = 0
    private const val L = 1
    private const val N = 2

    private class Cl(val cp: Int) { val tail = StringBuilder() }
    private class G(val text: String, var dir: Int)

    private fun str(cp: Int) = String(Character.toChars(cp))

    private fun attaches(cp: Int) =
        cp in 0x064B..0x065F || cp == 0x0670 || cp in 0x06D6..0x06DC || cp in 0x06DF..0x06E4 ||
            cp == 0x06E7 || cp == 0x06E8 || cp in 0x06EA..0x06ED || cp in 0x0300..0x036F ||
            cp == 0x200D || cp == 0xFE0F || cp in 0x1F3FB..0x1F3FF

    private fun baseOf(cp: Int) = FALLBACK[cp] ?: cp
    private fun joinsNext(b: Int) = b == 0x0640 || FORMS[b]?.let { it[2] != it[0] } == true
    private fun joinsPrev(b: Int) = b == 0x0640 || b == 0x06D5 || FORMS[b]?.let { it[1] != it[0] } == true

    private fun classify(cp: Int): Int = when {
        cp in 0x30..0x39 || cp in 0x0660..0x0669 || cp in 0x06F0..0x06F9 -> L
        cp > 0xFFFF || cp == 0x200D -> L
        cp == 0x060C || cp == 0x061B || cp == 0x061F -> N
        cp in 0x0600..0x06FF || cp in 0x0750..0x077F || cp in 0xFB50..0xFDFF || cp in 0xFE70..0xFEFF -> R
        Character.isLetter(cp) -> L
        else -> N
    }

    /** يحوّل النص كاملاً (كل سطر على حدة). */
    fun shape(text: String): String = text.replace("\r", "").split("\n").joinToString("\n") { shapeLine(it) }

    private fun shapeLine(line: String): String {
        if (line.isEmpty()) return line
        // 1) تقسيم إلى مقاطع (حرف أساسي + علاماته)
        val cl = ArrayList<Cl>()
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            i += Character.charCount(cp)
            if (attaches(cp) && cl.isNotEmpty()) cl.last().tail.appendCodePoint(cp) else cl.add(Cl(cp))
        }
        // 2) التشكيل (اختيار الشكل المناسب لكل حرف)
        val out = ArrayList<G>(cl.size)
        var prevJoins = false
        var k = 0
        while (k < cl.size) {
            val c = cl[k]
            val base = baseOf(c.cp)
            if (c.cp == 0x0644 && k + 1 < cl.size && LAM_ALEF.containsKey(cl[k + 1].cp)) {
                val la = LAM_ALEF.getValue(cl[k + 1].cp)
                out.add(G(str(if (prevJoins) la[1] else la[0]) + c.tail + cl[k + 1].tail, R))
                prevJoins = false
                k += 2
                continue
            }
            val forms = FORMS[base]
            if (forms == null && base != 0x0640 && base != 0x06D5) {
                out.add(G(str(c.cp) + c.tail, classify(c.cp)))
                prevJoins = false
                k++
                continue
            }
            val nextJoins = k + 1 < cl.size && joinsPrev(baseOf(cl[k + 1].cp))
            val canNext = joinsNext(base)
            val idx = when {
                prevJoins && canNext && nextJoins -> 3
                prevJoins -> 1
                canNext && nextJoins -> 2
                else -> 0
            }
            val glyph = if (forms == null) base else forms[idx]
            out.add(G(str(glyph) + c.tail, R))
            prevJoins = canNext && nextJoins
            k++
        }
        // 3) تحديد اتجاه المحايدات (مسافات/ترقيم)
        val n = out.size
        val left = IntArray(n)
        val right = IntArray(n)
        var last = -1
        for (j in 0 until n) { left[j] = last; if (out[j].dir != N) last = out[j].dir }
        last = -1
        for (j in n - 1 downTo 0) { right[j] = last; if (out[j].dir != N) last = out[j].dir }
        for (j in 0 until n) if (out[j].dir == N) out[j].dir = if (left[j] == L && right[j] == L) L else R
        // 4) تجميع في مقاطع اتجاه ثم قلب الترتيب
        val runs = ArrayList<Pair<Int, MutableList<G>>>()
        for (g in out) {
            if (runs.isNotEmpty() && runs.last().first == g.dir) runs.last().second.add(g)
            else runs.add(Pair(g.dir, mutableListOf(g)))
        }
        val sb = StringBuilder()
        for (run in runs.asReversed()) {
            if (run.first == R) {
                for (g in run.second.asReversed()) {
                    val m = if (g.text.length == 1) MIRROR[g.text[0]] else null
                    sb.append(m ?: g.text)
                }
            } else {
                for (g in run.second) sb.append(g.text)
            }
        }
        return sb.toString()
    }
}
