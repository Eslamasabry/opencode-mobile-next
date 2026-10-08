package io.github.eslamasabry.opencode_mobile

fun main(args: Array<String>) {
    val scenario = args.single()
    check(scenario in setOf("callback-success", "callback-failure"))
    var actionRan = false
    var frameworkCompleted = false
    val result = runCatching {
        Bb8MainCallback.run(
            sync = { callback ->
                callback.run()
                frameworkCompleted = true
            },
            action = {
                actionRan = true
                if (scenario == "callback-failure") error("Synthetic callback failure")
            },
        )
    }
    check(actionRan) { "Authored callback did not run" }
    check(frameworkCompleted) { "Callback prevented framework completion" }
    check(result.isSuccess) { "Callback exception escaped its Runnable" }
    check(result.getOrThrow() == (scenario == "callback-success")) {
        "Callback result did not reflect action success"
    }
    println("PASS $scenario")
}
