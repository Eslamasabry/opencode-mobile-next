package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * E00-S13 / ADR §9.9 (no catalog row yet; the story id names it): a person updating from the Flutter app keeps their
 * widgets, tiles, shortcuts, alarms, scheduled work and notification settings, because every name Android binds to is
 * the one the Flutter 2.1.4 release used (`quality/config/component-freeze/flutter-2.1.4.tsv`).
 *
 * Runs in every `:quality:test` against the shipped modules' source manifests, and at T2 (step `component-freeze`)
 * against the merged manifest AGP writes for the same-package release, passed as `-Poc.mergedManifest=required`.
 */
class ComponentFreezeTest {
    private val root = File(System.getProperty("oc.root") ?: ".").absoluteFile
    private val dir = File(root, "quality/config/component-freeze")
    private val frozen = ComponentFreeze.readList(File(dir, "flutter-2.1.4.tsv"))

    /** `kind<TAB>name<TAB>owner and reason`: a known gap with a story, tolerated until fixed (the freeze-pending pattern). */
    private val pending = ComponentFreeze.readList(File(dir, "pending.tsv"))

    private fun shipped(): ComponentFreeze.Shipped {
        val merged =
            when (val asked = System.getProperty("oc.mergedManifest").orEmpty()) {
                "" -> null
                "required" ->
                    ComponentFreeze.findMergedManifest(root)
                        ?: error("oc.mergedManifest=required but androidApp/build has no switchRelease merged manifest")
                else -> File(asked).also { check(it.isFile) { "oc.mergedManifest: no file at $asked" } }
            }
        return ComponentFreeze.shipped(root, merged)
    }

    @Test
    fun `E00-S13 every name Android binds to is still the Flutter 2_1_4 name`() {
        val shipped = shipped()
        val waived = pending.map { it.kind to it.name }.toSet()
        val problems = frozen.filter { (it.kind to it.name) !in waived }.mapNotNull { e ->
            ComponentFreeze.check(e, shipped)?.let { "${e.kind} ${e.name}: $it" }
        }
        assertTrue(
            problems.isEmpty(),
            "Names frozen from the Flutter app changed (an update would break what they name):\n" + problems.joinToString("\n"),
        )
    }

    @Test
    fun `E00-S13 a pending gap is still a gap and is on the frozen list`() {
        val shipped = shipped()
        val keys = frozen.map { it.kind to it.name }.toSet()
        val stale =
            pending.mapNotNull { p ->
                when {
                    (p.kind to p.name) !in keys -> "${p.kind} ${p.name}: not on the frozen list"
                    p.detail.isBlank() -> "${p.kind} ${p.name}: a pending line needs its owner and reason"
                    ComponentFreeze.check(frozen.first { it.kind == p.kind && it.name == p.name }, shipped) == null ->
                        "${p.kind} ${p.name}: fixed; remove it from pending.tsv"
                    else -> null
                }
            }
        assertTrue(stale.isEmpty(), stale.joinToString("\n"))
    }

    @Test
    fun `E00-S13 the frozen list covers every kind the ADR names`() {
        val kinds = frozen.map { it.kind }.toSet()
        for (k in listOf("component", "widget", "tile", "shortcut", "scheme", "channel", "worker", "work-name", "authority")) {
            assertTrue(k in kinds, "the freeze list has no '$k' line")
        }
        assertTrue(frozen.size == frozen.distinct().size, "duplicate lines in the freeze list")
    }
}
