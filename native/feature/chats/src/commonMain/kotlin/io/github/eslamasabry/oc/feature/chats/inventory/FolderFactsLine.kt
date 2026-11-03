package io.github.eslamasabry.oc.feature.chats.inventory

// Port of `chatStartFactsLine` (lib/ui/screens/chat/empty_chat.dart) with the English and Arabic copy of
// lib/l10n/app_en.arb / app_ar.arb: chatStartLooking, chatStartEmptyFolder, chatStartItemCount, chatStartGit,
// chatStartChangeCount. The words move to :ui:words when its owner adds these keys; the rule stays here.

/**
 * What is known about a new conversation's folder. [entries]: top-level entries other than `.git` (null = unknown);
 * [git]: a repository, not one, or unknown; [changes]: changed files; [settled] false while the folder is looked at.
 */
public data class FolderFacts(
    val entries: Int? = null,
    val git: Boolean? = null,
    val changes: Int? = null,
    val settled: Boolean = true,
) {
    public companion object {
        public val Pending: FolderFacts = FolderFacts(settled = false)
    }
}

/**
 * The short line under a new conversation's header: what is known (items, Git, changes), less for a clean tree or a
 * folder without Git, nothing when nothing is known, and "looking" while pending. [locale] is a BCP-47 tag; Arabic
 * (`ar…`) uses Arabic plurals, anything else English.
 */
public fun folderFactsLine(facts: FolderFacts, locale: String = "en"): String? {
    val words = if (locale.lowercase().startsWith("ar")) ArabicFacts else EnglishFacts
    if (!facts.settled) return words.looking
    val entries = facts.entries
    val changes = facts.changes
    val parts = buildList {
        if (entries == 0) add(words.emptyFolder) else if (entries != null) add(words.items(entries))
        if (facts.git == true) add(words.git)
        if (facts.git == true && changes != null && changes > 0) add(words.changes(changes))
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

private interface FactWords {
    val looking: String
    val emptyFolder: String
    val git: String

    fun items(count: Int): String

    fun changes(count: Int): String
}

private object EnglishFacts : FactWords {
    override val looking = "Looking at the folder…"
    override val emptyFolder = "Empty folder"
    override val git = "Git"

    override fun items(count: Int) = if (count == 1) "1 item" else "$count items"

    override fun changes(count: Int) = if (count == 1) "1 change" else "$count changes"
}

private object ArabicFacts : FactWords {
    override val looking = "جارٍ فحص المجلد…"
    override val emptyFolder = "مجلد فارغ"
    override val git = "Git"

    override fun items(count: Int) =
        arabicPlural(count, one = "عنصر واحد", two = "عنصران", few = "$count عناصر", many = "$count عنصرًا", other = "$count عنصر")

    override fun changes(count: Int) =
        arabicPlural(count, one = "تغيير واحد", two = "تغييران", few = "$count تغييرات", many = "$count تغييرًا", other = "$count تغيير")
}

/**
 * Intl `pluralLogic` for `ar` with the ARB's `=1`, `=2`, `few`, `many`, `other` (no `zero` form, so zero falls back to
 * other): exact 1 and 2 first, then CLDR few = n % 100 in 3..10, many = n % 100 in 11..99.
 */
internal fun arabicPlural(count: Int, one: String, two: String, few: String, many: String, other: String): String =
    when {
        count == 1 -> one
        count == 2 -> two
        count % 100 in 3..10 -> few
        count % 100 in 11..99 -> many
        else -> other
    }
