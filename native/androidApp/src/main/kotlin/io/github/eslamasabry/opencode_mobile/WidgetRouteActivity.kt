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
        val target = WidgetTextActions.target(intent)
        if (target != null) {
            startActivity(
                Intent(this, WidgetOverlayActivity::class.java).apply {
                    putExtra(WidgetTextActions.EXTRA_MODE, "steer")
                    putExtra(
                        WidgetTextActions.EXTRA_PAYLOAD,
                        intent.getStringExtra(WidgetTextActions.EXTRA_PAYLOAD),
                    )
                }
            )
        } else {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        finish()
    }
}
