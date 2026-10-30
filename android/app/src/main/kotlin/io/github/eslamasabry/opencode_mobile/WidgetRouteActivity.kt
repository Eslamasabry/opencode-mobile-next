package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import org.json.JSONObject

/** Checks the builder feature gate before creating any Flutter engine. */
open class WidgetRouteActivity : Activity() {
    protected open val configureOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        if (savedInstanceState != null) return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val ours = ownsWidget(id)
        val mode = modeFor(id)
        val ready = WidgetV2Bridge.overlayReady(this)
        if (!ready || !ours || mode !in setOf("configure", "project", "steer", "voice")) {
            // Pre-31 launchers do not understand configuration_optional. Accept
            // default-style placement while the UI builder gate is disabled;
            // this does not report a style save or execute any widget action.
            if (!ready && ours && configureOnly) {
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            }
            Toast.makeText(
                this, NativeStrings.get(this, R.string.widget_overlay_unavailable), Toast.LENGTH_SHORT,
            ).show()
            finish()
            return
        }
        // Exported launcher configuration accepts only a widget id. All routing
        // identity is read locally. Non-configure intents target the private host.
        // A widget placed before the app has published its first snapshot (a
        // fresh install, or the app not opened since) is still styled: the
        // style window needs no conversation identity, and cancelling here
        // would make the launcher drop the widget the moment it lands.
        val payload = if (configureOnly) {
            WidgetV2Bridge.snapshot(this)?.let { JSONObject(WidgetV2Bridge.identity(it, null)).toString() }
                ?: JSONObject().toString()
        } else intent.getStringExtra(WidgetV2Bridge.EXTRA_PAYLOAD)
        if (payload == null) {
            finish()
        } else {
            startActivityForResult(Intent(this, WidgetOverlayActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(WidgetV2Bridge.EXTRA_MODE, mode)
                putExtra(WidgetV2Bridge.EXTRA_PAYLOAD, payload)
            }, OVERLAY_REQUEST)
        }
    }

    private fun ownsWidget(id: Int): Boolean {
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider
        return provider != null && provider.packageName == packageName && provider.className in WidgetProviders.allNames
    }

    /** Edit on the Conversations widget picks its project; the retired entries keep their style window. */
    private fun modeFor(id: Int): String = when {
        !configureOnly -> intent.getStringExtra(WidgetV2Bridge.EXTRA_MODE).orEmpty()
        AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className ==
            SessionsWideWidgetProvider::class.java.name -> "project"
        else -> "configure"
    }

    @Deprecated("Android activity result compatibility API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_REQUEST) {
            setResult(resultCode, data)
            finish()
        }
    }

    private companion object { const val OVERLAY_REQUEST = 7182 }
}

/** The launcher can only configure an installed widget owned by this package. */
class WidgetConfigureActivity : WidgetRouteActivity() {
    override val configureOnly = true
}
