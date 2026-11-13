package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.model.TerminalShellOption
import io.github.eslamasabry.oc.core.model.TerminalShellSettings
import io.github.eslamasabry.oc.core.model.realShells
import io.github.eslamasabry.oc.core.model.selects
import io.github.eslamasabry.oc.feature.settings.presentation.selectedValue
import io.github.eslamasabry.oc.feature.settings.presentation.shellChoices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** Sweep C P45: on a merged-/usr Linux the same shell is listed twice (/bin and /usr/bin); one row each. */
class ShellChoicesTest {
    private val merged =
        TerminalShellSettings(
            "",
            persistentListOf(
                TerminalShellOption("/bin/sh", "sh", true),
                TerminalShellOption("/usr/bin/sh", "sh", true),
                TerminalShellOption("/bin/bash", "bash", true),
                TerminalShellOption("/usr/bin/bash", "bash", true),
                TerminalShellOption("/bin/rbash", "rbash", false),
                TerminalShellOption("/usr/bin/rbash", "rbash", false),
                TerminalShellOption("/usr/bin/dash", "dash", true),
            ),
        )

    @Test
    fun `a shell listed under both bin folders is one row named by its name`() {
        val rows = shellChoices(merged)
        assertEquals(listOf(null, "sh", "bash", "rbash", "dash"), rows.map { it.label })
        assertEquals(listOf("", "/bin/sh", "/bin/bash", "/bin/rbash", "/usr/bin/dash"), rows.map { it.value })
        assertEquals(listOf(false, false, false, true, false), rows.map { it.terminalOnly })
        assertEquals(4, merged.realShells().size)
    }

    @Test
    fun `two different shells with one name keep their paths`() {
        val two =
            TerminalShellSettings(
                "",
                persistentListOf(
                    TerminalShellOption("/usr/bin/zsh", "zsh", true),
                    TerminalShellOption("/opt/zsh/bin/zsh", "zsh", true),
                ),
            )
        assertEquals(listOf(null, "/usr/bin/zsh", "/opt/zsh/bin/zsh"), shellChoices(two).map { it.label })
    }

    @Test
    fun `a saved name selects its shell and an unknown save is kept`() {
        assertEquals("/bin/bash", merged.copy(selected = "bash").selectedValue())
        val custom = merged.copy(selected = "/opt/fish")
        assertEquals("/opt/fish", custom.selectedValue())
        assertEquals("/opt/fish", shellChoices(custom).last().value)
    }

    @Test
    fun `a save under the other bin folder selects the one listed row and adds no second row`() {
        val saved = merged.copy(selected = "/usr/bin/bash")
        val rows = shellChoices(saved)
        assertEquals(listOf("", "/bin/sh", "/bin/bash", "/bin/rbash", "/usr/bin/dash"), rows.map { it.value })
        assertEquals("/bin/bash", saved.selectedValue())
        // The Git panel's check: exactly the one listed bash is selected, no other shell.
        assertEquals(listOf("bash"), saved.realShells().filter { saved.selects(it) }.map { it.name })
    }

    @Test
    fun `nothing saved selects no shell`() {
        assertEquals(emptyList(), merged.realShells().filter { merged.selects(it) })
    }
}
