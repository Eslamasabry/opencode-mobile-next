package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.QuestionChoice
import io.github.eslamasabry.oc.core.model.QuestionPrompt
import io.github.eslamasabry.oc.engine.requests.questions.QuestionSheet
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.QuestionSheetUi
import io.github.eslamasabry.oc.feature.chat.requests.ui.ChatQuestionSheet
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceGroup
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class QuestionSheetScreenTest {
    @Test fun `B-CHAT-072 kit choice group exposes selected checks and does not select disabled choices`() = runComposeUiTest {
        val chosen = mutableListOf<String>()
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                KitChoiceGroup(
                    choices = listOf(KitChoice("staging", "Staging"), KitChoice("live", "Production", enabled = KitEnabled.Disabled("Unavailable"))),
                    selected = setOf("staging"), semanticsLabel = "Deploy target", multiple = true,
                    onSelect = { chosen += it },
                )
            }
        }
        onNodeWithText("Staging").assertIsOn().performClick()
        onNodeWithText("Production").assertIsNotEnabled()
        assertEquals(listOf("staging"), chosen)
    }

    @Test fun `B-CHAT-072 own answer is visible immediately and choosing a radio answer clears its text`() = runComposeUiTest {
        val prompt = QuestionPrompt("Deploy target", "Where should the release build go first?", false, true, false,
            persistentListOf(QuestionChoice("Staging", "Test environment"), QuestionChoice("Production", "Live users")), false)
        val question = PendingQuestion(RequestId("q_deploy"), SessionId("ses_release"), persistentListOf(prompt))
        val state = mutableStateOf(QuestionSheetUi(question, QuestionSheet.emptyDraft(question), false))
        val actions = object : Actions() {
            private fun replace(draft: io.github.eslamasabry.oc.engine.requests.questions.PromptDraft) {
                val drafts = persistentListOf(draft)
                state.value = QuestionSheetUi(question, drafts, QuestionSheet.block(question, drafts) == null)
            }
            override fun chooseInSheet(id: RequestId, prompt: Int, label: String) {
                replace(QuestionSheet.choose(question.prompts[prompt], state.value.drafts[prompt], label))
            }
            override fun editSheetOther(id: RequestId, prompt: Int, text: String) {
                replace(QuestionSheet.write(question.prompts[prompt], state.value.drafts[prompt], text))
            }
        }
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                KitSheetHost { ChatQuestionSheet(state.value, actions) }
            }
        }
        onNodeWithText("OpenCode needs input").assertExists()
        onNodeWithText("Send answers").assertIsNotEnabled()
        val field = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("question-own-answer-0")))
        field.performTextInput("Canary")
        onNodeWithText("Send answers").assertIsEnabled()
        onNodeWithText("Staging").performClick()
        field.assertTextEquals("")
        assertEquals<List<String>?>(listOf("Staging"), QuestionSheet.answers(question, state.value.drafts)?.single())
    }

    private open class Actions : ChatRequestActions {
        override fun shown(id: RequestId) {}
        override fun allowOnce(id: RequestId) {}
        override fun reject(id: RequestId) {}
        override fun askAlwaysAllow(id: RequestId) {}
        override fun confirmAlwaysAllow() {}
        override fun cancelAlwaysAllow() {}
        override fun keepAsking(id: RequestId) {}
        override fun choose(id: RequestId, option: String) {}
        override fun openOther(id: RequestId) {}
        override fun editOther(id: RequestId, text: String) {}
        override fun sendOther(id: RequestId) {}
        override fun openSheet(id: RequestId) {}
        override fun closeSheet(id: RequestId) {}
        override fun chooseInSheet(id: RequestId, prompt: Int, label: String) {}
        override fun editSheetOther(id: RequestId, prompt: Int, text: String) {}
        override fun sendSheet(id: RequestId) {}
        override fun undo(id: RequestId) {}
        override fun retry(id: RequestId) {}
    }
}
