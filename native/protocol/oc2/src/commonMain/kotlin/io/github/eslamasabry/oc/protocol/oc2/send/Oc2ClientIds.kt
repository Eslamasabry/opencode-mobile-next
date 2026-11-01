package io.github.eslamasabry.oc.protocol.oc2.send

import kotlin.random.Random
import kotlin.time.Clock

/**
 * Client ids for prompts and sessions (plan 04 §7.13), exactly Flutter's `createCommandReceiptID`
 * (`lib/domain/command_receipts.dart:150-159`): OpenCode orders messages and sessions by id, so the id must have the
 * server's sortable shape: prefix, 12 hex digits of `(ms * 4096) & 0xffffffffffff` (sessions XOR-ed so the newest sorts
 * first), then 14 base62 characters. 30 characters in all.
 *
 * The offline queue's `queue:<entryId>` is only the local journal's command id and never reaches the wire (B-PROTO-140).
 * [random] is `Random.secure`-backed in production (the app graph binds it); tests pass a seeded one.
 */
public class Oc2ClientIds(private val clock: Clock, private val random: Random) {
    public fun message(): String = id("msg", descending = false)

    public fun session(): String = id("ses", descending = true)

    private fun id(prefix: String, descending: Boolean): String {
        var t = (clock.now().toEpochMilliseconds() * 4096) and MASK
        if (descending) t = MASK xor t
        val time = t.toString(16).padStart(12, '0')
        val tail = CharArray(TAIL) { ALPHABET[random.nextInt(ALPHABET.length)] }
        return "${prefix}_$time${tail.concatToString()}"
    }

    public companion object {
        private const val MASK = 0xffffffffffffL
        private const val TAIL = 14
        internal const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        private val SHAPE = Regex("^(msg|ses)_[0-9a-f]{12}[0-9A-Za-z]{14}$")

        /** True for an id this class could have made. */
        public fun isClientId(id: String): Boolean = SHAPE.matches(id)
    }
}
