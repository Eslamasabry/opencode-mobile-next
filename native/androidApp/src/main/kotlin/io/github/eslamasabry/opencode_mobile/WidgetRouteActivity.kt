package io.github.eslamasabry.opencode_mobile

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetPanelMode
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetPanelViewModel
import io.github.eslamasabry.oc.ui.shell.widgets.ui.WidgetPanelScreen
import io.github.eslamasabry.opencode_mobile.graph.AppLocale
import io.github.eslamasabry.opencode_mobile.graph.SettingsAppGraph
import io.github.eslamasabry.opencode_mobile.graph.WidgetPanelAppGraph
import io.github.eslamasabry.opencode_mobile.graph.WidgetPanelBindings
import io.github.eslamasabry.opencode_mobile.graph.appGraph

/**
 * Frozen private route host. Cosmetic configuration never starts the app's navigation stack or a
 * server.
 */
open class WidgetRouteActivity : ComponentActivity() {
    protected open val configureOnly: Boolean = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val widgetId =
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        val graph = appGraph
        val source = (graph as WidgetPanelAppGraph).widgetPanelBindings
        if (!source.owns(widgetId)) {
            finish()
            return
        }
        val mode =
            when {
                configureOnly && source.choosesProject(widgetId) -> WidgetPanelMode.Project
                configureOnly -> WidgetPanelMode.Style
                intent.getStringExtra("oc.widgetMode") == "configure" -> WidgetPanelMode.Style
                intent.getStringExtra("oc.widgetMode") == "project" -> WidgetPanelMode.Project
                else -> WidgetPanelMode.Unavailable
            }
        val vm =
            ViewModelProvider(this, Factory(source, widgetId, mode))[
                WidgetPanelViewModel::class.java]
        val settings = graph as SettingsAppGraph
        val platform = AndroidKitPlatform(window.decorView, window)
        setContent {
            val appearance by settings.appPreferences.appearance.collectAsState()
            val state by vm.state.collectAsState()
            LaunchedEffect(state.saved, state.dismissed) {
                if (state.saved)
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                    )
                if (state.saved || state.dismissed) finish()
            }
            KitTheme(
                appearance = appearance,
                platform = platform,
                localeTag = AppLocale.applied ?: java.util.Locale.getDefault().toLanguageTag(),
            ) {
                WidgetPanelScreen(state, vm)
            }
        }
    }

    private class Factory(
        private val source: WidgetPanelBindings,
        private val id: Int,
        private val mode: WidgetPanelMode,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WidgetPanelViewModel(source, id, mode) as T
    }
}

/**
 * The exported launcher entry accepts only an installed widget id; routing identity is read from
 * local storage.
 */
class WidgetConfigureActivity : WidgetRouteActivity() {
    override val configureOnly: Boolean = true
}

/** Frozen alias for already-created private overlay intents. */
class WidgetOverlayActivity : WidgetRouteActivity()
