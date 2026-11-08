@file:Suppress("MagicNumber") // canned demo data and gallery chrome colours, not product values

package io.github.eslamasabry.oc.desktop.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.zacsweers.metro.createGraphFactory
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Severity
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.security.RedactingWriter
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.desktop.DesktopAppGraph
import io.github.eslamasabry.oc.desktop.desktopBuildInfo
import io.github.eslamasabry.oc.engine.agents.feed.BriefScope
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.FeedFolders
import io.github.eslamasabry.oc.engine.agents.feed.FeedPermission
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.code.CodeLanguage
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffModel
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffView
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.kitchat.tool.KitCommandBox
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import io.github.eslamasabry.oc.ui.shell.App
import java.io.File
import java.lang.reflect.Proxy
import javax.imageio.ImageIO
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

/**
 * Dev loop gallery (tool/dev/README.md): real screens with canned data at phone size, for Compose Hot Reload.
 * `./gradlew :desktopApp:hotRunGallery -Poc.hotReload=true` (or tool/dev/hot.sh); edit a screen or kit file, save, and
 * the window redraws in place. Every redraw of the phone frame is also written to $OC_HOT_SHOTS (default
 * ~/.cache/oc-dev-loop/shots) as `<scene>-<light|dark>.png` and `latest.png`, so an agent can edit and then read the
 * picture. Fake data only: it never connects anywhere except the Shell scene, which is the real desktop app.
 * Not part of the desktop product: the desktop app's main class stays MainKt.
 */
private enum class Scene { Conversations, ChatParts, Shell }

private val shots: File =
    File(System.getenv("OC_HOT_SHOTS") ?: "${System.getProperty("user.home")}/.cache/oc-dev-loop/shots")
        .also { it.mkdirs() }

fun main(args: Array<String>) {
    val redactor = Redactor()
    AppLog.install(RedactingWriter(redactor, emptyList()), Severity.Info)
    val graph by lazy { createGraphFactory<DesktopAppGraph.Factory>().create(desktopBuildInfo, redactor) }
    val first = args.firstOrNull()?.let { a -> Scene.entries.firstOrNull { it.name.equals(a, ignoreCase = true) } }
    application {
        val state = rememberWindowState(size = DpSize(440.dp, 1010.dp))
        Window(onCloseRequest = ::exitApplication, state = state, title = "OpenCode dev gallery (hot reload)") {
            Gallery(first ?: Scene.Conversations, redactor) { graph }
        }
    }
}

@Composable
private fun Gallery(initial: Scene, redactor: Redactor, graph: () -> DesktopAppGraph) {
    var scene by remember { mutableStateOf(initial) }
    var dark by remember { mutableStateOf(System.getenv("OC_HOT_DARK") == "1") }
    var arabic by remember { mutableStateOf(false) }
    var big by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color(0xFF202124))) {
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Scene.entries.forEach { s -> Chip(s.name, scene == s) { scene = s } }
            Chip(if (dark) "Dark" else "Light", dark) { dark = !dark }
            Chip("AR", arabic) { arabic = !arabic }
            Chip("1.3x", big) { big = !big }
        }
        val platform = remember { DesktopKitPlatform() }
        val shotName = "${scene.name.lowercase()}-${if (dark) "dark" else "light"}"
        Box(Modifier.padding(start = 8.dp, top = 4.dp)) {
            PhoneFrame(shotName) {
                val density = Density(LocalDensity.current.density, if (big) 1.3f else 1f)
                CompositionLocalProvider(LocalDensity provides density) {
                    if (scene == Scene.Shell) {
                        val g = remember { graph() }
                        g.kitChatEnvironment.Provide { App(g.shell, platform, g.metroViewModelFactory) }
                    } else {
                        CompositionLocalProvider(LocalKitPlatform provides platform) {
                            KitTheme(
                                appearance =
                                    KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light),
                                platform = platform,
                                localeTag = if (arabic) "ar" else "en",
                            ) {
                                CompositionLocalProvider(LocalKitRedactor provides redactor) {
                                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                        when (scene) {
                                            Scene.Conversations -> ConversationsScene()
                                            Scene.ChatParts -> ChatPartsScene()
                                            Scene.Shell -> Unit
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    BasicText(
        label,
        Modifier.background(if (on) Color(0xFF8FE5BD) else Color(0xFF3C4043)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = TextStyle(color = if (on) Color.Black else Color.White, fontSize = 13.sp),
    )
}

/** 412 x 915 dp, the plan 06 §15.3 phone; each redraw is saved (at most a few times a second) for agents. */
@Composable
private fun PhoneFrame(name: String, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val drawn = remember { Channel<Unit>(Channel.CONFLATED) }
    LaunchedEffect(name) {
        drawn.receiveAsFlow().collect {
            delay(120) // let a burst of frames settle
            val image = layer.toImageBitmap().toAwtImage()
            withContext(Dispatchers.IO) {
                val tmp = File(shots, ".$name.png.tmp")
                ImageIO.write(image, "png", tmp)
                tmp.copyTo(File(shots, "latest.png"), overwrite = true)
                tmp.renameTo(File(shots, "$name.png"))
            }
        }
    }
    Box(
        Modifier.size(412.dp, 915.dp).drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
            drawn.trySend(Unit)
        }
    ) { content() }
}

// --- Conversations: the home list with the feed rows of feature/chats LooksCaptureTest.home (fake data, not copy).

private val profile = ProfileId("p1")
private val briefScope = BriefScope(profile, "opencode:p1", "gallery")

private class FeedRow(val dir: String, val at: Long, val title: String)

private fun item(id: String, row: FeedRow, status: ChatStatus = ChatStatus.Idle, unseen: Boolean = false) =
    ChatFeedItem(
        sourceId = "opencode:p1",
        locationKey = briefScope.location,
        profileId = profile,
        sessionId = SessionId(id),
        title = row.title,
        directory = row.dir,
        projectName =
            if (FeedFolders.isOtherFolder(row.dir)) FeedFolders.otherFolderLabel(row.dir)
            else FeedFolders.folderName(row.dir),
        status = status,
        lastActivityMs = row.at,
        finishedUnseen = unseen,
    )

@Composable
private fun ConversationsScene() {
    val ui = remember {
        val now = System.currentTimeMillis()
        val min = 60_000L
        val hour = 60 * min
        val items =
            listOf(
                item("a", FeedRow("/work/api", now - 2 * min, "Fix login redirect loop"), ChatStatus.Running),
                item("b", FeedRow("/work/web", now - 25 * min, "Add dark mode to settings"), ChatStatus.NeedsYou)
                    .copy(permission = FeedPermission("r1", "npm test -- settings")),
                item("c", FeedRow("/work/api", now - 2 * hour, "Rate limit the upload endpoint"), unseen = true),
                item("d", FeedRow("/work/mobile", now - 5 * hour, "Migrate the chat list to Compose")),
                item("e", FeedRow("/work/web", now - 30 * hour, "Why is the build slow on CI?")),
                item("f", FeedRow("/work/docs", now - 80 * hour, "Rewrite the install guide")),
                item("g", FeedRow("/work/mobile", now - 200 * hour, "Crash when rotating the file viewer")),
            )
        val feed = ChatFeedSnapshot(items.toImmutableList())
        ChatsState.build(feed, ReturnBrief.None, ChatsFilters(), now - 6 * hour, true, now)
    }
    ChatsScreen(ui, noActions<ChatsActions>())
}

// --- Chat parts: the transcript parts of ui/kit-chat ChatScenes (code, tool steps, permission card, inline diff).

private val before = (1..30).joinToString("\n") { "fun step$it() = run($it)" }
private val after =
    before.replace("fun step8() = run(8)", "fun step8() = run(8, fast = true)").replace("fun step21() = run(21)\n", "")

@Composable
private fun ChatPartsScene() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KitToolRow(
            KitToolView(
                "t1", KitToolKind.Read, "Read main.dart", "lib/main.dart", null, KitToolStatus.Done, "0.4 s",
                null, null, "Done",
            ),
            opened = false, onToggle = {},
        )
        KitToolRow(
            KitToolView(
                "t2", KitToolKind.Shell, "Ran a command", "npm test", null, KitToolStatus.Failed, "3.1 s", null, null,
                "Exit code 1",
            ),
            opened = true, onToggle = {},
            body = {
                KitCommandBox(
                    command = "npm test", output = (1..14).joinToString("\n") { "  ✓ case $it" },
                    moreLabel = "Show all", onMore = {}, exitLabel = "Exit code 1",
                )
            },
        )
        KitPermissionCard(
            title = "Run npm install in neon-breaker?", summary = "The agent wants to run a command.",
            subject = "npm install --save-dev vitest",
            allowOnceLabel = "Allow once", alwaysLabel = "Always allow", denyLabel = "Deny",
            onAllowOnce = {}, onAlways = {}, onDeny = {},
        )
        KitCodeBlock(
            source = "import os\n\ndef main():\n    print(\"hello\", 42)  # greet\n",
            kind = KitCodeKind.Code, language = CodeLanguage.Python, copyLabel = "Copy", onCopy = {},
        )
        KitToolRow(
            KitToolView(
                "t4", KitToolKind.Edit, "Edited list.dart", "lib/list.dart", null, KitToolStatus.Done, null,
                1, 2, "Done",
            ),
            opened = true, onToggle = {},
            body = {
                KitDiffView(
                    file = DiffModel.fromBeforeAfter("lib/list.dart", before, after),
                    hunkLabel = { a, b, _, _ -> "Lines $a–$b" },
                    gapLabel = { "$it unchanged lines" },
                )
            },
        )
    }
}

/** Actions that do nothing, for any `XActions` interface: a screen drawn from canned state needs no behaviour. */
private inline fun <reified T : Any> noActions(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            else -> null
        }
    } as T
