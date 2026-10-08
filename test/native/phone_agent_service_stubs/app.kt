package android.app
import android.content.Context
import android.content.Intent
import android.os.IBinder
import io.github.eslamasabry.opencode_mobile.ServiceEvents
open class Service : Context() {
    @Volatile var stopped = false
    @Volatile var foreground = false
    open fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = 0
    open fun onTimeout(startId: Int, fgsType: Int) {}
    open fun onBind(intent: Intent?): IBinder? = null
    open fun onDestroy() {}
    fun startForeground(id: Int, notification: Notification, type: Int = 0) {
        if (denyForeground) throw SecurityException("policy")
        foreground = true
        ServiceEvents.add("foreground.start")
    }
    fun stopForeground(flags: Int) { foreground = false; ServiceEvents.add("foreground.stop") }
    fun stopSelf() { stopped = true; ServiceEvents.add("service.stop") }
    fun stopSelf(id: Int) { stopSelf() }
    companion object { const val START_STICKY = 1; const val START_NOT_STICKY = 2; const val STOP_FOREGROUND_REMOVE = 1; var denyForeground = false }
}
class Notification {
    class Builder {
        constructor(context: Context)
        constructor(context: Context, channel: String)
        fun setSmallIcon(icon: Int) = this
        fun setContentTitle(title: String) = this
        fun setContentText(text: String) = this
        fun setOngoing(on: Boolean) = this
        fun setOnlyAlertOnce(on: Boolean) = this
        fun setAutoCancel(on: Boolean) = this
        fun setContentIntent(intent: PendingIntent) = this
        fun addAction(action: Action) = this
        fun build() = Notification()
    }
    class Action {
        class Builder(icon: Any?, label: String, intent: PendingIntent) { fun build() = Action() }
    }
}
class NotificationChannel(id: String, name: String, importance: Int) {
    var description: String = ""
    fun setShowBadge(on: Boolean) {}
}
class NotificationManager {
    fun createNotificationChannel(channel: NotificationChannel) { if (denyChannel) throw SecurityException("policy") }
    fun notify(id: Int, notification: Notification) { if (denyNotify) throw SecurityException("policy") }
    fun cancel(id: Int) {}
    companion object {
        const val IMPORTANCE_LOW = 1
        val instance = NotificationManager()
        var denyChannel = false
        var denyNotify = false
    }
}
class PendingIntent {
    companion object {
        const val FLAG_UPDATE_CURRENT = 1
        const val FLAG_IMMUTABLE = 2
        fun getActivity(context: Context, code: Int, intent: Intent, flags: Int) = PendingIntent()
        fun getService(context: Context, code: Int, intent: Intent, flags: Int) = PendingIntent()
    }
}

class ActivityManager {
    class RunningAppProcessInfo {
        var importance = IMPORTANCE_FOREGROUND
        companion object { const val IMPORTANCE_FOREGROUND = 100 }
    }
    companion object {
        fun getMyMemoryState(info: RunningAppProcessInfo) {}
    }
}
