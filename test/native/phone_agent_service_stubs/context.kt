package android.content
import android.app.NotificationManager
open class Context {
    var selectedLanguage = "en"
    val applicationContext: Context get() = this
    fun <T> getSystemService(type: Class<T>): T? = type.cast(NotificationManager.instance)
    fun startForegroundService(intent: Intent) { if (denyDispatch) throw SecurityException("policy") }
    fun startService(intent: Intent) { if (denyDispatch) throw SecurityException("policy") }
    fun stopService(intent: Intent): Boolean = true
    companion object { var denyDispatch = false }
}
class Intent(context: Context, type: Class<*>) {
    private var selectedAction: String? = null
    val action: String? get() = selectedAction
    private val extras = mutableMapOf<String, String?>()
    fun getStringExtra(key: String): String? = extras[key]
    fun putExtra(key: String, value: String?): Intent { extras[key] = value; return this }
    fun addFlags(flags: Int): Intent = this
    fun setAction(value: String): Intent { selectedAction = value; return this }
    companion object { const val FLAG_ACTIVITY_SINGLE_TOP = 1; const val FLAG_ACTIVITY_CLEAR_TOP = 2 }
}
