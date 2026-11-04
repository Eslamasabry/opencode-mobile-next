package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.eslamasabry.oc.android.widgets.WidgetTextActions

/**
 * Frozen widget PendingIntent target. Typed text is collected only in the bounded native overlay.
 */
class WidgetRouteActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(WidgetTextActions.EXTRA_MODE)
        val chosen =
            mode.takeIf { it in setOf("configure", "project", "steer", "voice") } ?: "configure"
        if (chosen in setOf("configure", "project") || WidgetTextActions.target(intent) != null) {
            startActivity(
                Intent(this, WidgetOverlayActivity::class.java).apply {
                    putExtra(WidgetTextActions.EXTRA_MODE, chosen)
                    putExtra(
                        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
                        intent.getIntExtra(
                            android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
                            android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID,
                        ),
                    )
                    if (chosen in setOf("steer", "voice"))
                        putExtra(
                            WidgetTextActions.EXTRA_PAYLOAD,
                            intent.getStringExtra(WidgetTextActions.EXTRA_PAYLOAD),
                        )
                }
            )
        }
        finish()
    }
}
