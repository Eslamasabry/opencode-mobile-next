package io.github.eslamasabry.oc.ui.kit.rb.o26

import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * CapRole against Dart: each KitMaxWidth constructor's width and alignment (lib/ui/kit/kit_max_width.dart:39-109,
 * KitLayout values in lib/ui/kit/kit_layout.dart) and the roles of test/kit/kit_capacity.json (dialog, toast, fill,
 * wrapper, scope).
 */
class CapRoleParityTest {
    @Test fun `every role has Flutter's cap and alignment`() {
        val expected =
            mapOf(
                CapRole.Conversation to (700 to CapAlign.Center), // paneDetailMaxWidth, cap() default centre
                CapRole.Reading to (720 to CapAlign.Center), // readingWidth, AlignmentDirectional.center
                CapRole.List to (960 to CapAlign.Center), // listWidth
                CapRole.Sheet to (640 to CapAlign.Center), // sheetMaxWidth
                CapRole.Confirm to (560 to CapAlign.Center), // confirmMediumWidth
                CapRole.Dialog to (480 to CapAlign.Center), // confirmDialogWidth (capacity json)
                CapRole.Popover to (320 to CapAlign.Start), // popoverMaxWidth, centerStart
                CapRole.Toast to (480 to CapAlign.Start), // undoMaxWidth (capacity json)
                CapRole.State to (440 to CapAlign.Center), // stateMaxWidth
                CapRole.Lane to (400 to CapAlign.Start), // laneMaxWidth, centerStart
                CapRole.Control to (400 to CapAlign.Center), // controlMaxWidth
                CapRole.Chip to (320 to CapAlign.Start), // chipMaxWidth, centerStart
                CapRole.Glyph to (96 to CapAlign.Center), // glyphMaxWidth
            )
        for ((role, want) in expected) {
            assertEquals(want.first, role.capDp, "cap of $role")
            assertEquals(want.second, role.defaultAlign, "alignment of $role")
        }
    }

    @Test fun `fill wrapper and scope carry no cap`() {
        for (role in listOf(CapRole.Fill, CapRole.Wrapper, CapRole.Scope)) assertEquals(null, role.capDp, "$role")
    }

    @Test fun `the roles are the sixteen of kit_capacity json`() {
        assertEquals(
            setOf("chip", "confirm", "control", "conversation", "dialog", "fill", "glyph", "lane", "list", "popover",
                "reading", "scope", "sheet", "state", "toast", "wrapper"),
            CapRole.entries.map { it.name.lowercase() }.toSet(),
        )
    }
}
