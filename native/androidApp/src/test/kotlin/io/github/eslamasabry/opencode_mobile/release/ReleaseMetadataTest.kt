package io.github.eslamasabry.opencode_mobile.release

import io.github.eslamasabry.oc.engine.updates.AppRelease
import io.github.eslamasabry.oc.engine.updates.ReleaseTags
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** `test/release_metadata_test.dart` (B-BUILD-028) and `desktop_packaging_contract_test.dart` (B-BUILD-007). */
class ReleaseMetadataTest {
    private val version = "1.2.0+52"
    private val properties = "# ADR §9.9\nversionName=1.2.0\nversionCode=52\n"
    private val changelog = "# Changelog\n\n## $version — 2026-10-07\n\nNotes.\n"
    private val notes = "# OpenCode Mobile $version\n\nRelease notes.\n"

    private fun validate(props: String = properties, changes: String = changelog, releaseNotes: String? = notes) =
        ReleaseMetadata.validate(props, changes, releaseNotes)

    private fun List<String>.mentions(text: String) = any { it.contains(text) }

    // ---- B-BUILD-007 ------------------------------------------------------------------------------------------

    @Test
    fun `B-BUILD-007 the app version carries a build number the update check can read`() {
        val props = Properties().apply { RepoFiles.native("version.properties").inputStream().use(::load) }
        val name = props.getProperty("versionName")
        val code = props.getProperty("versionCode")
        assertTrue(Regex("""^\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?$""").matches(name), name)
        assertTrue(Regex("""^\d+$""").matches(code), code)
        assertTrue(code.toInt() >= 10_000, "native builds number from 10000 so they sort above every Flutter build")
        // The build reads exactly these two keys into versionName / versionCode.
        val plugin = RepoFiles.text("build-logic/convention/src/main/kotlin/oc/buildlogic/OcAndroidApplicationPlugin.kt")
        assertTrue(plugin.contains("getProperty(\"versionCode\")"))
        assertTrue(plugin.contains("getProperty(\"versionName\")"))
    }

    @Test
    fun `B-BUILD-007 a release tag for this version parses back to this build`() {
        val props = Properties().apply { RepoFiles.native("version.properties").inputStream().use(::load) }
        val name = props.getProperty("versionName")
        val build = props.getProperty("versionCode").toInt()
        val tag = assertNotNull(ReleaseTags.tagFor(name, build))
        assertEquals(build, AppRelease.buildOf(tag))
        assertEquals(build, AppRelease.buildOf("$tag-preview.9"))
        assertFalse(ReleaseTags.isNewer(build, tag), "the running build is never newer than itself")
        assertTrue(ReleaseTags.isNewer(build - 1, tag), "and the next one is")
    }

    // ---- B-BUILD-028 ------------------------------------------------------------------------------------------

    @Test
    fun `B-BUILD-028 matching metadata passes and older releases below the first are ignored`() {
        assertEquals(emptyList(), validate())
        assertEquals(emptyList(), validate(changes = "$changelog\n## 1.1.0+51 — 2026-09-01\n"))
        assertEquals(version, ReleaseMetadata.releaseVersion(properties))
    }

    @Test
    fun `B-BUILD-028 an Unreleased section may come first, but only one`() {
        assertEquals(emptyList(), validate(changes = "## Unreleased\n\nPending changes.\n$changelog"))
        assertTrue(validate(changes = "## Unreleased\n## 1.1.0+51\n$changelog").mentions("CHANGELOG.md first release heading"))
    }

    @Test
    fun `B-BUILD-028 a build-number-only mismatch in the changelog is reported`() {
        assertTrue(validate(changes = changelog.replaceFirst(version, "1.2.0+51")).mentions("CHANGELOG.md first release heading"))
    }

    @Test
    fun `B-BUILD-028 the first entry is required, not a matching older one`() {
        assertTrue(validate(changes = "## 1.1.0+51\n\n$changelog").mentions("CHANGELOG.md first release heading"))
    }

    @Test
    fun `B-BUILD-028 a version prefix match is rejected`() {
        assertTrue(validate(changes = changelog.replaceFirst(version, "1.2.0+521")).isNotEmpty())
    }

    @Test
    fun `B-BUILD-028 a missing or unversioned first changelog heading is reported`() {
        for (changes in listOf("# Changelog\n", "## Unreleased\n", "## Unknown\n$changelog")) {
            assertTrue(validate(changes = changes).isNotEmpty(), changes)
        }
    }

    @Test
    fun `B-BUILD-028 missing release notes name the expected path`() {
        assertTrue(validate(releaseNotes = null).mentions("docs/releases/v$version.md"))
    }

    @Test
    fun `B-BUILD-028 a build-number-only mismatch in the release header is reported`() {
        assertTrue(validate(releaseNotes = notes.replaceFirst(version, "1.2.0+51")).mentions("must start with # OpenCode Mobile $version"))
    }

    @Test
    fun `B-BUILD-028 the exact first-line release header is required`() {
        for (bad in listOf("", "\n$notes", "Introduction\n$notes", "# OpenCode Mobile $version - Preview\n", "# OpenCode Mobile ${version}0\n")) {
            assertTrue(validate(releaseNotes = bad).isNotEmpty(), bad)
        }
    }

    @Test
    fun `B-BUILD-028 a malformed or missing release identity is reported`() {
        for (props in listOf(
            "versionCode=52\n",
            "versionName=1.2.0\n",
            "versionName=1.2.0\nversionCode=invalid\n",
            "versionName=1.2.0\nversionCode=52suffix\n",
            "versionName=1.2\nversionCode=52\n",
            "versionName=3.0.0-dev\nversionCode=10003\n",
        )) {
            assertEquals(listOf(ReleaseMetadata.BAD_IDENTITY), validate(props = props), props)
        }
    }

    @Test
    fun `B-BUILD-028 CRLF metadata and trailing comments are supported`() {
        assertEquals(
            emptyList(),
            validate(
                props = "versionName=1.2.0 # current release\r\nversionCode = 52 # build\r\n",
                changes = changelog.replace("\n", "\r\n"),
                releaseNotes = notes.replace("\n", "\r\n"),
            ),
        )
    }
}
