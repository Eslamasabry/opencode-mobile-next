package io.github.eslamasabry.oc.protocol.oc2.fixtures

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Oc2FixtureManifestTest {
    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private val manifest: Map<String, String> =
        File(Fixtures.root, "MANIFEST.sha256").readLines().filter { it.isNotBlank() }.associate { line ->
            val (hash, path) = line.split(Regex("\\s+"), limit = 2)
            path.removePrefix("*") to hash
        }

    @Test
    fun `B-PROTO-027 every copied OpenCode 2 fixture matches its SHA-256 manifest entry and nothing is unlisted`() {
        val api2 = manifest.keys.filter { it.startsWith("flutter/api2/") }
        assertTrue(api2.size >= 24, "expected the 24 Flutter api2 fixtures, found ${api2.size}")
        for ((path, hash) in manifest) assertEquals(hash, sha256(File(Fixtures.root, path)), path)
        val onDisk =
            File(Fixtures.root, "flutter").walkTopDown().filter { it.isFile }
                .map { it.relativeTo(Fixtures.root).invariantSeparatorsPath }.toSet()
        assertEquals(manifest.keys, onDisk)
    }

    @Test
    fun `B-PROTO-027 the copies are byte-identical to the Flutter originals under test fixtures`() {
        for (path in manifest.keys) {
            val original = File(Fixtures.repoRoot, "test/fixtures/" + path.removePrefix("flutter/"))
            if (!original.isFile) continue // the Flutter tree may be gone after the switch; the manifest still holds
            assertEquals(sha256(original), manifest.getValue(path), "drifted from ${original.path}")
        }
    }
}
