package io.github.eslamasabry.opencode_mobile

private const val MAIN = "private.package"
private const val SECRET = "private-token /private/path https://private.example"

class NumericSubReason {
    fun getSubReason(): Int = 25
    fun getDescription(): String = error("description must not be read: $SECRET")
    override fun toString(): String = error("toString must not be read: $SECRET")
}

class BlockedSubReason {
    fun getSubReason(): Int = throw SecurityException(SECRET)
}

@Suppress("UNCHECKED_CAST")
private fun entries(result: Map<String, Any?>): List<Map<String, Any?>> =
    result["entries"] as List<Map<String, Any?>>

private fun queryBounds() {
    check(AppExitHistoryPolicy.limit(null) == 10)
    check(AppExitHistoryPolicy.limit(1) == 1)
    check(AppExitHistoryPolicy.limit(50L) == 50)
    for (raw in listOf(0, -1, 51, Long.MAX_VALUE, 1.5, "10")) {
        check(AppExitHistoryPolicy.limit(raw) == null)
    }
    var calls = 0
    fun read(maxNum: Int): List<AppExitMetadata> {
        calls++
        check(maxNum == 0) // filter the retained package buffer before limiting
        return (1L..60L).map { AppExitMetadata(MAIN, 4, 100, it) }
    }
    for (limit in listOf(0, -1, 51, Int.MAX_VALUE)) {
        val result = AppExitHistoryPolicy.history(37, MAIN, limit, ::read)
        check(result["error"] == "invalidLimit")
        check(entries(result).isEmpty())
    }
    val unsupported = AppExitHistoryPolicy.history(29, MAIN, 10, ::read)
    check(unsupported["supported"] == false && unsupported["error"] == null)
    check(calls == 0)
    for (limit in listOf(1, 10, 50)) {
        val result = AppExitHistoryPolicy.history(30, MAIN, limit, ::read)
        check(result["supported"] == true && result["error"] == null)
        check(entries(result).size == limit)
    }
    check(calls == 3)
}

private fun mainProcessHistory() {
    val source = listOf(
        AppExitMetadata(MAIN, 1, 100, 100),
        AppExitMetadata("$MAIN:worker", 4, 100, 999),
        AppExitMetadata(null, 4, 100, 998),
        AppExitMetadata("other.package", 4, 100, 997),
        AppExitMetadata(MAIN, 3, 200, 300),
        AppExitMetadata(MAIN, 10, 125, 200),
        AppExitMetadata(MAIN, 4, 100, 0),
    )
    val first = AppExitHistoryPolicy.history(37, MAIN, 2) { source }
    val second = AppExitHistoryPolicy.history(37, MAIN, 2) { source }
    check(first == second) // history never consumes the records
    check(entries(first).map { it["timestamp"] } == listOf(300L, 200L))
    check(entries(first).map { it["reason"] } == listOf(3, 10))
    check(source.size == 7)
    val empty = AppExitHistoryPolicy.history(37, MAIN, 10) { emptyList() }
    check(empty["supported"] == true && empty["error"] == null)
    check(entries(empty).isEmpty())
}

private fun safeSummaries() {
    check(AppExitHistoryPolicy.subReason(NumericSubReason()) == 25)
    check(AppExitHistoryPolicy.subReason(BlockedSubReason()) == -1)
    check(AppExitHistoryPolicy.subReason(Any()) == -1)
    val cases = listOf(
        Triple(0, -1, "App closed"),
        Triple(1, -1, "App closed"),
        Triple(15, -1, "App updated"),
        Triple(16, -1, "App updated"),
        Triple(10, 25, "App updated"),
        Triple(10, 21, "App stopped"),
        Triple(11, -1, "App stopped"),
        Triple(3, -1, "Phone needed memory"),
        Triple(4, -1, "App stopped unexpectedly"),
        Triple(5, -1, "App stopped unexpectedly"),
        Triple(6, -1, "App stopped unexpectedly"),
        Triple(7, -1, "App stopped unexpectedly"),
        Triple(2, 6, "Phone needed memory"),
        Triple(13, 22, "App stopped"),
        Triple(13, 25, "App updated"),
        Triple(9, -1, "Android ended the app"),
    )
    for ((reason, subReason, summary) in cases) {
        val result = AppExitHistoryPolicy.history(37, MAIN, 1) {
            listOf(AppExitMetadata(MAIN, reason, 100, 100, subReason))
        }
        val entry = entries(result).single()
        check(entry.keys == setOf("reason", "importance", "timestamp", "subReason", "status", "description"))
        check(entry["description"] == summary && summary.length <= 64)
        check(!result.toString().contains(MAIN))
        check(!result.toString().contains(SECRET))
    }
}

private fun readFailure() {
    val failed = AppExitHistoryPolicy.history(37, MAIN, 10) {
        throw IllegalStateException(SECRET)
    }
    check(failed["supported"] == true)
    check(failed["error"] == "unavailable")
    check(entries(failed).isEmpty())
    check(!failed.toString().contains(SECRET))
}

fun main(args: Array<String>) {
    when (args.single()) {
        "query-bounds" -> queryBounds()
        "main-process-history" -> mainProcessHistory()
        "safe-summaries" -> safeSummaries()
        "read-failure" -> readFailure()
        else -> error("Unknown harness scenario")
    }
    println("PASS ${args.single()}")
}
