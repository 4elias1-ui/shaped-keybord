package com.arabickb.shaped

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View

class Palette(
    val bg: Int, val key: Int, val func: Int, val pressed: Int,
    val text: Int, val hint: Int, val accent: Int, val onAccent: Int,
) {
    companion object {
        fun of(night: Boolean) = if (night)
            Palette(0xFF16171B.toInt(), 0xFF2B2D33.toInt(), 0xFF3A3D45.toInt(), 0xFF4C505B.toInt(),
                0xFFF1F2F6.toInt(), 0xFF8A8F9C.toInt(), 0xFF7C9BFF.toInt(), 0xFF0B1230.toInt())
        else
            Palette(0xFFE4E7EE.toInt(), 0xFFFFFFFF.toInt(), 0xFFCBD0DB.toInt(), 0xFFB4BBCB.toInt(),
                0xFF16181D.toInt(), 0xFF7B8190.toInt(), 0xFF3F5EFB.toInt(), 0xFFFFFFFF.toInt())
    }
}

class KeyboardView(context: Context, night: Boolean) : View(context) {
    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onLangLongPress()
    }

    var listener: Listener? = null
    var keyHeightDp = 56f
        set(v) { field = v; requestLayout() }
    var haptic = true
    var sound = false
    private val pal = Palette.of(night)
    private var layer = Layer.AR
    private var lastLetters = Layer.AR
    private var shift = 0 // 0 = off, 1 = once, 2 = lock
    private var lastShiftTap = 0L

    private class Cell(val key: Key, val r: RectF, val row: Int)
    private var cells: List<List<Cell>> = emptyList()
    private var rowCount = 5
    private var cachedRows: List<List<Key>> = emptyList()

    private inner class Press(val cell: Cell) {
        var longDone = false
        var job: Runnable? = null
    }
    private class Popup(val pointer: Int, val items: List<String>, val rects: List<RectF>) {
        var hover = -1
        fun hit(x: Float, y: Float) = rects.indexOfFirst { it.contains(x, y) }
    }
    private val presses = HashMap<Int, Press>()
    private var popup: Popup? = null
    private val handler = Handler(Looper.getMainLooper())

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity

    init { rebuild() }

    private fun rebuild() {
        val rows = Layouts.rows(layer, shift != 0, lastLetters)
        rowCount = rows.size
        layoutCells(rows, width)
        invalidate()
    }

    private fun layoutCells(rows: List<List<Key>>, w: Int) {
        cachedRows = rows
        val pad = dp(8f); val gap = dp(6f); val kh = dp(keyHeightDp)
        val out = ArrayList<List<Cell>>()
        rows.forEachIndexed { ri, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val avail = w - 2 * pad - gap * (row.size - 1)
            var x = pad
            val y = pad + ri * (kh + gap)
            out.add(row.map { key ->
                val kw = avail * key.weight / total
                val c = Cell(key, RectF(x, y, x + kw, y + kh), ri)
                x += kw + gap
                c
            })
        }
        cells = out
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val h = dp(8f) * 2 + rowCount * dp(keyHeightDp) + (rowCount - 1) * dp(6f)
        setMeasuredDimension(MeasureSpec.getSize(wSpec), h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        layoutCells(cachedRows.ifEmpty { Layouts.rows(layer, shift != 0, lastLetters) }, w)
    }

    private fun setLayer(l: Layer) {
        layer = l
        if (l == Layer.AR || l == Layer.EN) lastLetters = l
        shift = 0
        rebuild()
    }

    private fun labelOf(k: Key, row: Int): String = when (k.kind) {
        Kind.SHIFT -> if (shift == 2) "⇪" else "⇧"
        Kind.LANG -> if (layer == Layer.EN || (layer in listOf(Layer.SYM1, Layer.SYM2) && lastLetters == Layer.EN)) "ع" else "EN"
        else -> k.label
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(pal.bg)
        val rad = dp(10f)
        for (row in cells) for (cell in row) {
            val k = cell.key
            if (k.kind == Kind.SPACER) continue
            val isPressed = presses.values.any { it.cell === cell }
            val isFunc = k.kind != Kind.CHAR && k.kind != Kind.SPACE
            fill.color = when {
                isPressed -> pal.pressed
                k.kind == Kind.ENTER -> pal.accent
                k.kind == Kind.SHIFT && shift != 0 -> pal.accent
                isFunc -> pal.func
                else -> pal.key
            }
            c.drawRoundRect(cell.r, rad, rad, fill)
            val onAcc = k.kind == Kind.ENTER || (k.kind == Kind.SHIFT && shift != 0)
            txt.color = if (onAcc) pal.onAccent else pal.text
            val label = labelOf(k, cell.row)
            txt.textSize = when {
                k.kind == Kind.SPACE -> sp(13f)
                k.kind == Kind.CHAR && label.length <= 2 -> sp(22f)
                else -> sp(16f)
            }
            if (k.kind == Kind.SPACE) txt.color = pal.hint
            val fm = txt.fontMetrics
            c.drawText(label, cell.r.centerX(), cell.r.centerY() - (fm.ascent + fm.descent) / 2, txt)
            if (k.kind == Kind.CHAR && k.alts.isNotEmpty()) {
                txt.color = pal.hint
                txt.textSize = sp(11f)
                val f2 = txt.fontMetrics
                c.drawText(k.alts[0], cell.r.right - dp(10f), cell.r.top + dp(6f) - f2.ascent, txt)
            }
        }
        popup?.let { p ->
            p.rects.forEachIndexed { i, r ->
                fill.color = if (i == p.hover) pal.accent else pal.func
                c.drawRoundRect(r, rad, rad, fill)
                txt.color = if (i == p.hover) pal.onAccent else pal.text
                txt.textSize = sp(24f)
                val fm = txt.fontMetrics
                c.drawText(p.items[i], r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, txt)
            }
        }
    }

    private fun hit(x: Float, y: Float): Cell? {
        var best: Cell? = null
        var bd = Float.MAX_VALUE
        for (row in cells) for (cell in row) {
            if (cell.key.kind == Kind.SPACER) continue
            val dx = if (x < cell.r.left) cell.r.left - x else if (x > cell.r.right) x - cell.r.right else 0f
            val dy = if (y < cell.r.top) cell.r.top - y else if (y > cell.r.bottom) y - cell.r.bottom else 0f
            val d = dx * dx + dy * dy
            if (d < bd) { bd = d; best = cell }
        }
        return if (bd <= dp(8f) * dp(8f)) best else null
    }

    private fun begin(id: Int, x: Float, y: Float) {
        val cell = hit(x, y) ?: return
        val p = Press(cell)
        presses[id] = p
        if (haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (sound) playSoundEffect(SoundEffectConstants.CLICK)
        val k = cell.key
        when {
            k.kind == Kind.DELETE -> {
                p.longDone = true
                listener?.onDelete()
                val rep = object : Runnable {
                    override fun run() { listener?.onDelete(); handler.postDelayed(this, 45) }
                }
                p.job = rep
                handler.postDelayed(rep, 420)
            }
            k.kind == Kind.CHAR && k.alts.isNotEmpty() || k.kind == Kind.LANG -> {
                val r = Runnable { longPress(id, p) }
                p.job = r
                handler.postDelayed(r, 380)
            }
        }
        invalidate()
    }

    private fun longPress(id: Int, p: Press) {
        p.longDone = true
        val k = p.cell.key
        if (k.kind == Kind.LANG) { listener?.onLangLongPress(); presses.remove(id); invalidate(); return }
        if (haptic) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val cw = maxOf(p.cell.r.width(), dp(52f))
        val total = cw * k.alts.size
        val x0 = (p.cell.r.centerX() - total / 2).coerceIn(dp(4f), maxOf(dp(4f), width - total - dp(4f)))
        val kh = dp(keyHeightDp)
        val y0 = if (p.cell.row > 0) p.cell.r.top - kh - dp(6f) else p.cell.r.bottom + dp(6f)
        val rects = k.alts.indices.map { RectF(x0 + it * cw, y0, x0 + (it + 1) * cw - dp(4f), y0 + kh) }
        popup = Popup(id, k.alts, rects)
        invalidate()
    }

    private fun end(id: Int, x: Float, y: Float, cancel: Boolean) {
        val p = presses.remove(id) ?: return
        p.job?.let { handler.removeCallbacks(it) }
        val pop = popup
        if (pop != null && pop.pointer == id) {
            val i = pop.hit(x, y)
            popup = null
            if (!cancel && i >= 0) { listener?.onText(pop.items[i]); afterChar() }
            invalidate()
            return
        }
        if (!p.longDone && !cancel) fire(p.cell.key)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                begin(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val p = presses[id] ?: continue
                    val pop = popup
                    if (pop != null && pop.pointer == id) {
                        val h = pop.hit(e.getX(i), e.getY(i))
                        if (h != pop.hover) { pop.hover = h; invalidate() }
                    } else {
                        val cell = hit(e.getX(i), e.getY(i))
                        if (cell?.key !== p.cell.key) {
                            p.job?.let { handler.removeCallbacks(it) }
                            presses.remove(id)
                            begin(id, e.getX(i), e.getY(i))
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                end(e.getPointerId(i), e.getX(i), e.getY(i), false)
            }
            MotionEvent.ACTION_CANCEL -> {
                presses.keys.toList().forEach { end(it, 0f, 0f, true) }
                popup = null
                invalidate()
            }
        }
        return true
    }

    private fun afterChar() {
        if (layer == Layer.EN && shift == 1) { shift = 0; rebuild() }
    }

    private fun fire(k: Key) {
        when (k.kind) {
            Kind.CHAR, Kind.SPACE -> { listener?.onText(k.text); afterChar() }
            Kind.ENTER -> listener?.onEnter()
            Kind.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                shift = if (shift == 2) 0 else if (now - lastShiftTap < 350 && shift == 1) 2 else if (shift == 0) 1 else 0
                lastShiftTap = now
                rebuild()
            }
            Kind.EXT -> setLayer(if (layer == Layer.AR_EXT) Layer.AR else Layer.AR_EXT)
            Kind.SYMBOLS -> setLayer(Layer.SYM1)
            Kind.LETTERS -> setLayer(lastLetters)
            Kind.SYM_PAGE -> setLayer(if (layer == Layer.SYM1) Layer.SYM2 else Layer.SYM1)
            Kind.LANG -> setLayer(if (layer == Layer.EN || (layer in listOf(Layer.SYM1, Layer.SYM2) && lastLetters == Layer.EN)) Layer.AR else Layer.EN)
            else -> {}
        }
    }
}
