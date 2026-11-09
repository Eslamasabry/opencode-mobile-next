package io.github.eslamasabry.opencode_mobile

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.StrictMode

/**
 * Dev-flavour debug builds only (src/devDebug): turns on StrictMode as the process starts, before the application's
 * own code, so slow calls on the main thread and leaked resources show in logcat. It logs, never crashes (the first
 * pass is to see what is there). LeakCanary installs itself the same way from its own provider.
 */
class DebugHygieneProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().detectActivityLeaks().penaltyLog().build())
        return true
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0

    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
