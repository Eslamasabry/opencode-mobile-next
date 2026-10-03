package android.util
object Log {
    fun e(tag: String, message: String, failure: Throwable? = null): Int = 0
    fun w(tag: String, message: String, failure: Throwable? = null): Int = 0
}
