package io.github.eslamasabry.oc.android.voice

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
}
