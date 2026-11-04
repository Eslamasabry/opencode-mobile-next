package io.github.eslamasabry.opencode_mobile

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.data.migration.GateState
import io.github.eslamasabry.oc.feature.widgetpanels.WidgetPanelEntries
import io.github.eslamasabry.oc.feature.widgetpanels.presentation.WidgetPanelData
import io.github.eslamasabry.oc.feature.widgetpanels.presentation.WidgetPanelHost
import io.github.eslamasabry.oc.feature.widgetpanels.presentation.WidgetPanelProblem
import io.github.eslamasabry.oc.feature.widgetpanels.presentation.WidgetSubmission
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.routes.WidgetPanel
import io.github.eslamasabry.oc.ui.shell.StandaloneRoot
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.opencode_mobile.graph.AppLocale
import io.github.eslamasabry.opencode_mobile.graph.WidgetPanelAccessors
import io.github.eslamasabry.opencode_mobile.graph.WidgetPanelsWiring
import io.github.eslamasabry.opencode_mobile.graph.appGraph
import io.github.eslamasabry.opencode_mobile.graph.migration
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource

/**
 * Secure one-panel window. It opens no connection and reads no profile credentials or conversation
 * text.
 */
class WidgetOverlayActivity : ComponentActivity() {
    private lateinit var host: PanelHost
    private val speech =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (::host.isInitialized) host.onSpeech(result.resultCode, result.data)
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val graph = appGraph
        val wiring = (graph as WidgetPanelAccessors).widgetPanelsWiring
        host =
            ViewModelProvider(
                this,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        PanelHost(applicationContext, wiring, savedInstanceState) as T
                },
            )[PanelHost::class.java]
        host.activity = this
        lifecycleScope.launch { host.prepare(intent) }
        val platform = AndroidKitPlatform(window.decorView, window)
        setContent {
            val prepared by host.display.collectAsState()
            val gate by graph.migration.engine.gate.state.collectAsState()
            val display = (prepared as? Outcome.Ok)?.value
            val route = host.route
            if (display != null && route != null) {
                val entries = remember(host) { setOf(WidgetPanelEntries(host)) }
                StandaloneRoot(
                    route,
                    entries,
                    platform,
                    display.appearance,
                    display.locale,
                    ::finish,
                )
            } else
                StandaloneRoot(platform, localeTag = AppLocale.applied) {
                    val waiting = gate !is GateState.Open && gate !is GateState.Blocked
                    KitScreen(
                        topBar = {
                            KitTopBar(
                                stringResource(Res.string.widgetV2ConfigureTitle),
                                exit = KitTopBarExit.Close,
                                exitLabel = stringResource(Res.string.kitTopBarClose),
                                onExit = ::finish,
                            )
                        },
                        loading = waiting,
                    ) {
                        KitNotice(
                            stringResource(
                                if (waiting) Res.string.widgetPanelUpdating
                                else if (gate is GateState.Blocked)
                                    Res.string.widgetPanelOpenAppForUpdate
                                else Res.string.widgetPanelOpenFailed
                            )
                        )
                        if (gate is GateState.Blocked)
                            KitButton(
                                stringResource(Res.string.widgetPanelOpenApp),
                                {
                                    startActivity(
                                        Intent(this@WidgetOverlayActivity, MainActivity::class.java)
                                    )
                                    finish()
                                },
                            )
                    }
                }
        }
    }

    internal fun requestSpeech() {
        speech.launch(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                AppLocale.applied?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        host.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::host.isInitialized && host.activity === this) host.activity = null
        super.onDestroy()
    }

    companion object {
        internal const val CONFIGURE_ONLY = "oc.widgetConfigureOnly"
    }
}

/**
 * Retained only across this host's configuration changes. Drafts remain in the UI's platform text
 * field.
 */
private class PanelHost(
    private val context: Context,
    private val wiring: WidgetPanelsWiring,
    state: Bundle?,
) : ViewModel(), WidgetPanelHost {
    var activity: WidgetOverlayActivity? = null
    val display = MutableStateFlow<Outcome<WidgetPanelsWiring.Display>?>(null)
    var route: WidgetPanel? = null
        private set

    private var store: WidgetPanelStore? = null
    private var preparing = false
    private val submissionId = state?.getString("submissionId") ?: UUID.randomUUID().toString()
    private var submitted = state?.getBoolean("submitted") ?: false
    private var saved = false
    private var speech: CompletableDeferred<Outcome<String>>? = null

    suspend fun prepare(intent: Intent) {
        if (display.value != null || preparing) return
        preparing = true
        val result = wiring.prepare()
        if (result is Outcome.Ok) {
            val target =
                WidgetPanelStore.fromIntent(
                    context,
                    intent,
                    intent.getBooleanExtra(WidgetOverlayActivity.CONFIGURE_ONLY, false),
                )
            if (target == null) {
                activity?.finish()
                return
            }
            store = target
            route =
                when (target.mode) {
                    "configure" -> WidgetPanel.Style(target.widgetId)
                    "project" -> WidgetPanel.Project(target.widgetId)
                    else -> WidgetPanel.Steer(target.widgetId, target.mode == "voice")
                }
        }
        display.value = result
    }

    override suspend fun load(): Outcome<WidgetPanelData> =
        withContext(wiring.dispatchers.io) {
            when (val loaded = store?.load() ?: Outcome.Failed(Failure.Device())) {
                is Outcome.Failed -> loaded
                is Outcome.Ok ->
                    loaded.value.let {
                        Outcome.Ok(
                            WidgetPanelData(
                                it.background,
                                it.accent,
                                it.project,
                                it.projects,
                                it.steerMode,
                                submitted,
                            )
                        )
                    }
            }
        }

    override suspend fun saveStyle(background: String, accent: String): Outcome<Unit> =
        withContext(wiring.dispatchers.io) {
            (store?.saveStyle(background, accent) ?: Outcome.Failed(Failure.Device())).also {
                if (it is Outcome.Ok) saved = true
            }
        }

    override suspend fun saveProject(project: String): Outcome<Unit> =
        withContext(wiring.dispatchers.io) {
            (store?.saveProject(project) ?: Outcome.Failed(Failure.Device())).also {
                if (it is Outcome.Ok) saved = true
            }
        }

    override suspend fun submit(text: String): WidgetSubmission {
        if (submitted) return WidgetSubmission(false, WidgetPanelProblem.Uncertain)
        submitted = true
        val result = attempt({ Failure.Device() }) { store?.submit(text, submissionId) }
        if (result is Outcome.Failed) return WidgetSubmission(false, WidgetPanelProblem.Uncertain)
        val answer =
            (result as Outcome.Ok).value ?: mapOf("status" to "rejected", "error" to "unavailable")
        if (answer["status"] == "accepted") return WidgetSubmission(true).also { saved = true }
        val problem =
            when {
                answer["status"] == "uncertain" || answer["error"] == "uncertainDelivery" ->
                    WidgetPanelProblem.Uncertain
                answer["error"] == "staleRequest" -> WidgetPanelProblem.Stale
                answer["error"] == "unsupported" -> WidgetPanelProblem.Unsupported
                answer["error"] == "busy" -> WidgetPanelProblem.Busy
                answer["error"] == "invalidRequest" -> WidgetPanelProblem.Invalid
                else -> WidgetPanelProblem.Unavailable
            }
        if (problem != WidgetPanelProblem.Uncertain) submitted = false
        return WidgetSubmission(false, problem)
    }

    override suspend fun dictate(): Outcome<String> {
        if (submitted || speech != null || store?.currentScope() != true)
            return Outcome.Failed(Failure.Device())
        val pending = CompletableDeferred<Outcome<String>>()
        speech = pending
        val target = activity
        if (target == null) {
            speech = null
            return Outcome.Failed(Failure.Device())
        }
        try {
            target.requestSpeech()
        } catch (_: RuntimeException) {
            speech = null
            return Outcome.Failed(Failure.Device())
        }
        return try {
            withTimeoutOrNull(60_000) { pending.await() } ?: Outcome.Failed(Failure.TimedOut())
        } finally {
            if (speech === pending) speech = null
        }
    }

    fun onSpeech(code: Int, data: Intent?) {
        val pending = speech ?: return
        speech = null
        val text =
            data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
                .trim()
                .take(8000)
        pending.complete(
            if (
                code == android.app.Activity.RESULT_OK &&
                    text.isNotEmpty() &&
                    store?.currentScope() == true
            )
                Outcome.Ok(text)
            else Outcome.Failed(Failure.Device())
        )
    }

    override fun close() {
        activity?.let { owner ->
            if (saved)
                owner.setResult(
                    android.app.Activity.RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, store?.widgetId),
                )
            owner.finish()
        }
    }

    fun save(state: Bundle) {
        state.putString("submissionId", submissionId)
        state.putBoolean("submitted", submitted)
    }

    override fun onCleared() {
        speech?.cancel()
        speech = null
        activity = null
    }
}
