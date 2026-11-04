package io.github.eslamasabry.opencode_mobile

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Launcher configuration accepts only an owned widget id; private action routes retain their bound
 * identity.
 */
open class WidgetRouteActivity : ComponentActivity() {
    protected open val configureOnly: Boolean = false
    private val overlay =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            setResult(result.resultCode, result.data)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        if (savedInstanceState != null) return
        val store = WidgetPanelStore.fromIntent(this, intent, configureOnly) ?: return finish()
        overlay.launch(
            Intent(this, WidgetOverlayActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, store.widgetId)
                putExtra(WidgetPanelStore.EXTRA_MODE, store.mode)
                putExtra(
                    WidgetPanelStore.EXTRA_PAYLOAD,
                    if (configureOnly) WidgetPanelStore.configurePayload(this@WidgetRouteActivity)
                    else intent.getStringExtra(WidgetPanelStore.EXTRA_PAYLOAD),
                )
                putExtra(WidgetOverlayActivity.CONFIGURE_ONLY, configureOnly)
                if (!configureOnly) data = intent.data
            }
        )
    }
}

/** Frozen external launcher entry. Its caller cannot provide a conversation identity. */
class WidgetConfigureActivity : WidgetRouteActivity() {
    override val configureOnly: Boolean = true
}
