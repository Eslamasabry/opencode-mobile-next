package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Ports test/credential_ingress_redaction_test.dart (B-SEC-055, B-SEC-056). */
class CredentialIngressRedactionTest {
    private val secret = listOf("stored", "credential", "canary", "0042").joinToString("-")
    private val changed = listOf("changed", "credential", "canary", "0043").joinToString("-")

    @Test
    fun `B-SEC-055 a credential loaded from storage is masked in persisted diagnostics and in reports`() = runTest {
        val h = Harness(StandardTestDispatcher(testScheduler), FakeSecrets(mapOf("pw.server-1" to secret)))
        h.seed(
            "oc.profiles" to """[{"id":"server-1","name":"Workstation","baseUrl":"https://server.example:4096","username":"dev"}]""",
        )
        val loaded = assertIs<Outcome.Ok<List<LoadedProfile>>>(h.store.load()).value.single()
        assertTrue(loaded.hasSecret)

        val line = "Rejected credential $secret"
        assertFalse(secret in h.redactor.redact(line), "loaded credential reached the log")
        assertFalse(secret in h.redactor.diagnostics(line), "loaded credential reached persisted diagnostics")
        assertFalse(secret in h.redactor.details(line).text, "loaded credential reached the report")
    }

    @Test
    fun `B-SEC-056 an updated credential is registered before secure storage can fail`() = runTest {
        var maskedAtWrite = false
        val secrets = FakeSecrets()
        val h = Harness(StandardTestDispatcher(testScheduler), secrets)
        secrets.beforeWrite = { maskedAtWrite = changed !in h.redactor.redact("Rejected $changed") }
        secrets.failWrites = true
        val profile = StoredProfile(id("server-2"), "Workstation", "https://server.example:4096", username = "dev")

        assertIs<Failure.SecretUnavailable>(assertIs<Outcome.Failed>(h.store.upsert(profile, SecretEdit.Set(changed))).failure)
        assertTrue(maskedAtWrite, "registration must precede failing storage")
        assertFalse(changed in h.redactor.redact("after the refusal: $changed"))
    }
}
