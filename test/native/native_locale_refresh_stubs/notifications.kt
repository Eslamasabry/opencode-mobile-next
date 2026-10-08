package android.app

import android.content.Context
import android.os.Bundle

class Notification {
    var extras = Bundle()
    var actions: Array<Action>? = null
    var ongoing = true
    var channelId = "opencode_builtin_server"
    var visibility = -1

    class Action(var title: CharSequence, val intentToken: Any) {
        class Builder(private val original: Action) {
            fun build(): Action = Action(original.title, original.intentToken)
        }
    }

    class Builder(original: Notification) {
        private val value = Notification().also {
            it.extras = original.extras.copy()
            it.actions = original.actions?.map { action ->
                Action(action.title, action.intentToken)
            }?.toTypedArray()
            it.ongoing = original.ongoing
            it.channelId = original.channelId
            it.visibility = original.visibility
        }
        fun setContentTitle(title: CharSequence): Builder = apply {
            value.extras.putCharSequence(EXTRA_TITLE, title)
        }
        fun setContentText(text: CharSequence): Builder = apply {
            value.extras.putCharSequence(EXTRA_TEXT, text)
        }
        fun setActions(vararg actions: Action): Builder = apply { value.actions = arrayOf(*actions) }
        fun build(): Notification = value
        companion object {
            fun recoverBuilder(context: Context, notification: Notification): Builder = Builder(notification)
        }
    }

    companion object {
        const val EXTRA_TITLE = "android.title"
        const val EXTRA_TEXT = "android.text"
    }
}

class NotificationChannel(val id: String, var name: CharSequence, var importance: Int) {
    var description: String = ""
    var sound: String? = null
}

class ActiveNotification(val id: Int, val notification: Notification)

class NotificationManager {
    val channels = linkedMapOf<String, NotificationChannel>()
    val posted = linkedMapOf<Int, Notification>()
    val updates = mutableListOf<Int>()
    val activeNotifications: Array<ActiveNotification>
        get() = posted.map { (id, notification) -> ActiveNotification(id, notification) }.toTypedArray()
    fun getNotificationChannel(id: String): NotificationChannel? = channels[id]
    fun createNotificationChannel(channel: NotificationChannel) { channels[channel.id] = channel }
    fun notify(id: Int, notification: Notification) {
        posted[id] = notification
        updates.add(id)
    }
}
