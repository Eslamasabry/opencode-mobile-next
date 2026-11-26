package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoStage
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoWords
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoWorld
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.stateDemoAfter
import io.github.eslamasabry.oc.ui.words.res.stateDemoAllowed
import io.github.eslamasabry.oc.ui.words.res.stateDemoBefore
import io.github.eslamasabry.oc.ui.words.res.stateDemoDenied
import io.github.eslamasabry.oc.ui.words.res.stateDemoLeave
import io.github.eslamasabry.oc.ui.words.res.stateDemoModel
import io.github.eslamasabry.oc.ui.words.res.stateDemoMore
import io.github.eslamasabry.oc.ui.words.res.stateDemoOwn
import io.github.eslamasabry.oc.ui.words.res.stateDemoPrompt
import io.github.eslamasabry.oc.ui.words.res.stateDemoReply
import io.github.eslamasabry.oc.ui.words.res.stateDemoReset
import io.github.eslamasabry.oc.ui.words.res.stateDemoTitle
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import org.jetbrains.compose.resources.stringResource

/**
 * Before/after renders of the demo chat when a person sends a question of their own (B-428): 360 dp, dark, English and
 * Arabic, text scale 1.0 and 1.3. BEFORE shows the old scripted reply and Edit welcome.txt request card; AFTER shows
 * the honest stateDemoOwn line with no request card and the message box ready. Writes PNGs to `DEMO_OWN_QUESTION_OUT`,
 * `~/render-out/demo-own-question` on the picture server, or `build/demo-own-question-render`.
 */
@OptIn(ExperimentalTestApi::class)
class DemoOwnQuestionRenders {
    private val out: File =
        (
            System.getenv("DEMO_OWN_QUESTION_OUT")?.let(::File)
                ?: File(System.getProperty("user.home"), "render-out")
                    .takeIf { it.isDirectory }
                    ?.resolve("demo-own-question")
                ?: File("build/demo-own-question-render")
        ).also(File::mkdirs)

    private val dispatchers =
        object : AppDispatchers {
            override val main = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined

            override fun serial(name: String) = Dispatchers.Unconfined
        }

    private val composerActions =
        object : BasicComposerActions {
            override fun draftChanged(text: String) = Unit

            override suspend fun send(text: String) = true

            override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
        }

    private val noRequestActions =
        object : ChatRequestActions {
            override fun shown(id: RequestId) = Unit

            override fun allowOnce(id: RequestId) = Unit

            override fun reject(id: RequestId) = Unit

            override fun askAlwaysAllow(id: RequestId) = Unit

            override fun confirmAlwaysAllow() = Unit

            override fun cancelAlwaysAllow() = Unit

            override fun keepAsking(id: RequestId) = Unit

            override fun choose(id: RequestId, option: String) = Unit

            override fun openOther(id: RequestId) = Unit

            override fun editOther(id: RequestId, text: String) = Unit

            override fun sendOther(id: RequestId) = Unit

            override fun openSheet(id: RequestId) = Unit

            override fun closeSheet(id: RequestId) = Unit

            override fun chooseInSheet(id: RequestId, prompt: Int, label: String) = Unit

            override fun editSheetOther(id: RequestId, prompt: Int, text: String) = Unit

            override fun sendSheet(id: RequestId) = Unit

            override fun undo(id: RequestId) = Unit

            override fun retry(id: RequestId) = Unit
        }

    @Test
    fun `B-428 before and after demo own question renders at 360 dp dark en and ar`() {
        val locales = listOf("en" to EN_QUESTION, "ar" to AR_QUESTION)
        val scales = listOf(1.0f to "t10", 1.3f to "t13")
        for ((locale, question) in locales) {
            for ((scale, tag) in scales) {
                shoot(locale, question, beforeFix = true, scale = scale, name = "before-$locale-$tag")
                shoot(locale, question, beforeFix = false, scale = scale, name = "after-$locale-$tag")
            }
        }
    }

    private fun shoot(locale: String, question: String, beforeFix: Boolean, scale: Float, name: String) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(locale))
        val world = DemoWorld(dispatchers)
        try {
            kitUiTest(WIDTH_PX, HEIGHT_PX) {
                setContent { DemoScene(world, locale, question, beforeFix, scale) }
                waitForIdle()
                mainClock.advanceTimeBy(SETTLE_MS)
                waitForIdle()
                assertOutcome(world, beforeFix)
                onNodeWithText(question, substring = true).assertIsDisplayed()
                if (!beforeFix) {
                    val expected = if (locale == "ar") AR_OWN_SNIPPET else EN_OWN_SNIPPET
                    onNodeWithText(expected, substring = true).assertIsDisplayed()
                }
                val image = onRoot().captureToImage().toAwtImage()
                val file = File(out, "$name.png")
                ImageIO.write(image, "png", file)
                assertEquals(WIDTH_PX, image.width)
                assertTrue(file.length() > 0L, "expected non-empty render ${file.name}")
            }
        } finally {
            world.close()
            Locale.setDefault(previous)
        }
    }

    private fun assertOutcome(world: DemoWorld, beforeFix: Boolean) {
        if (beforeFix) {
            assertNotNull(world.request.value)
            assertEquals(DemoStage.Review, world.stage.value)
        } else {
            assertNull(world.request.value)
            assertEquals(DemoStage.Ready, world.stage.value)
        }
    }

    @Composable
    private fun DemoScene(world: DemoWorld, locale: String, question: String, beforeFix: Boolean, scale: Float) {
        val presenter = remember(world) { ChatPresenter(world, RecordingNavigator(), Immediate) }
        val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }
        val states = remember(presenter) { presenter.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly) }
        val environment = remember { KitChatEnvironment(Redactor()) }
        CompositionLocalProvider(LocalDensity provides Density(DENSITY, scale)) {
            KitTheme(
                appearance = KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = MotionLevel.Off),
                platform = DesktopKitPlatform(),
                localeTag = locale,
                stillFrame = true,
            ) {
                val words = demoWords()
                LaunchedEffect(words, beforeFix, question) {
                    val active = if (beforeFix) words.copy(prompt = question) else words
                    world.configure(active)
                    world.send(question)
                }
                DemoScreenContent(world, presenter, states, environment, words)
            }
        }
    }

    @Composable
    private fun DemoScreenContent(
        world: DemoWorld,
        presenter: ChatPresenter,
        states: kotlinx.coroutines.flow.StateFlow<io.github.eslamasabry.oc.feature.chat.presentation.ChatUi>,
        environment: KitChatEnvironment,
        words: DemoWords,
    ) {
        val state by states.collectAsState()
        val req by world.request.collectAsState()
        val stage by world.stage.collectAsState()
        val draft by world.draft.collectAsState()
        val requests =
            RequestSlotUi(
                req?.let {
                    RequestCardUi.Permission(
                        id = it.id,
                        waitingSinceMs = null,
                        state = CardState.Waiting,
                        ask = PermissionAsk.of(it),
                        canAlwaysAllow = false,
                        captured = PendingRequest.Permission(it),
                    )
                }
            )
        val sample =
            words.model.takeIf { it.isNotEmpty() }?.let {
                ComposerModel("demo", "demo", it, supportsAttachments = false, providerName = words.title)
            }
        val composerUi =
            BasicComposerUi(
                available = stage != DemoStage.Review,
                sending = false,
                steers = false,
                draft = draft,
                targetKey = "offline-demo",
                models = ComposerModels(choices = listOfNotNull(sample), selected = sample),
            )
        val host =
            object : ComposerHost {
                @Composable
                override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                    BasicComposer(composerUi, composerActions, chrome, modifier)
                }
            }
        environment.Provide {
            KitSheetHost {
                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                    ChatScreen(
                        state,
                        presenter,
                        host,
                        requests = requests,
                        requestActions = noRequestActions,
                        demoMode = true,
                        extraActions =
                            listOf(
                                KitLineAction(stringResource(Res.string.stateDemoReset)) { Unit },
                                KitLineAction(stringResource(Res.string.stateDemoLeave)) { Unit },
                            ),
                    )
                }
            }
        }
    }

    @Composable
    private fun demoWords(): DemoWords {
        val before = stringResource(Res.string.stateDemoBefore)
        val after = stringResource(Res.string.stateDemoAfter)
        val prompt = stringResource(Res.string.stateDemoPrompt)
        return DemoWords(
            title = stringResource(Res.string.stateDemoTitle),
            prompt = prompt,
            reply = stringResource(Res.string.stateDemoReply, before, after),
            allowed = stringResource(Res.string.stateDemoAllowed, after),
            denied = stringResource(Res.string.stateDemoDenied),
            before = before,
            after = after,
            more = stringResource(Res.string.stateDemoMore),
            model = stringResource(Res.string.stateDemoModel),
            own = stringResource(Res.string.stateDemoOwn, prompt),
        )
    }

    private companion object {
        /** 360 dp wide at the A54's density. */
        const val DENSITY = 2.625f
        const val WIDTH_PX = 945
        const val HEIGHT_PX = 2100
        const val SETTLE_MS = 1_000L
        const val EN_QUESTION = "Which Excel formula adds up column B?"
        const val AR_QUESTION = "ما معادلة إكسل التي تجمع العمود B؟"
        const val EN_OWN_SNIPPET = "This demo can’t answer your own questions."
        const val AR_OWN_SNIPPET = "لا يمكن لهذه التجربة الإجابة عن أسئلتك الخاصة."
    }
}
