package io.github.eslamasabry.opencode_mobile

/** Agent setup checks return receipts only, never raw CLI output or credentials. */
internal object PhoneAgentCheckOutput {
    private val receipt = Regex("^::oc-check-(?:begin agent-[a-z0-9][a-z0-9_-]{0,79}|end agent-[a-z0-9][a-z0-9_-]{0,79} [0-9]{1,3})$")
    fun accept(line: String): String? = line.takeIf { receipt.matches(it) }
}
