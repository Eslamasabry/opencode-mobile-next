package io.github.eslamasabry.oc.android.voice

import io.github.eslamasabry.oc.android.voice.device.ConnectedNetworkFacts
import io.github.eslamasabry.oc.android.voice.device.LinkKind
import io.github.eslamasabry.oc.android.voice.device.NetworkFacts
import io.github.eslamasabry.oc.android.voice.device.SetupNetworkRules
import io.github.eslamasabry.oc.android.voice.tts.SystemTtsRules
import io.github.eslamasabry.oc.android.voice.tts.VoiceInfo
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {
    private fun facts(
        manager: Boolean = true,
        network: Boolean = true,
        internet: Boolean = true,
        validated: Boolean = true,
        cellular: Boolean = false,
        vpn: Boolean = false,
        metered: Boolean = false,
        notMetered: Boolean = !metered,
    ) = NetworkFacts(manager, network, internet, validated, cellular, vpn, metered, notMetered)

    @Test
    fun `B-ANDR-101 network state is reported and missing or failed probes never allow free data`() {
        assertEquals(SetupNetwork.Unmetered, SetupNetworkRules.classify(facts()))
        assertEquals(SetupNetwork.Unknown, SetupNetworkRules.classify(null)) // a failed probe
        assertEquals(SetupNetwork.Unknown, SetupNetworkRules.classify(facts(manager = false)))
        assertEquals(SetupNetwork.Offline, SetupNetworkRules.classify(facts(network = false)))
        assertEquals(SetupNetwork.Offline, SetupNetworkRules.classify(facts(internet = false)))
        assertEquals(SetupNetwork.Unknown, SetupNetworkRules.classify(facts(validated = false)))
        assertEquals(SetupNetwork.Metered, SetupNetworkRules.classify(facts(cellular = true))) // even on an unmetered plan
        assertEquals(SetupNetwork.Metered, SetupNetworkRules.classify(facts(metered = true)))
    }

    @Test
    fun `B-ANDR-101 a VPN is read by what Android says about its underlying networks`() {
        // Tailscale over home Wi-Fi (the owner's phone, 2026-11-07): Transports WIFI|VPN, NOT_METERED, not metered.
        assertEquals(SetupNetwork.Unmetered, SetupNetworkRules.classify(facts(vpn = true)))
        // A VPN over mobile data: the cellular transport is carried onto the VPN, or Android calls it metered.
        assertEquals(SetupNetwork.Metered, SetupNetworkRules.classify(facts(vpn = true, cellular = true, metered = false, notMetered = true)))
        assertEquals(SetupNetwork.Metered, SetupNetworkRules.classify(facts(vpn = true, metered = true)))
        // The two answers disagree, or an unvalidated VPN: never free data.
        assertEquals(SetupNetwork.Unknown, SetupNetworkRules.classify(facts(vpn = true, metered = false, notMetered = false)))
        assertEquals(SetupNetwork.Unknown, SetupNetworkRules.classify(facts(vpn = true, validated = false)))
    }

    @Test
    fun `B-MEDIA-009 only offline voices are offered, sorted by name`() {
        val voices =
            listOf(
                VoiceInfo("en-us-x-net", "en-US", needsNetwork = true, notInstalled = false),
                VoiceInfo("en-gb-x-local", "en-GB", needsNetwork = false, notInstalled = false),
                VoiceInfo("ar-xa-x-missing", "ar", needsNetwork = false, notInstalled = true),
                VoiceInfo("ar-eg-x-local", "ar-EG", needsNetwork = false, notInstalled = false),
            )
        val offline = SystemTtsRules.offline(voices)
        assertEquals(listOf("ar-eg-x-local", "en-gb-x-local"), offline.map { it.id })
        assertTrue(offline.all { it.id == it.label })
        assertEquals("en-gb-x-local", SystemTtsRules.defaultVoice(offline, "en-gb-x-local", "ar-EG")?.id)
        assertEquals("ar-eg-x-local", SystemTtsRules.defaultVoice(offline, null, "ar_EG")?.id)
        assertEquals("ar-eg-x-local", SystemTtsRules.defaultVoice(offline, null, "ar-SA")?.id)
        assertEquals("ar-eg-x-local", SystemTtsRules.defaultVoice(offline, null, "fr-FR")?.id) // the first
        assertNull(SystemTtsRules.defaultVoice(emptyList(), null, "en"))
    }

    @Test
    fun `B-MEDIA-009 system voice chunks stay under the limit and never split a surrogate pair`() {
        val text = "ab" + "😀".repeat(5) + "cd"
        val pieces = SystemTtsRules.chunks(text, 3)
        assertEquals(text, pieces.joinToString(""))
        assertTrue(pieces.all { it.length <= 3 })
        assertTrue(pieces.none { it.last().isHighSurrogate() || it.first().isLowSurrogate() })
        assertEquals(listOf("x".repeat(3000), "x"), SystemTtsRules.chunks("x".repeat(3001), 3000))
    }

    @Test
    fun `B-ANDR-102 plain Wi-Fi is free, plain mobile data waits, and a network that cannot be told stays ask`() {
        assertEquals(SetupNetwork.Unmetered, SetupNetworkRules.classify(facts()))
        assertEquals(SetupNetwork.Metered, SetupNetworkRules.classify(facts(cellular = true)))
        assertEquals(SetupNetwork.Unknown, SetupNetworkRules.classify(facts(validated = false)))
        // A VPN over a Wi-Fi nobody validated is not known to be free either.
        val unchecked = net(LinkKind.Wifi, validated = false, notMetered = true)
        val own = facts(vpn = true, validated = false)
        assertEquals(SetupNetwork.Unknown, decide(setOf(LinkKind.Wifi), unchecked, own = own))
    }

    private fun net(link: LinkKind, validated: Boolean, notMetered: Boolean) =
        ConnectedNetworkFacts(setOf(link), internet = true, validated = validated, notMetered = notMetered)

    private val wifiNet = net(LinkKind.Wifi, validated = true, notMetered = true)
    private val mobileNet = net(LinkKind.Cellular, validated = true, notMetered = false)
    private val wifi = setOf(LinkKind.Wifi)
    private val mobile = setOf(LinkKind.Cellular)

    private fun decide(vpnLinks: Set<LinkKind>, vararg connected: ConnectedNetworkFacts, own: NetworkFacts) =
        SetupNetworkRules.classify(own.copy(underlying = SetupNetworkRules.carriersOf(vpnLinks, connected.toList())))

    @Test
    fun `B-ANDR-102 Wi-Fi and mobile data both connected, a VPN over Wi-Fi downloads, read by its own links`() {
        // Its own record is unvalidated, so the carriers speak: mobile data beside it is not read.
        val badRecord = facts(vpn = true, validated = false, metered = true, notMetered = false)
        assertEquals(
            SetupNetwork.Unmetered,
            decide(wifi, wifiNet, mobileNet, own = badRecord),
        )
        // Its own record is validated and not metered: it is used first.
        assertEquals(SetupNetwork.Unmetered, decide(wifi, wifiNet, mobileNet, own = facts(vpn = true)))
    }

    @Test
    fun `B-ANDR-102 Wi-Fi connected but the VPN pinned to mobile data waits`() {
        val unsure = facts(vpn = true, validated = false, metered = true, notMetered = false)
        assertEquals(SetupNetwork.Metered, decide(mobile, wifiNet, mobileNet, own = unsure))
        // Its own record says so too (validated, cellular link): waits whatever the plan.
        val validatedCellular = facts(vpn = true, cellular = true)
        assertEquals(SetupNetwork.Metered, decide(mobile, wifiNet, mobileNet, own = validatedCellular))
    }

    @Test
    fun `B-ANDR-102 a validated VPN's own metered answer comes first, no carriers known stays ask`() {
        assertEquals(SetupNetwork.Metered, SetupNetworkRules.classify(facts(vpn = true, metered = true)))
        assertEquals(SetupNetwork.Unmetered, SetupNetworkRules.classify(facts(vpn = true)))
        // Unvalidated and nothing known about what carries it: never free data.
        assertEquals(SetupNetwork.Unknown, SetupNetworkRules.classify(facts(vpn = true, validated = false)))
        val unsure = facts(vpn = true, validated = false)
        assertEquals(SetupNetwork.Unknown, decide(setOf(LinkKind.Bluetooth), wifiNet, mobileNet, own = unsure))
    }
}
