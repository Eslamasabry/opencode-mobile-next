package io.github.eslamasabry.oc.ui.kitchat.rb.o14

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.chip.KitComposerModelChip
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.rb.o06.FlutterCapture
import kotlin.test.Test

/**
 * test/goldens/kit/kit_composer_chips_golden_test.dart: the three forms of KitComposerChips (model, attachments,
 * suggestions) on `_scene` (gutter, a `surface2` panel, space3 inside, space2 between), 412x915, and the default
 * (attachments + model) at 412x915 and 1280x800, at 1.0 and 2.0 text; light and dark. Frame: kitGalleryPart (Part).
 *
 * Mismatch: native has no KitComposerChips. The model form is [KitComposerModelChip] (no `state`: the words of
 * serverDefault / signInNeeded / chooseNeeded are passed as its label, Flutter's `_words`; no long-press menu).
 * Mismatch: attachments are KitChips in a KitChipWrap (no native attachment chip): no image thumbnail (the image
 * glyph stands in for the 1x1 PNG), no mono middle-cut label, no detail line ("Recovered", "2.1 MB").
 * Mismatch: suggestions are a KitRowGroup of KitRows plus a tertiary "Show all" (no native suggestion panel): no mono
 * labels, no word-cut description, KitRowGroup's surface instead of `_PanelSurface`; Flutter shows at most 5 rows
 * (`visibleCount`), reproduced by taking 5.
 */
class KitComposerChipsGateTest {
    private val wrap: @Composable (@Composable () -> Unit) -> Unit =
        { c -> CompositionLocalProvider(LocalKitRedactor provides Redactor()) { FlutterCapture(c) } }

    private class Attachment(val label: String, val icon: ImageVector, val detail: String?, val opens: Boolean)

    /** Flutter `_items()` (kit_composer_chips_golden_test.dart:51-78). */
    private val items = listOf(
        Attachment("screenshot-2026-09-27.png", KitIcons.image, "Recovered", opens = true),
        Attachment("build-log.txt", KitIcons.file, "2.1 MB", opens = true),
        Attachment("lib/ui/screens/chat/composer.dart:1054–1288", KitIcons.bookmark, null, opens = true),
        Attachment("@src/app", KitIcons.folders, null, opens = false),
    )

    /** Flutter `_scene` (kit_composer_chips_golden_test.dart:133-155): shrink-wrapped, centred by the gallery. */
    @Composable
    private fun Scene(children: @Composable () -> Unit) {
        val tokens = LocalKitTokens.current
        Box(Modifier.padding(horizontal = tokens.gutter.dp)) { // F: test/goldens/kit/kit_composer_chips_golden_test.dart:137
            Box(Modifier.background(LocalKitColors.current.surface2, KitShapeId.Panel.shape())) {
                Column(
                    Modifier.padding(Space.S3.dp), // F: test/goldens/kit/kit_composer_chips_golden_test.dart:144
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp), // F: test/goldens/kit/kit_composer_chips_golden_test.dart:148
                ) { children() }
            }
        }
    }

    @Composable
    private fun Model(label: String = "explore · Sonnet 4.5 · High") {
        KitComposerModelChip(label, {})
    }

    @Composable
    private fun Attachments(readOnly: Boolean = false) {
        KitChipWrap {
            items.forEach { a ->
                when {
                    !readOnly -> KitChip(
                        a.label, kind = KitChipKind.Removable, icon = a.icon, removeLabel = "Remove ${a.label}",
                        onClick = if (a.opens) ({}) else null, onRemove = {},
                    )
                    a.opens -> KitChip(a.label, kind = KitChipKind.Action, icon = a.icon, onClick = {})
                    else -> KitChip(a.label, icon = a.icon)
                }
            }
        }
    }

    /** Flutter `_suggestions()` (kit_composer_chips_golden_test.dart:85-129). */
    @Composable
    private fun Suggestions() {
        val rows = listOf(
            "/compact" to "Summarise the conversation so far into a shorter history that keeps the decisions, the open " +
                "questions and every changed file, so the model has room to keep working",
            "/review" to "Review the changes on this branch",
            "/init" to "Write an AGENTS.md for this project",
            "/undo" to "Undo the last message and its changes",
            "/share" to "Share this conversation",
            "/models" to null,
            "/help" to null,
        ).take(5) // F: lib/ui/kit/chat/kit_composer_chips.dart:209 (visibleCount)
        Column {
            KitRowGroup {
                rows.forEach { (label, description) -> KitRow(label, supporting = description, onClick = {}) }
            }
            Box(Modifier.padding(horizontal = Space.S2.dp)) { // F: lib/ui/kit/chat/kit_composer_chips.dart:1172-1174
                KitButton("Show all", {}, role = KitButtonRole.Tertiary, expand = false)
            }
        }
    }

    @Composable private fun Default() { Scene { Attachments(); Model() } }

    private fun shot(name: String, content: @Composable () -> Unit) = FlutterGoldenGate.check(name, wrap = wrap, content = content)

    @Test fun model() {
        shot("kit_composer_chips_model") { Scene { Model() } }
        shot("kit_composer_chips_model_default") { Scene { Model("Sonnet 4.5"); Model("Server default") } }
        shot("kit_composer_chips_model_sign_in") { Scene { Model("Sign in to a model"); Model("Choose a model") } }
        shot("kit_composer_chips_model_narrow") {
            Scene {
                Box(Modifier.width(120.dp), contentAlignment = Alignment.CenterStart) { Model() } // F: test/goldens/kit/kit_composer_chips_golden_test.dart:170
            }
        }
    }

    @Test fun attachments_and_suggestions() {
        shot("kit_composer_chips_attachments") { Scene { Attachments() } }
        shot("kit_composer_chips_attachments_read_only") { Scene { Attachments(readOnly = true) } }
        shot("kit_composer_chips_suggestions") { Scene { Suggestions() } }
        // Empty attachments and empty suggestions render nothing; the Column's spacing still parts the two.
        shot("kit_composer_chips_empty") { Scene { Spacer(Modifier.width(0.dp)); Spacer(Modifier.width(0.dp)) } }
    }

    @Test fun default_sizes() {
        for (suffix in listOf("", "_1280x800", "_text2", "_text2_1280x800")) {
            shot("kit_composer_chips_default$suffix") { Default() }
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_composer_chips",
            caps = mapOf("KitComposerChips" to "conversation"),
            wrap = { c -> CompositionLocalProvider(LocalKitRedactor provides Redactor()) { c() } },
        ) { Default() }
    }
}
