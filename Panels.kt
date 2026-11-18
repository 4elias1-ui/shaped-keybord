package com.arabickb.shaped

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object EmojiData {
    /** (أيقونة التبويب, الرموز مفصولة بمسافة) */
    val CATS: List<Pair<String, String>> = listOf(
        "😀" to "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕",
        "👍" to "👍 👎 👌 ✌ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝ ✋ 🤚 🖐 🖖 👋 🤝 🙏 ✍ 💪 👏 🙌 👐 🤲 🤜 🤛 ✊ 👊 🫶 🫡 🙈 🙉 🙊 👀 👁 👅 👄 🧠",
        "❤" to "❤ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣ 💕 💞 💓 💗 💖 💘 💝 💟 ♥ 💋 💯 💢 💥 💫 💦 💨 🕊 ✨ ⭐ 🌟 🔥 🎉 🎊 🎁 🏆 🥇 🎯",
        "🐱" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦀 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐘 🐪 🐫 🌵 🌴 🌳 🌸 🌹 🌻 🌼 🍀 🍁",
        "🍕" to "🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🍆 🥕 🌽 🥔 🍞 🧀 🥚 🍳 🥓 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🍝 🍜 🍣 🍤 🍦 🍩 🍪 🎂 🍰 🍫 🍬 🍭 ☕ 🍵 🥤 🍺 🍷",
        "⚽" to "⚽ 🏀 🏈 ⚾ 🎾 🏐 🎱 🏓 🎮 🕹 🎲 🧩 🎧 🎤 🎸 🎹 🎬 📷 📱 💻 ⌚ 💡 🔋 🔑 🔒 📌 📎 ✏ 📚 💰 💎 ⚡ ☀ 🌙 ⭐ ☁ ☔ ❄ 🌈 🚗 ✈ 🚀 ⏰ ✅ ❌ ⚠ ❓ ❗",
    )
}

private fun roundBg(color: Int, r: Float) = GradientDrawable().apply { cornerRadius = r; setColor(color) }

/** لوحة الإيموجي: شبكة رموز مع تبويبات للفئات وفئة "الأخيرة". */
class EmojiPanel(
    context: Context,
    private val pal: Palette,
    private val prefs: SharedPreferences,
    private val onPick: (String) -> Unit,
) : LinearLayout(context) {
    private val dpx = resources.displayMetrics.density
    private val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val tabs = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val tabViews = ArrayList<TextView>()
    private var current = 1

    init {
        orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(context).apply { addView(grid) }
        addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val tabScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        }
        addView(tabScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (44 * dpx).toInt()))
        val all = listOf("🕘") + EmojiData.CATS.map { it.first }
        all.forEachIndexed { i, icon ->
            val tv = TextView(context).apply {
                text = icon
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding((16 * dpx).toInt(), 0, (16 * dpx).toInt(), 0)
                setOnClickListener { select(i) }
            }
            tabViews.add(tv)
            tabs.addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
        }
        select(1)
    }

    private fun recents(): List<String> =
        (prefs.getString("recent_emoji", "") ?: "").split(" ").filter { it.isNotEmpty() }

    private fun pushRecent(e: String) {
        val list = ArrayList<String>()
        list.add(e)
        list.addAll(recents().filter { it != e })
        prefs.edit().putString("recent_emoji", list.take(30).joinToString(" ")).apply()
    }

    private fun select(i: Int) {
        current = i
        tabViews.forEachIndexed { idx, tv ->
            tv.background = if (idx == i) roundBg(pal.func, 12 * dpx) else null
        }
        val items = if (i == 0) recents() else EmojiData.CATS[i - 1].second.split(" ").filter { it.isNotEmpty() }
        grid.removeAllViews()
        if (items.isEmpty()) {
            grid.addView(TextView(context).apply {
                text = "لا توجد رموز أخيرة بعد"
                setTextColor(pal.hint)
                gravity = Gravity.CENTER
                setPadding(0, (24 * dpx).toInt(), 0, 0)
            })
            return
        }
        val cols = 9
        items.chunked(cols).forEach { chunk ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until cols) {
                val e = chunk.getOrNull(c)
                val tv = TextView(context).apply {
                    text = e ?: ""
                    textSize = 26f
                    gravity = Gravity.CENTER
                    setPadding(0, (6 * dpx).toInt(), 0, (6 * dpx).toInt())
                    if (e != null) setOnClickListener { pushRecent(e); onPick(e) }
                }
                row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            grid.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }
}

interface ToolActions {
    fun cursor(left: Boolean)
    fun menu(id: Int)
    fun pasteConverted()
    fun insertClip(text: String)
    fun clearClips()
}

/** لوحة الأدوات والحافظة: أزرار التحرير وسجل النسخ. */
class ClipPanel(
    context: Context,
    private val pal: Palette,
    private val actions: ToolActions,
) : LinearLayout(context) {
    private val dpx = resources.displayMetrics.density
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        orientation = LinearLayout.VERTICAL
        val tools = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun tool(label: String, f: () -> Unit) {
            val tv = TextView(context).apply {
                text = label
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(pal.text)
                setPadding((16 * dpx).toInt(), 0, (16 * dpx).toInt(), 0)
                background = roundBg(pal.func, 20 * dpx)
                setOnClickListener { f() }
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (40 * dpx).toInt())
            lp.marginEnd = (6 * dpx).toInt()
            tools.addView(tv, lp)
        }
        tool("◀") { actions.cursor(true) }
        tool("▶") { actions.cursor(false) }
        tool("تحديد الكل") { actions.menu(android.R.id.selectAll) }
        tool("نسخ") { actions.menu(android.R.id.copy) }
        tool("قص") { actions.menu(android.R.id.cut) }
        tool("لصق") { actions.menu(android.R.id.paste) }
        tool("لصق وتحويل") { actions.pasteConverted() }
        tool("مسح السجل") { actions.clearClips() }
        val hs = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tools)
            setPadding((8 * dpx).toInt(), (8 * dpx).toInt(), (8 * dpx).toInt(), (8 * dpx).toInt())
        }
        addView(hs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val sv = ScrollView(context).apply { addView(list) }
        addView(sv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun setClips(clips: List<String>) {
        list.removeAllViews()
        if (clips.isEmpty()) {
            list.addView(TextView(context).apply {
                text = "سجل الحافظة فارغ. انسخ نصاً وسيظهر هنا."
                setTextColor(pal.hint)
                gravity = Gravity.CENTER
                setPadding(0, (24 * dpx).toInt(), 0, 0)
            })
            return
        }
        for (c in clips) {
            val tv = TextView(context).apply {
                text = c.replace("\n", " ")
                textSize = 15f
                maxLines = 2
                setTextColor(pal.text)
                textDirection = View.TEXT_DIRECTION_ANY_RTL
                setPadding((14 * dpx).toInt(), (10 * dpx).toInt(), (14 * dpx).toInt(), (10 * dpx).toInt())
                background = roundBg(pal.func, 12 * dpx)
                setOnClickListener { actions.insertClip(c) }
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins((8 * dpx).toInt(), 0, (8 * dpx).toInt(), (6 * dpx).toInt())
            list.addView(tv, lp)
        }
    }
}
