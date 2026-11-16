package io.github.eslamasabry.oc.quality.sec

import java.io.File

/** Explicit known findings remain detected. New findings, changed counts and stale entries fail the suite. */
object TrackedSecurityFailures {
    data class Entry(val backlog: String, val assertion: String, val diagnostic: String, val count: Int)

    fun read(file: File): List<Entry> = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
        val fields = line.split('|').map { it.trim() }
        require(fields.size == 4 && fields[0].matches(Regex("SEC301-[A-Z-]+"))) { "Invalid tracked-security entry" }
        require(fields[1] in setOf("K-SEC-2", "K-SEC-4", "K-SEC-10", "K-SEC-12")) { "Unreviewed tracked assertion" }
        require(fields[2].startsWith(fields[1] + " ") && fields[3].toInt() > 0) { "Invalid exact finding" }
        Entry(fields[0], fields[1], fields[2], fields[3].toInt())
    }.also { entries -> require(entries.map { it.diagnostic }.distinct().size == entries.size) { "Duplicate tracked finding" } }

    fun remaining(violations: List<Violation>, rule: String, entries: List<Entry>): List<String> {
        fun key(v: Violation) = "${v.rule} ${v.file} ${v.what}"
        val expected = entries.filter { it.assertion == rule }.associateBy { it.diagnostic }
        val counts = violations.groupingBy(::key).eachCount()
        val changed = expected.values.filter { counts[it.diagnostic] != it.count }.map {
            "${it.backlog}: ${it.diagnostic}: expected ${it.count} known finding(s), found ${counts[it.diagnostic] ?: 0}; fix the debt or review the new violation"
        }
        return violations.filter { key(it) !in expected }.map { it.toString() } + changed
    }
}
