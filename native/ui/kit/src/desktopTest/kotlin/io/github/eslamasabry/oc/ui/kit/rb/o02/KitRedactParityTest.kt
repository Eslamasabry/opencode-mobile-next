package io.github.eslamasabry.oc.ui.kit.rb.o02

import kotlin.test.*
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import kotlinx.serialization.json.Json

class KitRedactParityTest {
    @BeforeTest fun before() = KitRedact.clearKnownSecrets()
    @AfterTest fun after() = KitRedact.clearKnownSecrets()
    @Test fun exactValuesAreLiteralCaseSensitiveLongestFirstAndSixCharactersMinimum() {
        KitRedact.registerKnownSecret("short")
        KitRedact.registerKnownSecret("sample")
        KitRedact.registerKnownSecret("sample-long")
        KitRedact.registerKnownSecret("a.b[cd]")
        assertEquals("short ••• ••• Sample •••", KitRedact.text("short sample-long sample Sample a.b[cd]"))
        KitRedact.clearKnownSecrets(); assertEquals("sample",KitRedact.text("sample"))
    }
    @Test fun credentialIngressFindsNestedNamesAndAuthorizationSuffix() {
        KitRedact.registerCredentialValues(Json.parseToJsonElement("""{"providers":[{"api_key":"opaque-example","Authorization":"Basic encoded-example"}]}"""))
        assertEquals("••• •••",KitRedact.text("opaque-example encoded-example"))
    }
    @Test fun allProviderPrefixesKeepTheirPublicHint() {
        for ((prefix,length) in listOf("sk-ant-" to 8,"sk-proj-" to 8,"sk-" to 16,"AIza" to 30,"github_pat_" to 20,"ghp_" to 20,"gho_" to 20,"ghs_" to 20,"ghu_" to 20,"ghr_" to 20,"xoxa-" to 10,"xoxb-" to 10,"xoxp-" to 10,"xoxr-" to 10)) {
            val token = prefix + "a".repeat(length)
            assertEquals(prefix+"•••",KitRedact.text(token))
            assertTrue(KitRedact.containsSecret(token))
        }
    }
    @Test fun headersNamedValuesPathsAndQuotes() {
        val cases = mapOf(
            "  Authorization: Basic example extra" to "  Authorization: •••",
            "Cookie=a=b; c=d" to "Cookie=•••",
            "Set-Cookie: a=b" to "Set-Cookie: •••",
            "Proxy-Authorization: example" to "Proxy-Authorization: •••",
            "x-api-key: example" to "x-api-key: •••",
            "Bearer example" to "Bearer •••",
            "password='a,b c' next" to "password='•••' next",
            "API_KEY=example; next" to "API_KEY=•••; next",
            "client_secret=example" to "client_secret=•••",
            "https://host/?token=example&ok=yes" to "https://host/?token=•••&ok=yes",
            "postgres://user:pass@host/db" to "postgres://•••@host/db",
            "/tmp/token=cache/x" to "/tmp/token=cache/x",
            "const token = createToken()" to "const token = createToken()",
            """-H "Authorization: Basic example"""" to """-H "Authorization: •••"""",
            """{"password":"two\" words"}""" to """{"password":"•••"}""",
        )
        for ((input,expected) in cases) assertEquals(expected,KitRedact.text(input))
    }
    @Test fun privateKeysJwtsAndOpaqueTokens() {
        assertEquals("•••",KitRedact.text("-----BEGIN PRIVATE KEY-----\nexample\n-----END PRIVATE KEY-----"))
        assertEquals("•••",KitRedact.text("eyJheader.eyJclaims.signature"))
        assertEquals("ordinary.long.words",KitRedact.text("ordinary.long.words"))
        assertEquals("•••",KitRedact.opaqueTokens("1"+"a".repeat(31)))
        assertEquals("a".repeat(40),KitRedact.opaqueTokens("a".repeat(40)))
        assertEquals("",KitRedact.text(""))
        assertFalse(KitRedact.containsSecret("/src/main.dart 8302de537"))
    }
}
