package io.github.eslamasabry.opencode_mobile

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetComposePhase
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetComposeSource
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetComposeViewModel
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetPanelMode
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetPanelViewModel
import io.github.eslamasabry.oc.ui.shell.widgets.ui.WidgetComposeScreen
import io.github.eslamasabry.oc.ui.shell.widgets.ui.WidgetPanelScreen
import io.github.eslamasabry.opencode_mobile.graph.AppLocale
import io.github.eslamasabry.opencode_mobile.graph.SettingsAppGraph
import io.github.eslamasabry.opencode_mobile.graph.WidgetPanelAppGraph
import io.github.eslamasabry.opencode_mobile.graph.WidgetPanelBindings
import io.github.eslamasabry.opencode_mobile.graph.appGraph
import java.util.UUID

/** Frozen standalone widget host. Actions use their exact live target and never open MainActivity. */
open class WidgetRouteActivity : ComponentActivity() {
    protected open val configureOnly: Boolean = false
    private var composeModel: WidgetComposeViewModel? = null
    private var submissionId = ""
    private var close: () -> Unit = { finish() }
    private var recognitionInFlight = false
    private val handler = Handler(Looper.getMainLooper())
    private val speechTimeout = Runnable { composeModel?.dictated(null, unavailable = true) }
    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handler.removeCallbacks(speechTimeout)
        recognitionInFlight = false
        composeModel?.dictated(
            if (result.resultCode == RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() else null,
        )
    }

    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLocale.wrap(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val source = (appGraph as WidgetPanelAppGraph).widgetPanelBindings
        if (!source.owns(widgetId)) { finish(); return }
        val mode = intent.getStringExtra("oc.widgetMode")
        if (!configureOnly && mode in setOf("steer", "voice")) {
            submissionId = savedInstanceState?.getString("widgetSubmissionId") ?: UUID.randomUUID().toString()
            val vm = ViewModelProvider(this, ComposeFactory(
                source.compose(widgetId, intent.getStringExtra("oc.widgetPayload"), mode == "voice", submissionId),
                mode == "voice", savedInstanceState?.getBoolean("widgetMayHaveSent") == true,
            ))[WidgetComposeViewModel::class.java]
            composeModel = vm
            if (vm.state.value.phase == WidgetComposePhase.Dictating && !vm.state.value.dictationRequested) {
                recognitionInFlight = true
                handler.postDelayed(speechTimeout, 60_000)
            }
            close = vm::close
            composeContent(vm)
        } else {
            val page = when {
                configureOnly && source.choosesProject(widgetId) -> WidgetPanelMode.Project
                configureOnly || mode == "configure" -> WidgetPanelMode.Style
                mode == "project" -> WidgetPanelMode.Project
                else -> WidgetPanelMode.Unavailable
            }
            val vm = ViewModelProvider(this, PanelFactory(source, widgetId, page))[WidgetPanelViewModel::class.java]
            close = vm::close
            panelContent(vm, widgetId)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { close() }
        })
    }

    private fun panelContent(vm: WidgetPanelViewModel, widgetId: Int) {
        val settings = appGraph as SettingsAppGraph
        val platform = AndroidKitPlatform(window.decorView, window)
        setContent {
            val appearance by settings.appPreferences.appearance.collectAsState()
            val state by vm.state.collectAsState()
            LaunchedEffect(state.saved, state.dismissed) {
                if (state.saved) setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                if (state.saved || state.dismissed) finish()
            }
            KitTheme(appearance = appearance, platform = platform, localeTag = AppLocale.applied ?: java.util.Locale.getDefault().toLanguageTag()) {
                WidgetPanelScreen(state, vm)
            }
        }
    }

    private fun composeContent(vm: WidgetComposeViewModel) {
        val settings = appGraph as SettingsAppGraph
        val platform = AndroidKitPlatform(window.decorView, window)
        setContent {
            val appearance by settings.appPreferences.appearance.collectAsState()
            val state by vm.state.collectAsState()
            LaunchedEffect(state.finished) { if (state.finished) finish() }
            LaunchedEffect(state.dictationRequested) {
                if (state.dictationRequested) {
                    vm.dictationLaunched()
                    recognize(vm)
                }
            }
            KitTheme(appearance = appearance, platform = platform, localeTag = AppLocale.applied ?: java.util.Locale.getDefault().toLanguageTag()) {
                WidgetComposeScreen(state, vm)
            }
        }
    }

    private fun recognize(vm: WidgetComposeViewModel) {
        if (recognitionInFlight) { vm.dictated(null, unavailable = true); return }
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            AppLocale.applied?.takeIf { it.matches(Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8}){0,3}$")) }?.let {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, it)
            }
        }
        try {
            recognitionInFlight = true
            speech.launch(request)
            handler.postDelayed(speechTimeout, 60_000)
        } catch (_: RuntimeException) {
            recognitionInFlight = false
            vm.dictated(null, unavailable = true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (submissionId.isNotEmpty()) outState.putString("widgetSubmissionId", submissionId)
        outState.putBoolean("widgetMayHaveSent", composeModel?.state?.value?.mayHaveSent == true)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacks(speechTimeout)
        super.onDestroy()
    }

    private class PanelFactory(private val source: WidgetPanelBindings, private val id: Int, private val mode: WidgetPanelMode) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WidgetPanelViewModel(source, id, mode) as T
    }
    private class ComposeFactory(private val source: WidgetComposeSource, private val voice: Boolean, private val pending: Boolean) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WidgetComposeViewModel(source, voice, pending) as T
    }
}

/** The launcher may only configure its installed widget; it cannot request a prompt action or supply a target. */
class WidgetConfigureActivity : WidgetRouteActivity() { override val configureOnly: Boolean = true }

/** Frozen alias for already-created private overlay intents. */
class WidgetOverlayActivity : WidgetRouteActivity()
