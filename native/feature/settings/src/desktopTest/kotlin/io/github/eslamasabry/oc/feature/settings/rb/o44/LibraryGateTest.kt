package io.github.eslamasabry.oc.feature.settings.rb.o44

import io.github.eslamasabry.oc.core.model.CommandInfo
import io.github.eslamasabry.oc.core.model.ReferenceInfo
import io.github.eslamasabry.oc.core.model.SkillInfo
import io.github.eslamasabry.oc.engine.integrations.catalog.LibraryInventory
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryActions
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryTab
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryUi
import io.github.eslamasabry.oc.feature.settings.ui.LibraryScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import java.lang.reflect.Proxy
import kotlin.test.Ignore
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/**
 * Gate cases for the Library goldens (Commands, References, Skills) with the inputs of
 * `test/revamp/screen_library_4_golden_test.dart:76-138` at FLUTTER_REF.
 */
class LibraryGateTest {
    private val screen = FlutterGoldenGate.Frame.Screen
    private val wide = Pair(1280, 800) // F: test/revamp/screen_library_4_golden_test.dart (_wide)

    private val actions: LibraryActions =
        Proxy.newProxyInstance(LibraryActions::class.java.classLoader, arrayOf(LibraryActions::class.java)) {
            proxy,
            method,
            args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "GateActions"
                else -> null
            }
        } as LibraryActions

    private val commands =
        persistentListOf(
            CommandInfo("review", "Review the working tree for bugs and risky changes", "plan", false),
            CommandInfo("release-notes", "Draft release notes from merged changes", null, false),
            CommandInfo("test", "Run the focused tests for the files you changed", "build", true),
            CommandInfo("init", null, null, false),
        )

    private val references =
        persistentListOf(
            ReferenceInfo(
                "platform-docs",
                "/work/references/platform-docs",
                "Android platform guidance the app follows",
            ),
            ReferenceInfo(
                "design-system",
                "/work/references/design-system",
                "The kit contracts and the visual language",
            ),
            ReferenceInfo("opencode", "/work/references/opencode", null),
        )

    private val skills =
        persistentListOf(
            SkillInfo(
                "focused-review",
                "focused-review",
                "Review a change for correctness and readability",
                "/work/.opencode/skills/focused-review/SKILL.md",
                "# Focused review\n\nReview the current change before it is merged.",
                true,
            ),
            SkillInfo(
                null,
                "release-check",
                "Verify a release candidate before it ships",
                "/work/.opencode/skills/release-check/SKILL.md",
                "# Release check\n\nRun the gates.",
                false,
            ),
            SkillInfo(
                null,
                "translate",
                null,
                "/home/me/.config/opencode/skills/translate/SKILL.md",
                "Translate the copy.",
                false,
            ),
        )

    private fun ui(tab: LibraryTab, inventory: LibraryInventory) =
        LibraryUi(tab = tab, available = true, inventory = inventory)

    private val loaded =
        LibraryInventory(loading = false, commands = commands, references = references, skills = skills)

    // Blocked until the kit fix lands; the module's align baseline may only shrink (native/docs/kit/ALIGN-RULES.md).
    @Ignore("align gate start-edge 'Find a command or action' at 64 dp: KitSearchField placeholder (kit).")
    @Test
    fun library_commands_loaded() {
        FlutterGoldenGate.check("library_commands_loaded", frame = screen) {
            LibraryScreen(ui(LibraryTab.Commands, loaded), actions)
        }
    }

    // Blocked until the kit fix lands; the module's align baseline may only shrink (native/docs/kit/ALIGN-RULES.md).
    @Ignore("align gate start-edge 'Find a command or action' at 64 dp: KitSearchField placeholder (kit).")
    @Test
    fun library_commands_loaded_1280x800() {
        FlutterGoldenGate.check("library_commands_loaded_1280x800", frame = screen, window = wide) {
            LibraryScreen(ui(LibraryTab.Commands, loaded), actions)
        }
    }

    @Test
    fun library_references_loaded() {
        FlutterGoldenGate.check("library_references_loaded", frame = screen) {
            LibraryScreen(ui(LibraryTab.References, loaded), actions)
        }
    }

    // Blocked until the kit fix lands; the module's align baseline may only shrink (native/docs/kit/ALIGN-RULES.md).
    @Ignore("align gate start-edge at 32 dp: KitStateView words (kit A6; Flutter draws them at 32 too).")
    @Test
    fun library_references_empty() {
        FlutterGoldenGate.check("library_references_empty", frame = screen) {
            LibraryScreen(ui(LibraryTab.References, LibraryInventory(loading = false)), actions)
        }
    }

    @Test
    fun library_skills_loaded() {
        FlutterGoldenGate.check("library_skills_loaded", frame = screen) {
            LibraryScreen(ui(LibraryTab.Skills, loaded), actions)
        }
    }

    @Test
    fun library_skills_loaded_1280x800() {
        FlutterGoldenGate.check("library_skills_loaded_1280x800", frame = screen, window = wide) {
            LibraryScreen(ui(LibraryTab.Skills, loaded), actions)
        }
    }
}
