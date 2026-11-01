package io.github.eslamasabry.oc.tools.strings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The hand conversions (plan 06 §13.3 steps 4–5, plan 01 §7.5): the messages the converter refuses are rewritten
 * by a reviewed recipe in `hand-recipes.tsv`, as close to the ICU meaning as CMP allows.
 */
class HandTest {
    private fun arb(locale: String, json: String) = Arb.parse(json, locale)

    private val en = arb(
        "en",
        """
        {
          "before": "Before",
          "kitAgentLabel": "{hasRole, select, yes{{name}, {role}, {state}} other{{name}, {state}}}",
          "@kitAgentLabel": {"placeholders": {"hasRole": {"type": "String"}, "name": {"type": "String"}, "role": {"type": "String"}, "state": {"type": "String"}}},
          "cpu": "{level, select, idle{Idle} busy{Busy} other{Very busy}} · {percent}%",
          "@cpu": {"placeholders": {"level": {"type": "String"}, "percent": {"type": "String"}}},
          "storage": "{total} of unsent work — {queued, plural, =1{1 queued prompt} other{{queued} queued prompts}} ({queueBytes}) and {drafts, plural, =1{1 draft} other{{drafts} drafts}} ({draftBytes}). Discarded after {days} days.",
          "@storage": {"placeholders": {"total": {"type": "String"}, "queued": {"type": "int"}, "queueBytes": {"type": "String"}, "drafts": {"type": "int"}, "draftBytes": {"type": "String"}, "days": {"type": "int"}}},
          "goal": "Spec approved {age} · {milestones, plural, one{1 milestone} other{{milestones} milestones}} · {repos, plural, one{1 repo} other{{repos} repos}}",
          "@goal": {"placeholders": {"age": {"type": "String"}, "milestones": {"type": "int"}, "repos": {"type": "int"}}},
          "board": "{tasks, plural, one{1 task} other{{tasks} tasks}} across {milestones, plural, one{1 milestone} other{{milestones} milestones}}",
          "@board": {"placeholders": {"tasks": {"type": "int"}, "milestones": {"type": "int"}}},
          "after": "After"
        }
        """,
    )
    private val ar = arb(
        "ar",
        """
        {
          "before": "قبل",
          "kitAgentLabel": "{hasRole, select, yes{{name}، {role}، {state}} other{{name}، {state}}}",
          "cpu": "{level, select, idle{خامل} other{مشغول جدًا}} · {percent}%",
          "storage": "{total} — {queued, plural, =0{لا طلبات} =1{طلب واحد} few{{queued} طلبات} other{{queued} طلب}} ({queueBytes}) و{drafts, plural, =1{مسودة واحدة} other{{drafts} مسودة}} ({draftBytes}). بعد {days} يومًا.",
          "goal": "اعتُمدت المواصفات منذ {age} · عدد المراحل الرئيسية: {milestones} · عدد المستودعات: {repos}",
          "after": "بعد"
        }
        """,
    )
    private val zh = arb(
        "zh",
        """
        {
          "board": "{milestones, plural, one{1 个里程碑} other{{milestones} 个里程碑}}中共 {tasks, plural, one{1 个任务} other{{tasks} 个任务}}"
        }
        """,
    )
    private val recipes = HandRecipes.parse(
        """
        # key	recipe	note
        kitAgentLabel	select	one string per branch
        cpu	select	level words
        storage	split-plurals	two counts in one sentence
        goal	split-plurals	ar writes the counts as numbers
        board	split-plurals	zh names milestones first
        """.trimIndent().lines(),
    )
    private val expansion = Hand.expand(listOf(en, ar, zh), recipes)
    private val conv = Converter({ "chat" }).convert(expansion.arbs)

    private fun str(locale: String, name: String) =
        conv[locale].files.values.flatten().filterIsInstance<Resource.Str>().single { it.name == name }.text

    private fun plurals(locale: String, name: String) =
        conv[locale].files.values.flatten().filterIsInstance<Resource.Plurals>().single { it.name == name }.items

    private fun names(locale: String) = conv[locale].files.values.flatten().map { it.name }

    @Test
    fun `B-L10N-010 a select becomes one string per branch and the select argument is not a format argument`() {
        assertEquals("%1\$s, %2\$s, %3\$s", str("en", "kitAgentLabel__yes"))
        assertEquals("%1\$s, %3\$s", str("en", "kitAgentLabel__other"))
        assertEquals("%1\$s، %2\$s، %3\$s", str("ar", "kitAgentLabel__yes"))
        assertEquals(listOf("name", "role", "state"), conv.plans.getValue("kitAgentLabel__yes").placeholders.map { it.name })
        assertTrue(conv.problems.isEmpty(), conv.problems.toString())
    }

    @Test
    fun `B-L10N-010 select text around the select is copied into every branch`() {
        assertEquals("Idle · %1\$s%", str("en", "cpu__idle"))
        assertEquals("Very busy · %1\$s%", str("en", "cpu__other"))
    }

    @Test
    fun `B-L10N-010 a language without a branch reads its own other branch, as ICU does`() {
        assertEquals("مشغول جدًا · %1\$s%", str("ar", "cpu__busy"))
        assertEquals("خامل · %1\$s%", str("ar", "cpu__idle"))
    }

    @Test
    fun `B-L10N-010 several plurals split into a frame string and one plurals per count`() {
        assertEquals(
            "%1\$s of unsent work — %2\$s (%3\$s) and %4\$s (%5\$s). Discarded after %6\$d days.",
            str("en", "storage"),
        )
        assertEquals(mapOf("one" to "1 queued prompt", "other" to "%1\$d queued prompts"), plurals("en", "storage__queued"))
        assertEquals(mapOf("one" to "1 draft", "other" to "%1\$d drafts"), plurals("en", "storage__drafts"))
        assertEquals("1 queued prompt", str("en", "storage__queued__eq1"))
        // The frame's placeholders name the pieces, then the plain arguments, in English order of appearance.
        assertEquals(
            listOf("total", "queued__plural", "queueBytes", "drafts__plural", "draftBytes", "days"),
            conv.plans.getValue("storage").placeholders.map { it.name },
        )
    }

    @Test
    fun `B-L10N-010 Arabic pieces keep all six categories and their exact selectors`() {
        val q = plurals("ar", "storage__queued")
        assertEquals(listOf("zero", "one", "two", "few", "many", "other"), q.keys.toList())
        assertEquals("لا طلبات", q["zero"])
        assertEquals("طلب واحد", q["one"])
        assertEquals("%1\$d طلبات", q["few"])
        assertEquals("لا طلبات", str("ar", "storage__queued__eq0"))
        // =0 is used by Arabic only; English still gets the exact string, with what ICU shows for 0 in English.
        assertEquals("%1\$d queued prompts", str("en", "storage__queued__eq0"))
    }

    @Test
    fun `B-L10N-010 a language that writes a count as a number keeps it in its frame`() {
        assertEquals("اعتُمدت المواصفات منذ %1\$s · عدد المراحل الرئيسية: %4\$d · عدد المستودعات: %5\$d", str("ar", "goal"))
        assertEquals("Spec approved %1\$s · %2\$s · %3\$s", str("en", "goal"))
        assertEquals(
            listOf("age", "milestones__plural", "repos__plural", "milestones", "repos"),
            conv.plans.getValue("goal").placeholders.map { it.name },
        )
        assertFalse("goal__milestones" in names("ar"), "Arabic has no piece: its frame does not use one")
        assertEquals(setOf("goal__milestones", "goal__repos"), expansion.unreferencedPieces.getValue("ar"))
    }

    @Test
    fun `B-L10N-010 a translation that orders the counts differently keeps the right values`() {
        assertEquals("%1\$s across %2\$s", str("en", "board"))
        assertEquals("%2\$s中共 %1\$s", str("zh", "board"))
    }

    @Test
    fun `synthetic keys take the base key's place in key order`() {
        assertEquals(
            listOf(
                "before", "kitAgentLabel__yes", "kitAgentLabel__other", "cpu__idle", "cpu__busy", "cpu__other",
                "storage", "storage__queued", "storage__drafts", "goal", "goal__milestones", "goal__repos",
                "board", "board__tasks", "board__milestones", "after",
            ),
            expansion.arbs.first().messages.keys.toList(),
        )
        assertEquals("storage", expansion.baseOf.getValue("storage__queued"))
        assertEquals("kitAgentLabel", expansion.baseOf.getValue("kitAgentLabel__yes"))
    }

    @Test
    fun `every hand conversion is listed with its languages`() {
        val listed = expansion.converted.associate { it.key to it.locales }
        assertEquals(listOf("en", "ar"), listed["kitAgentLabel"])
        assertEquals(listOf("en", "zh"), listed["board"])
        assertEquals(listOf("en", "ar"), listed["goal"])
        assertEquals(10, expansion.converted.sumOf { it.locales.size })
    }

    @Test
    fun `a select with no recipe still fails loudly`() {
        val e = Hand.expand(listOf(en, ar), recipes.filter { it.key != "cpu" })
        val c = Converter({ "chat" }).convert(e.arbs)
        assertEquals(setOf("cpu"), c.problems.map { it.key }.toSet())
        assertEquals(ProblemKind.SELECT, c.problems.first().kind)
    }

    @Test
    fun `a recipe that does not fit its message is refused`() {
        val wrong = HandRecipes.parse(listOf("storage\tselect\twrong kind"))
        assertFailsWith<HandRecipeException> { Hand.expand(listOf(en), wrong) }
        val unknown = HandRecipes.parse(listOf("nope\tselect\tno such key"))
        assertFailsWith<HandRecipeException> { Hand.expand(listOf(en), unknown) }
        assertFailsWith<IllegalArgumentException> { HandRecipes.parse(listOf("x\tsplit\tbad recipe name")) }
    }

    @Test
    fun `the generated Kotlin picks a select branch with a when and composes split plurals`() {
        val kt = HandWordsKotlin.render(conv, expansion, shipped = { true })
        assertTrue("public fun kitAgentLabel(hasRole: String): StringResource = when (hasRole) {" in kt, kt)
        assertTrue("\"yes\" -> Res.string.kitAgentLabel__yes" in kt)
        assertTrue("else -> Res.string.kitAgentLabel__other" in kt)
        assertTrue("public suspend fun storage(total: String, queued: Int, queueBytes: String, drafts: Int, draftBytes: String, days: Int): String" in kt, kt)
        assertTrue("1 -> getString(Res.string.storage__queued__eq1, queued)" in kt, kt)
        assertTrue("0 -> getString(Res.string.storage__queued__eq0, queued)" in kt, kt)
        assertTrue("else -> getPluralString(Res.plurals.storage__queued, queued, queued)" in kt, kt)
        assertTrue("@Composable\n    public fun storageText(" in kt, kt)
        assertTrue("import io.github.eslamasabry.oc.ui.words.res.storage__queued__eq1" in kt)
        // goal: the frame takes the pieces and the raw counts (Arabic uses the counts).
        assertTrue("getString(Res.string.goal, age, milestonesText, reposText, milestones, repos)" in kt, kt)
    }
}
