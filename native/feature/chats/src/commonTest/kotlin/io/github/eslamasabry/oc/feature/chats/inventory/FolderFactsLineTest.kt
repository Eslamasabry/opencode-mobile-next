package io.github.eslamasabry.oc.feature.chats.inventory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FolderFactsLineTest {
    @Test
    fun `B-L10N-003 an empty Git folder`() {
        assertEquals("Empty folder · Git", folderFactsLine(FolderFacts(entries = 0, git = true)))
    }

    @Test
    fun `B-L10N-003 files, Git and changes`() {
        assertEquals("24 items · Git · 3 changes", folderFactsLine(FolderFacts(entries = 24, git = true, changes = 3)))
        assertEquals("1 item · Git · 1 change", folderFactsLine(FolderFacts(entries = 1, git = true, changes = 1)))
    }

    @Test
    fun `B-L10N-003 a clean tree and a folder without Git say less`() {
        assertEquals("5 items · Git", folderFactsLine(FolderFacts(entries = 5, git = true, changes = 0)))
        assertEquals("5 items", folderFactsLine(FolderFacts(entries = 5, git = false)))
        // Changes without Git are not claimed.
        assertEquals("5 items", folderFactsLine(FolderFacts(entries = 5, git = false, changes = 4)))
    }

    @Test
    fun `B-L10N-003 nothing known is nothing said and pending says it is looking`() {
        assertNull(folderFactsLine(FolderFacts()))
        assertEquals("Looking at the folder…", folderFactsLine(FolderFacts.Pending))
        assertEquals("جارٍ فحص المجلد…", folderFactsLine(FolderFacts.Pending, "ar"))
    }

    @Test
    fun `B-L10N-003 Arabic counts use Arabic plurals`() {
        assertEquals("3 عناصر · Git · تغييران", folderFactsLine(FolderFacts(entries = 3, git = true, changes = 2), "ar"))
        assertEquals("مجلد فارغ · Git", folderFactsLine(FolderFacts(entries = 0, git = true), "ar-EG"))
        // Every Arabic form of the ARB: =1, =2, few, many, other.
        assertEquals("عنصر واحد", folderFactsLine(FolderFacts(entries = 1), "ar"))
        assertEquals("عنصران", folderFactsLine(FolderFacts(entries = 2), "ar"))
        assertEquals("10 عناصر", folderFactsLine(FolderFacts(entries = 10), "ar"))
        assertEquals("11 عنصرًا", folderFactsLine(FolderFacts(entries = 11), "ar"))
        assertEquals("99 عنصرًا", folderFactsLine(FolderFacts(entries = 99), "ar"))
        assertEquals("100 عنصر", folderFactsLine(FolderFacts(entries = 100), "ar"))
        assertEquals("103 عناصر", folderFactsLine(FolderFacts(entries = 103), "ar"))
        assertEquals("Git · تغيير واحد", folderFactsLine(FolderFacts(git = true, changes = 1), "ar"))
        assertEquals("Git · 5 تغييرات", folderFactsLine(FolderFacts(git = true, changes = 5), "ar"))
        assertEquals("Git · 20 تغييرًا", folderFactsLine(FolderFacts(git = true, changes = 20), "ar"))
        assertEquals("Git · 200 تغيير", folderFactsLine(FolderFacts(git = true, changes = 200), "ar"))
    }
}
