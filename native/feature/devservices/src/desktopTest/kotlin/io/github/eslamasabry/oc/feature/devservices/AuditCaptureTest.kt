package io.github.eslamasabry.oc.feature.devservices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.runtime.services.commands.DevelopmentServiceStatus
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServiceLog
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesActions
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesAsk
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesProblem
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesReceipt
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesUi
import io.github.eslamasabry.oc.feature.devservices.presentation.EditorUi
import io.github.eslamasabry.oc.feature.devservices.presentation.FoundRowUi
import io.github.eslamasabry.oc.feature.devservices.presentation.NameError
import io.github.eslamasabry.oc.feature.devservices.presentation.ServiceRowUi
import io.github.eslamasabry.oc.feature.devservices.ui.DevServicesScreen
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/** Audit capture only: the Dev services page in many states. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val actions =
        Proxy.newProxyInstance(
            DevServicesActions::class.java.classLoader,
            arrayOf(DevServicesActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "DevServiceActions"
                else -> null
            }
        } as DevServicesActions

    private val environment = KitChatEnvironment(Redactor())

    private val running =
        ServiceRowUi(
            "preview", "Shopfront preview", "npm run dev", "http://localhost:5173",
            DevelopmentServiceStatus.Running, null, canStart = false, canStop = true, ports = persistentListOf(5173),
        )
    private val stopped =
        ServiceRowUi(
            "api", "API", "cargo run --release", "", DevelopmentServiceStatus.Stopped, 1,
            canStart = true, canStop = false,
        )
    private val fresh =
        ServiceRowUi(
            "docs", "A documentation server with a deliberately very long display name that must wrap",
            "npx some-very-long-command --with --many --flags --that --keep --going --and-going",
            "http://localhost:4000/a/very/long/path", DevelopmentServiceStatus.NotStarted, null,
            canStart = true, canStop = false,
        )
    private val base = DevServicesUi(directory = "/work/shop", supported = true, loading = false)

    private fun shot(
        name: String,
        ui: DevServicesUi,
        setup: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {},
    ) = Audit.guard("devservices__dev-services__${name}__build") {
        Audit.shoot("devservices", "dev-services", name, setup = setup) {
            environment.Provide { DevServicesScreen(ui, actions) }
        }
    }

    @Test
    fun devServices() {
        shot("loading", DevServicesUi())
        shot("unsupported", DevServicesUi(supported = false, loading = false, directory = "/work/shop"))
        shot("empty", base)
        shot("services", base.copy(services = persistentListOf(running, stopped, fresh)))
        shot(
            "services-menu",
            base.copy(services = persistentListOf(running, stopped)),
            setup = {
                onNodeWithContentDescription("More · Shopfront preview").performClick()
                waitForIdle()
                mainClock.advanceTimeBy(500)
            },
        )
        shot(
            "found",
            base.copy(
                services = persistentListOf(running),
                found = persistentListOf(
                    FoundRowUi("f1", "vite", "node vite --port 4173", persistentListOf(4173), canStop = true, hasLogs = true, canSave = true),
                    FoundRowUi("f2", "python http.server", "python -m http.server 8000", persistentListOf(8000), canStop = false, hasLogs = false, canSave = false),
                ),
                scansPhone = true,
            ),
        )
        shot("found-failed", base.copy(services = persistentListOf(running), foundFailed = true, scansPhone = true))
        shot("busy", base.copy(services = persistentListOf(running), busy = true))
        shot("unreadable", base.copy(readable = false))
        for (problem in DevServicesProblem.entries) {
            shot("problem-${problem.name.lowercase()}", base.copy(services = persistentListOf(running), problem = problem))
        }
        shot("offline-failure", base.copy(problem = DevServicesProblem.Offline, failure = Failure.Network(NetworkWhy.Offline)))
        shot("editor-empty", base.copy(editor = EditorUi()))
        shot("editor-errors", base.copy(editor = EditorUi(name = "API", command = "", url = "not a url", nameError = NameError.Duplicate, commandMissing = true, urlInvalid = true)))
        shot("ask-stop", base.copy(services = persistentListOf(running), ask = DevServicesAsk.Stop("preview", "Shopfront preview")))
        shot("ask-remove-running", base.copy(services = persistentListOf(running), ask = DevServicesAsk.Remove("preview", "Shopfront preview", running = true)))
        shot("receipt-started", base.copy(services = persistentListOf(running), receipt = DevServicesReceipt.Started("Shopfront preview")))
        shot("log", base.copy(services = persistentListOf(running), log = DevServiceLog("preview", false, "Shopfront preview", "ready in 312 ms\nlocal: http://localhost:5173/")))
        shot("log-failed", base.copy(services = persistentListOf(running), log = DevServiceLog("preview", false, "Shopfront preview", failed = true)))
    }
}
