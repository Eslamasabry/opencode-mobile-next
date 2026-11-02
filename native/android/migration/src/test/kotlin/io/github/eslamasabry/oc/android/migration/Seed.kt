package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref

/** A small, fully synthetic phone. Every value is made up; no address is a private-network one. */
internal object Seed {
    const val REMOTE = "1735000000000001" // OpenCode 2 server with a typed password
    const val CODEX = "1735000000000002" // Codex profile with a token
    const val PHONE = "1735000000000003" // the built-in phone server

    const val PASSWORD = "fixture-pass-remote-1"
    const val TOKEN = "fixture-codex-token-1"
    const val PROMPT_TEXT = "please refactor the fixture parser"
    const val SERVER_URL = "https://server.example.test:4097"

    val profiles: String = MiniJson.encode(
        listOf(
            linkedMapOf("id" to REMOTE, "name" to "Work", "baseUrl" to SERVER_URL, "backend" to "openCode", "username" to "opencode",
                "flavor" to "v2", "requiresOpenCode2Migration" to false, "openCode2HistoryPending" to false),
            linkedMapOf("id" to CODEX, "name" to "Codex", "baseUrl" to "wss://codex.example.test", "backend" to "codex", "username" to "",
                "codexDirectory" to "/work", "flavor" to "v2", "requiresOpenCode2Migration" to false, "openCode2HistoryPending" to false),
            linkedMapOf("id" to PHONE, "name" to "This phone", "baseUrl" to "http://localhost:4096", "backend" to "openCode", "username" to "opencode",
                "flavor" to "v1", "requiresOpenCode2Migration" to false, "openCode2HistoryPending" to true),
        ),
    )

    val queue: String = MiniJson.encode(
        listOf(
            linkedMapOf("id" to "q1", "profileID" to REMOTE, "sessionID" to "ses_1", "text" to PROMPT_TEXT, "createdAt" to 1730457000000L),
            linkedMapOf("id" to "q2", "profileID" to REMOTE, "sessionID" to "ses_1", "text" to "second", "createdAt" to 1730457000001L,
                "dispatchedAt" to 1730457000500L),
        ),
    )

    val prefs: Map<String, RawPref> = linkedMapOf(
        "oc.profiles" to s(profiles),
        "oc.activeProfile" to s(REMOTE),
        "oc.builtinServerOwner" to s(PHONE),
        "oc.offlineQueue" to s(queue),
        "oc.appLocale" to s("ar"),
        "oc.effectsMotion" to s("calm"),
        "oc.textScale" to RawPref.Str("VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu1.2"),
        "oc.sessionPins.$REMOTE" to s("""{"[\"/work\",null]":["ses_1"]}"""),
        "oc.budgets.$REMOTE" to s("""{"daily":5}"""),
        "oc.glassLiquidStrikes" to l(2),
        "oc.notifyRules.$REMOTE" to s("""{"enabled":true,"notifications":true,"wifiOnly":true,"quietStart":1320,"quietEnd":420}"""),
        "oc.shareSessionViews" to b(false),
        "oc.builtinRecovery.$PHONE" to s("""{"version":2,"nativeAuthority":true}"""),
        "oc.automation.$PHONE" to s("""{"version":1,"supervision":"balanced","behaviors":{"restartPhoneServer":true,"pollRestartHealth":true}}"""),
        "oc.phoneAgentOwner.$PHONE" to s(PHONE),
        "oc.keepLiveInBackground" to b(true),
        "oc.unknownFutureKey" to s("kept as is"),
    )

    val secrets: Map<String, String> = linkedMapOf(
        "pw.$REMOTE" to PASSWORD,
        "oc.codexToken.$CODEX" to TOKEN,
        "oc.githubToken.$REMOTE" to """{"accessToken":"gho_fixtureAccessToken","refreshToken":null}""",
        "oc.byoHostSecrets.$CODEX" to """{"schema":2,"keyAlias":"oc.byoHostSsh.$CODEX","publicKey":"ssh-ed25519 AAAAfixture","deviceToken":"dev-fixture-token"}""",
        "pw.$PHONE" to "fixture-phone-generated",
        "oc.agentHostSecret.$PHONE" to "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "oc.quotaToken.$REMOTE" to "fixture-quota-token",
        "oc.teamEngineAuth.$REMOTE" to "fixture-team-bearer",
        "oc.factsbook.$REMOTE" to "fixture-factsbook-token",
        "oc.a2aBearer.agent-7" to "fixture-a2a-bearer",
    )

    fun phone(world: World, corrupt: Set<String> = emptySet(), prefs: Map<String, RawPref> = Seed.prefs) {
        world.seedPrefs(prefs)
        world.seedSecrets(secrets, corrupt)
    }
}
