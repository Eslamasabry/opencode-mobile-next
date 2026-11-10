package io.github.eslamasabry.opencode_mobile

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

/**
 * What Android's App info › Storage › "Clear storage" (shown as "Manage
 * space") opens, via android:manageSpaceActivity: a page that says what
 * clearing deletes and offers Export projects first, Clear the app's cache
 * only, and Delete everything (confirmed, then clearApplicationUserData).
 *
 * Its own Flutter engine runs the `manageSpaceMain` entrypoint (lib/main.dart),
 * not the app: it works whether or not the app is running, and it neither
 * connects to a server nor starts the in-app one. Settings starts it
 * explicitly; it reads no extras, so being exported only lets another app
 * show the same page, where nothing happens without the person's tap.
 */
class ManageSpaceActivity : FlutterActivity() {
    private var bridge: ProjectExportBridge? = null

    override fun getDartEntrypointFunctionName(): String = "manageSpaceMain"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        bridge?.dispose()
        bridge = ProjectExportBridge(this, flutterEngine.dartExecutor.binaryMessenger)
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        bridge?.dispose()
        bridge = null
        super.cleanUpFlutterEngine(flutterEngine)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (bridge?.onActivityResult(requestCode, resultCode, data) == true) return
        super.onActivityResult(requestCode, resultCode, data)
    }
}
