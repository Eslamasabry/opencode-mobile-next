package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationModel
import io.github.eslamasabry.oc.feature.chats.ui.newConversationModelOptions
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitModelOption
import io.github.eslamasabry.oc.ui.kit.modal.KitModelSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ModelChoicesTest {
    @Test fun `B08 model choices show plain names and return the real model or default`() = runComposeUiTest {
        val real = ModelRef("private-provider-id", "real-model-id")
        val models = listOf(NewConversationModel(ModelRef("private-provider-id", "default"), "Default alias"), NewConversationModel(real, "Sonnet"))
        val selected = mutableListOf<ModelRef?>()
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                KitSheetHost {
                    KitModelSheet(
                        visible = true,
                        onDismissRequest = {},
                        options = newConversationModelOptions(models),
                        current = null,
                        onChoose = { o: KitModelOption? -> selected += o?.let { ModelRef(it.providerId, it.modelId) } },
                    )
                }
            }
        }
        onNodeWithText("Default alias").assertDoesNotExist()
        onNodeWithText("Sonnet").performClick()
        onNodeWithText("Server default").performClick()
        assertEquals(listOf(real, null), selected)
    }
}
