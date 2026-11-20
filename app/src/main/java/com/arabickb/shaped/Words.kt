package com.arabickb.shaped

import android.content.SharedPreferences

object Words {
    /** كلمات عربية شائعة (الترتيب تقريباً حسب الشيوع). */
    val AR: List<String> = (
        "في من على إلى عن مع هذا هذه ذلك تلك هناك هنا الذي التي الله أنا أنت هو هي نحن هم كل بعض أي أيضا جدا الآن اليوم غدا أمس " +
            "بعد قبل عند بين حتى لكن لأن أو ثم لا نعم ما ماذا لماذا كيف أين متى هل كان كانت يكون أكون ليس يوجد " +
            "مرحبا أهلا أهلاً سلام السلام عليكم ورحمة وبركاته وعليكم شكرا شكراً عفوا عفواً تفضل تفضلي من فضلك صباح مساء الخير النور " +
            "الحمد لله إن شاء ما شاء بارك فيك جزاك خيرا مبروك تهانينا آسف أسف سامحني معذرة " +
            "كيف حالك أخبارك بخير تمام حسنا طيب ممتاز رائع جميل حلو جيد سيء صعب سهل سريع بطيء كبير صغير " +
            "أحب أحبك أريد تريد أبي أبغى ابغى أقدر تقدر ممكن ساعدني ساعد تعال تعالي اذهب روح انتظر انتظرني بسرعة هيا يلا " +
            "لاعب لاعبين فريق لعبة ألعاب سلاح شخصية مستوى نقاط هجوم دفاع قتال معركة عدو صديق صديقي أصدقاء حليف " +
            "خادم سيرفر حساب اسم كلمة رسالة محادثة دردشة صوت مايك سماعة اتصال انترنت شبكة بطيء تأخير " +
            "بيت منزل مدرسة جامعة عمل شغل سيارة طريق مدينة بلد دولة عالم وقت ساعة دقيقة يوم أسبوع شهر سنة صباح ظهر عصر مغرب ليل " +
            "أكل شرب ماء قهوة شاي طعام نوم تعبان جوعان عطشان سعيد حزين غاضب مشغول فاضي موجود غائب " +
            "واحد اثنان ثلاثة أربعة خمسة ستة سبعة ثمانية تسعة عشرة مئة ألف " +
            "أول ثاني ثالث آخر جديد قديم كثير قليل كلهم دائما أحيانا أبدا مرة مرتين " +
            "اسمع شوف انظر قل قول اكتب اقرأ افتح اغلق ابدأ توقف استمر عد ارجع خذ أعطني هات جرب حاول فكر اعرف أعرف لا أعرف " +
            "صح خطأ غلط فعلا حقا طبعا بالطبع أكيد ربما يمكن مستحيل تأكد اتفقنا موافق لا بأس " +
            "الأخ أخي أختي أمي أبي الأب الأم الأخ الأخت ابن بنت ولد رجل امرأة طفل " +
            "شاهد يوتيوب بث مباشر قناة فيديو صورة ملف تطبيق لعب يلعب نلعب العب"
        ).split(" ").filter { it.isNotEmpty() }

    val EN: List<String> = (
        "the be to of and a in that have it for not on with he as you do at this but his by from they we say her she or an will my one all would there their " +
            "what so up out if about who get which go me when make can like time no just him know take people into year your good some could them see other than then now look only come its over think also back after use two how our work first well way even new want because any these give day most us " +
            "hello hi hey thanks thank please sorry yes okay ok sure game play player team win lose help wait come go let lets now nice cool good great awesome bad love friend friends"
        ).split(" ").filter { it.isNotEmpty() }

    /** أخطاء إملائية شائعة وتصحيحها. */
    val FIX: Map<String, String> = mapOf(
        "انا" to "أنا", "الى" to "إلى", "اذا" to "إذا", "هاذا" to "هذا", "هاذه" to "هذه", "هاذي" to "هذي",
        "لاكن" to "لكن", "ذالك" to "ذلك", "ذالكَ" to "ذلك", "اللة" to "الله", "الله" to "الله",
        "لانه" to "لأنه", "لانك" to "لأنك", "لاني" to "لأني", "لانها" to "لأنها", "لانهم" to "لأنهم",
        "اكثر" to "أكثر", "اكيد" to "أكيد", "ايضا" to "أيضا", "الان" to "الآن", "امس" to "أمس",
        "احبك" to "أحبك", "احب" to "أحب", "اريد" to "أريد", "انشالله" to "إن شاء الله", "ان شاء الله" to "إن شاء الله",
        "مشاءالله" to "ما شاء الله", "ماشاءالله" to "ما شاء الله", "السلام عليكم" to "السلام عليكم",
        "اهلا" to "أهلا", "اسف" to "آسف", "اخي" to "أخي", "اختي" to "أختي", "امي" to "أمي", "ابي" to "أبي",
        "اولا" to "أولا", "اخر" to "آخر", "ادري" to "أدري", "اعرف" to "أعرف",
    )
}

/** اقتراح الكلمات: كلمات مضمّنة + ما يتعلمه الكيبورد من كتابتك (يُحفظ في الجهاز فقط). */
class Suggester(private val prefs: SharedPreferences) {
    private val learned = LinkedHashMap<String, Int>()

    init {
        val raw = prefs.getString("learned", "") ?: ""
        for (line in raw.split("\n")) {
            val parts = line.split("\t")
            if (parts.size == 2 && parts[0].isNotEmpty()) {
                val n = parts[1].toIntOrNull() ?: 1
                learned[parts[0]] = n
            }
        }
    }

    /** توحيد الأشكال للمطابقة: أ إ آ ← ا، ى ← ي، وحذف التشكيل، وأحرف صغيرة. */
    fun norm(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            if (ch in '\u064B'..'\u0652' || ch == '\u0640') continue
            sb.append(
                when (ch) {
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ى' -> 'ي'
                    else -> ch.lowercaseChar()
                },
            )
        }
        return sb.toString()
    }

    fun suggest(prefix: String, n: Int): List<String> {
        val p = norm(prefix)
        if (p.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        fun add(w: String) {
            val k = norm(w)
            if (k != p && k.startsWith(p) && seen.add(k) && out.size < n) out.add(w)
        }
        learned.entries.sortedByDescending { it.value }.forEach { add(it.key) }
        val arabic = prefix.isNotEmpty() && prefix[0].code in 0x0600..0x06FF
        (if (arabic) Words.AR else Words.EN).forEach { add(it) }
        return out
    }

    fun fix(word: String): String? {
        val f = Words.FIX[word]
        return if (f != null && f != word) f else null
    }

    fun learn(word: String) {
        val w = word.trim()
        if (w.length < 2 || w.length > 24) return
        learned[w] = (learned[w] ?: 0) + 1
        if (learned.size > 400) {
            val keep = learned.entries.sortedByDescending { it.value }.take(300)
            learned.clear()
            for (e in keep) learned[e.key] = e.value
        }
        save()
    }

    fun clear() {
        learned.clear()
        save()
    }

    private fun save() {
        prefs.edit().putString("learned", learned.entries.joinToString("\n") { it.key + "\t" + it.value }).apply()
    }
}
