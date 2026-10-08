package io.github.eslamasabry.opencode_mobile

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NativeServerRecipeTest {
    private val generation = "0123456789abcdef".repeat(4)
    private val safeError = "The phone server could not restart."

    @Test
    fun requestAndStoredRecipeRoundTripWithoutLaunchInput() {
        for (runtime in listOf("openCode1", "openCode2")) {
            val recipe = request(runtime = runtime)
            assertEquals(mapOf(
                "version" to 1,
                "profileId" to "phone_1-A",
                "runtime" to runtime,
                "packageVersion" to 2201L,
                "rootfsGeneration" to generation,
            ), recipe.map())
            assertEquals(recipe, NativeServerRecipe.read(recipe.map()))
        }
    }

    @Test
    fun requestMustMatchCurrentProfileBinding() {
        for (bound in listOf(null, "", "other-profile", "Phone_1-A")) {
            rejects { request(bound = bound) }
        }
    }

    @Test
    fun profileIdentifiersAreBoundedAsciiNames() {
        assertEquals("A".repeat(80), request(profile = "A".repeat(80)).profileId)
        assertEquals("_", request(profile = "_").profileId)
        for (profile in listOf("", "A".repeat(81), "../root", "a/b", "a.b", "a b",
            "a\n", "a\u0000", "هاتف", "a;touch /tmp/injected", "\$(id)")) {
            rejects { request(profile = profile) }
            rejects { NativeServerRecipe.read(stored("profileId" to profile)) }
        }
    }

    @Test
    fun onlyKnownRuntimeNamesAreAccepted() {
        for (runtime in listOf<Any?>(null, "", "OpenCode1", "opencode", "openCode3",
            "openCode2;echo secret", 1, true)) {
            rejects { request(value = requested("runtime" to runtime)) }
            rejects { NativeServerRecipe.read(stored("runtime" to runtime)) }
        }
    }

    @Test
    fun schemaVersionRequiresIntOrLongOne() {
        assertEquals(request(), request(value = requested("version" to 1L)))
        assertEquals(request(), NativeServerRecipe.read(stored("version" to 1L)))
        for (version in listOf<Any?>(null, 0, 2L, "1", 1.0, 1.0f,
            1.toShort(), 1.toByte(), BigInteger.ONE, true)) {
            rejects { request(value = requested("version" to version)) }
            rejects { NativeServerRecipe.read(stored("version" to version)) }
        }
    }

    @Test
    fun packageVersionMustBeAPositiveStrictInteger() {
        assertEquals(1L, NativeServerRecipe.read(stored("packageVersion" to 1)).packageVersion)
        assertEquals(Long.MAX_VALUE,
            NativeServerRecipe.read(stored("packageVersion" to Long.MAX_VALUE)).packageVersion)
        for (version in listOf<Any?>(null, 0, -1L, "2201", 2201.0, 2201.0f,
            1.toShort(), 1.toByte(), BigInteger.ONE, true)) {
            rejects { NativeServerRecipe.read(stored("packageVersion" to version)) }
        }
        for (version in listOf(0L, -1L, Long.MIN_VALUE)) {
            rejects { request(packageVersion = version) }
        }
    }

    @Test
    fun rootfsGenerationRequiresExactLowercaseSha256Shape() {
        for (rootfs in listOf<Any?>(null, "", "a".repeat(63), "a".repeat(65),
            "A".repeat(64), "g".repeat(64), "a".repeat(63) + "\n", 123L)) {
            rejects { NativeServerRecipe.read(stored("rootfsGeneration" to rootfs)) }
            if (rootfs is String) rejects { request(rootfsGeneration = rootfs) }
        }
    }

    @Test
    fun compatibilityRequiresBothExactPackageAndRootfsIdentity() {
        val recipe = request()
        assertTrue(recipe.compatible(2201L, generation))
        assertFalse(recipe.compatible(2200L, generation))
        assertFalse(recipe.compatible(2202L, generation))
        assertFalse(recipe.compatible(2201L, "f".repeat(64)))
        assertFalse(recipe.compatible(0L, generation))
        assertFalse(recipe.compatible(2201L, generation.uppercase()))
    }

    @Test
    fun missingKeysAndNonStringKeysFailClosed() {
        for (key in requested().keys) {
            rejects { request(value = requested().toMutableMap().apply { remove(key) }) }
        }
        for (key in stored().keys) {
            rejects { NativeServerRecipe.read(stored().toMutableMap().apply { remove(key) }) }
        }
        rejects { request(value = requested() + mapOf(1 to "unknown")) }
        rejects { NativeServerRecipe.read(stored() + mapOf(null to "unknown")) }
        for (profile in listOf<Any?>(null, 123L, true)) {
            rejects { request(value = requested("profileId" to profile)) }
            rejects { NativeServerRecipe.read(stored("profileId" to profile)) }
        }
    }

    @Test
    fun requestCannotOverrideNativeCompatibilityMetadata() {
        for ((key, value) in listOf("packageVersion" to 2201L, "rootfsGeneration" to generation)) {
            rejects { request(value = requested() + (key to value)) }
        }
    }

    @Test
    fun arbitraryLaunchAndCredentialPayloadsAreRejectedWithSafeErrors() {
        for ((key, value) in listOf(
            "script" to "echo secret-value",
            "password" to "secret-value",
            "token" to "secret-value",
            "credentials" to mapOf("apiKey" to "secret-value"),
            "port" to 9000,
            "hostname" to "0.0.0.0",
            "passwordFile" to "/tmp/secret-value",
            "environment" to mapOf("OPENCODE_PASSWORD" to "secret-value"),
            "futureField" to true,
        )) {
            rejects { request(value = requested() + (key to value)) }
            rejects { NativeServerRecipe.read(stored() + (key to value)) }
        }
    }

    @Test
    fun dataClassConstructionAndCopyCannotBypassValidation() {
        rejects { request().copy(profileId = "../escape") }
        rejects { request().copy(runtime = "arbitrary command") }
        rejects { request().copy(packageVersion = 0L) }
        rejects { request().copy(rootfsGeneration = "unknown") }
    }

    @Test
    fun coldOpenCodeOneUsesOnlyPrivatePasswordReferenceAndFixedLoopbackPort() {
        assertEquals(commonScriptPrefix() + commonScriptSuffix("opencode"),
            request().restorationScript())
    }

    @Test
    fun coldOpenCodeTwoKeepsIsolatedPathsAndDoesNotRewriteContext() {
        val script = request(runtime = "openCode2").restorationScript()
        assertEquals(commonScriptPrefix() +
            "mkdir -p /root/.oc-opencode2/data/opencode /root/.oc-opencode2/cache " +
            "/root/.oc-opencode2/state /root/.oc-opencode2/config/opencode\n" +
            "unset OPENCODE_CONFIG OPENCODE_CONFIG_CONTENT\n" +
            "export XDG_DATA_HOME=/root/.oc-opencode2/data\n" +
            "export XDG_CACHE_HOME=/root/.oc-opencode2/cache\n" +
            "export XDG_STATE_HOME=/root/.oc-opencode2/state\n" +
            "export XDG_CONFIG_HOME=/root/.oc-opencode2/config\n" +
            "export OPENCODE_CONFIG_DIR=/root/.oc-opencode2/config/opencode\n" +
            "export OPENCODE_DB=/root/.oc-opencode2/data/opencode/opencode.db\n" +
            commonScriptSuffix("opencode2"), script)
        assertFalse(script.contains("AGENTS.md"))
        assertFalse(script.contains("PhoneAgentContext"))
        assertFalse(script.contains("phone_1-A"))
        assertFalse(script.contains(generation))
    }

    private fun commonScriptPrefix(): String =
        "set -eu\nmkdir -p /root/projects\ncd /root/projects\n" +
            "[ -s /root/.oc-builtin/server.password ] || { " +
            "echo '$safeError' >&2; exit 78; }\n"

    private fun commonScriptSuffix(binary: String): String =
        "password=\$(cat /root/.oc-builtin/server.password)\n" +
            "export OPENCODE_SERVER_USERNAME=opencode\n" +
            "export OPENCODE_SERVER_PASSWORD=\"\$password\"\n" +
            "export OPENCODE_PASSWORD=\"\$password\"\n" +
            "unset password\n" +
            "exec $binary serve --hostname 127.0.0.1 --port 4097\n"

    private fun request(
        profile: String = "phone_1-A",
        runtime: String = "openCode1",
        bound: String? = profile,
        packageVersion: Long = 2201L,
        rootfsGeneration: String = generation,
        value: Map<*, *> = requested("profileId" to profile, "runtime" to runtime),
    ): NativeServerRecipe =
        NativeServerRecipe.fromRequest(value, bound, packageVersion, rootfsGeneration)

    private fun requested(vararg changes: Pair<String, Any?>): Map<String, Any?> =
        mapOf<String, Any?>("version" to 1, "profileId" to "phone_1-A", "runtime" to "openCode1") + changes

    private fun stored(vararg changes: Pair<String, Any?>): Map<String, Any?> =
        requested() + mapOf("packageVersion" to 2201L, "rootfsGeneration" to generation) + changes

    private fun rejects(action: () -> Unit) {
        try {
            action()
            fail("Invalid recipe was accepted")
        } catch (error: IllegalArgumentException) {
            assertEquals(safeError, error.message)
            assertEquals(IllegalArgumentException::class.java, error.javaClass)
        }
    }
}
