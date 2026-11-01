package io.github.eslamasabry.oc.quality.sec

import kotlin.test.Test

/**
 * K-SEC-1…12 (plan 16 §2.1) on the live native tree. Infra tests (03 K12 exempt): named after the rule ids; the
 * planted proofs are in [KSecPlantedTest].
 */
class KSecRulesTest {
    private val files get() = Live.files
    private val config get() = Live.config

    /** Live hits minus the reported ones in `ksec-pending.txt`; a pending key of this rule that no longer fires fails. */
    private fun assertClean(violations: List<Violation>, rule: String) {
        val open = violations.filterNot { it.key in config.ksecPending }
        val stale = config.ksecPending.filter { it.startsWith("$rule ") && violations.none { v -> v.key == it } }.map { "$it: fixed, remove it from ksec-pending.txt" }
        io.github.eslamasabry.oc.quality.sec.assertClean(open + stale, rule)
    }

    @Test fun `K-SEC-1 no autoLink or Linkify, LocalUriHandler provided only at the shell root`() = assertClean(SecTextRules.kSec1(files), "K-SEC-1")

    @Test fun `K-SEC-2 custom TLS trust only in core net-okhttp`() = assertClean(SecTextRules.kSec2(files), "K-SEC-2")

    @Test fun `K-SEC-3 no certificate pinner, logging interceptor, Ktor Logging, retry or redirect`() = assertClean(SecTextRules.kSec3(files), "K-SEC-3")

    @Test fun `K-SEC-4 secret-named String properties of data and Serializable classes are Secret`() =
        assertClean(Live.declarations.kSec4(config) + SecTextRules.kSec4Reveal(files, config), "K-SEC-4")

    @Test fun `K-SEC-5 no Secret in route keys, saved state, work data, intents or bundles`() =
        assertClean(Live.declarations.kSec5Routes() + SecTextRules.kSec5Calls(files), "K-SEC-5")

    @Test fun `K-SEC-6 clipboard writes only in the kit KitCopy and KitPlatform`() = assertClean(SecTextRules.kSec6(files), "K-SEC-6")

    @Test fun `K-SEC-7 every PendingIntent is FLAG_IMMUTABLE outside the three reviewed files`() = assertClean(SecTextRules.kSec7(files, config), "K-SEC-7")

    @Test fun `K-SEC-8 no parseUri, getIntent or relaunched intents from extras`() = assertClean(SecTextRules.kSec8(files), "K-SEC-8")

    @Test fun `K-SEC-9 WebView only in KitWebPreview and never with a bridge, file access or proceed`() = assertClean(SecTextRules.kSec9(files), "K-SEC-9")

    @Test fun `K-SEC-10 every URL literal names a KnownHosts host or loopback`() = assertClean(SecTextRules.kSec10(files, config), "K-SEC-10")

    @Test fun `K-SEC-11 analytics event properties are enums, Boolean or buckets`() = assertClean(Live.declarations.kSec11(), "K-SEC-11")

    @Test fun `K-SEC-12 no kotlin or java Random where tokens and passwords are made`() = assertClean(SecTextRules.kSec12(files), "K-SEC-12")

    @Test fun `K-SEC the declaration scope is not empty`() {
        val (data, events) = Live.declarations.seen()
        kotlin.test.assertTrue(data > 20 && events > 5, "Konsist saw $data data classes and $events analytics events; the scope is wrong")
        kotlin.test.assertTrue(files.count { it.rel.startsWith("android/") } > 5, "the android/ layer was not scanned")
    }

    @Test fun `K-SEC pending hosts are still used, so the list only shrinks`() {
        val known = SecTextRules.knownHosts(files)
        val urls = files.filter { it.isProduction && it.isKotlin }.joinToString("\n") { it.code }
        val stale = config.pendingKnownHosts.filter { it in known || "://$it" !in urls }
        io.github.eslamasabry.oc.quality.sec.assertClean(stale.map { "$it: remove it from knownhosts-pending.txt (KnownHosts lists it, or no literal uses it)" }, "knownhosts-pending.txt")
    }
}
