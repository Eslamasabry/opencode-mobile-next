package io.github.eslamasabry.opencode_mobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

private class OwnedProcess : Process() {
    var alive = true
    override fun getOutputStream() = ByteArrayOutputStream()
    override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
    override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
    override fun waitFor() = 0
    override fun exitValue(): Int { check(!alive); return 0 }
    override fun destroy() { alive = false }
    override fun isAlive() = alive
}

fun main(args: Array<String>) {
    val process = OwnedProcess()
    var token: Pair<Int, String>? = 80 to "100"
    var unreadable = false
    val owners = PhoneAgentAuthOtherOwners { check(!unreadable); token }
    when (args.single()) {
        "registered-helper" -> {
            check(owners.snapshot().isEmpty())
            owners.register(process, false)
            check(owners.snapshot() == setOf(80 to "100"))
        }
        "private-excluded" -> {
            owners.register(process, true)
            check(owners.snapshot().isEmpty())
        }
        "unknown-at-launch" -> {
            token = null
            owners.register(process, false)
            token = 80 to "100"
            check(owners.snapshot().isEmpty())
        }
        "dead-pruned" -> {
            owners.register(process, false)
            process.alive = false
            check(owners.snapshot().isEmpty())
            process.alive = true
            check(owners.snapshot().isEmpty())
        }
        "reused-pruned" -> {
            owners.register(process, false)
            token = 80 to "101"
            check(owners.snapshot().isEmpty())
            token = 80 to "100"
            check(owners.snapshot().isEmpty())
        }
        "unreadable-excluded" -> {
            owners.register(process, false)
            unreadable = true
            check(owners.snapshot().isEmpty())
        }
        "unreadable-launch" -> {
            unreadable = true
            owners.register(process, false)
            unreadable = false
            check(owners.snapshot().isEmpty())
        }
        "invalid-birth" -> {
            for (invalid in listOf(0 to "100", -1 to "100", 80 to "", 80 to "0", 80 to "bad")) {
                token = invalid
                owners.register(process, false)
                check(owners.snapshot().isEmpty())
            }
        }
        else -> error("Unknown scenario")
    }
}
