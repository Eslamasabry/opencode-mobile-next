package android.os

class Bundle {
    private val entries = mutableMapOf<String, CharSequence?>()
    fun putCharSequence(key: String, value: CharSequence?) { entries[key] = value }
    fun getCharSequence(key: String): CharSequence? = entries[key]
    fun copy(): Bundle = Bundle().also { copy ->
        entries.forEach { (key, value) -> copy.putCharSequence(key, value) }
    }
}
