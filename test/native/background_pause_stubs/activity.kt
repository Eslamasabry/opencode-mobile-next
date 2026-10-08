package android.app

data class ExitRecord(val processName: String?, val reason: Int, val timestamp: Long) {
    val description: String get() = error("OS description must never be read")
    override fun toString(): String = error("OS record text must never be read")
}

class ActivityManager {
    var isBackgroundRestricted = false
    var exits = listOf<ExitRecord>()
    var reads = 0
    fun getHistoricalProcessExitReasons(packageName: String, pid: Int, limit: Int): List<ExitRecord> {
        check(limit <= 16)
        reads++
        return exits.take(limit)
    }
}
