package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.RecordingActions
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.choice.KitStyleOptionCard
import io.github.eslamasabry.oc.ui.kit.choice.KitThemeOptionCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class AppearanceCardsTest {
    @Test
    fun `B-KIT-037 visual style choices apply through the existing action`() = runComposeUiTest {
        val selected = mutableStateOf(Style.Solid)
        val calls = mutableListOf<Style>()
        setContent {
            SettingsThemed {
                Column {
                    for (style in listOf(Style.Solid, Style.Soft)) {
                        KitStyleOptionCard(
                            style,
                            style.name,
                            selected.value == style,
                            {
                                calls += style
                                selected.value = style
                            },
                        )
                    }
                    KitStyleOptionCard(
                        Style.Paper,
                        "Paper",
                        false,
                        { calls += Style.Paper },
                        enabled = false,
                        disabledReason = "Held by Saver",
                    )
                }
            }
        }
        onNodeWithContentDescription("Soft").performClick().assertIsSelected()
        assertEquals(listOf(Style.Soft), calls)
        onNodeWithContentDescription("Paper").assertIsNotEnabled().performClick()
        assertEquals(listOf(Style.Soft), calls)
    }

    @Test
    fun `B-KIT-049 theme swatch is a named choice with an immediate callback`() = runComposeUiTest {
        val calls = mutableListOf<PackId>()
        setContent {
            SettingsThemed {
                KitThemeOptionCard(
                    PackId.Nord,
                    "Nord",
                    false,
                    { calls += PackId.Nord },
                    Modifier.height(94.dp),
                )
            }
        }
        onNodeWithContentDescription("Nord").performClick()
        assertEquals(listOf(PackId.Nord), calls)
    }

    @Test
    fun `B-KIT-061 Appearance themes apply immediately without a second confirmation`() =
        runComposeUiTest {
            val actions = RecordingActions()
            val ui =
                SettingsUi(
                    stack = persistentListOf(SettingsPage.Hub, SettingsPage.Appearance),
                    appearance =
                        AppearanceUi(packs = persistentListOf(PackId.Opencode, PackId.Nord)),
                )
            setContent { SettingsThemed { SettingsScreen(ui, actions) } }
            onNodeWithContentDescription("Nord").performScrollTo().performClick()
            assertEquals(listOf("pack:Nord"), actions.calls)
        }
}
