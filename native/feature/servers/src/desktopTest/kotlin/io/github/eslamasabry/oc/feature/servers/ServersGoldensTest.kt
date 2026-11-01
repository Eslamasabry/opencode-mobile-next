package io.github.eslamasabry.oc.feature.servers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure.Net
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.feature.servers.presentation.CleartextConsentUi
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionDiagnosis
import io.github.eslamasabry.oc.feature.servers.presentation.DiagnosisContext
import io.github.eslamasabry.oc.feature.servers.presentation.EditorActions
import io.github.eslamasabry.oc.feature.servers.presentation.EditorUi
import io.github.eslamasabry.oc.feature.servers.presentation.EditorVerdict
import io.github.eslamasabry.oc.feature.servers.presentation.EntryKind
import io.github.eslamasabry.oc.feature.servers.presentation.FailureAction
import io.github.eslamasabry.oc.feature.servers.presentation.PinReviewUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServerPageActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServerPageUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRowUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServerStatus
import io.github.eslamasabry.oc.feature.servers.presentation.ServerSwitcherActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServerSwitcherUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.presentation.SwitcherRowUi
import io.github.eslamasabry.oc.feature.servers.presentation.TransportSecurity
import io.github.eslamasabry.oc.feature.servers.ui.EditorScreen
import io.github.eslamasabry.oc.feature.servers.ui.ServerPageScreen
import io.github.eslamasabry.oc.feature.servers.ui.ServerSwitcherContent
import io.github.eslamasabry.oc.feature.servers.ui.ServersScreen
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import java.util.Locale
import kotlin.test.Test

/**
 * S2-SERVERS goldens, light/dark × en/ar, 412 × 915 (plan 06 §15.3 phone size) on Compose Desktop (report D1).
 * Record: `:feature:servers:recordRoborazziDesktop`; verify: `:feature:servers:verifyRoborazziDesktop`.
 */
@OptIn(ExperimentalTestApi::class)
class ServersGoldensTest {
    private val variants = listOf(false to "en", true to "en", false to "ar", true to "ar")

    private fun golden(name: String, content: @Composable () -> Unit) {
        for ((dark, locale) in variants) {
            val before = Locale.getDefault()
            Locale.setDefault(Locale.forLanguageTag(locale))
            try {
                runDesktopComposeUiTest(width = 412, height = 915) {
                    setContent { ServersHost(dark, locale, inputs = false) { Box(Modifier.fillMaxSize()) { content() } } }
                    waitForIdle()
                    onRoot().captureRoboImage("src/screenshots/$name/${if (dark) "dark" else "light"}-$locale.png")
                }
            } finally {
                Locale.setDefault(before)
            }
        }
    }

    private val rows =
        persistentListOf(
            ServerRowUi(ProfileId("ph"), "This phone", "http://127.0.0.1:4097", ServerStatus.Connected, true, true, null),
            ServerRowUi(
                ProfileId("lp"), "Laptop", "https://desk.example:4096", ServerStatus.Failed, false, false,
                ConnectionDiagnosis.diagnose(
                    Net.SignInNeeded(Net.SignInNeeded.Why.PasswordRejected),
                    ServerOrigin("https", "desk.example", 4096),
                    DiagnosisContext(BackendKind.OpenCode2, supportsTermux = true),
                    RulesAddressPolicy,
                ),
            ),
            ServerRowUi(ProfileId("at"), "Attic", "http://desk.local:4096", ServerStatus.NotConnected, false, false, null),
        )

    @Test
    fun serversList() = golden("ServersScreen") { ServersScreen(ServersUi(rows), NoServers) }

    @Test
    fun serversWelcome() = golden("ServersWelcome") { ServersScreen(ServersUi(), NoServers) }

    @Test
    fun editorConsent() =
        golden("EditorCleartextConsent") {
            EditorScreen(
                EditorUi(entry = EntryKind.Address, address = "http://192.0.2.20:4096", consent = CleartextConsentUi(ServerOrigin("http", "192.0.2.20", 4096), public = true)),
                NoEditor,
            )
        }

    @Test
    fun editorPinReview() =
        golden("EditorPinReview") {
            EditorScreen(
                EditorUi(
                    entry = EntryKind.Address,
                    address = "https://desk.example:4097",
                    pinReview =
                        PinReviewUi(
                            ServerOrigin("https", "desk.example", 4097),
                            io.github.eslamasabry.oc.feature.servers.presentation.Fingerprints.display(FakeServersPort.SHA2),
                            1_700_000_000_000, 1_800_000_000_000,
                            io.github.eslamasabry.oc.feature.servers.presentation.Fingerprints.display(FakeServersPort.SHA),
                        ),
                ),
                NoEditor,
            )
        }

    @Test
    fun editorFailure() =
        golden("EditorFailure") {
            EditorScreen(
                EditorUi(
                    entry = EntryKind.Address,
                    address = "http://laptop.tail1234.ts.net:4096",
                    cleartextAllowed = true,
                    verdict =
                        EditorVerdict.Failed(
                            ConnectionDiagnosis.diagnose(
                                Net.Unreachable(Net.Unreachable.Why.Timeout),
                                ServerOrigin("http", "laptop.tail1234.ts.net", 4096),
                                DiagnosisContext(BackendKind.OpenCode2, supportsTermux = true, attempts = 3),
                                RulesAddressPolicy,
                            ),
                        ),
                ),
                NoEditor,
            )
        }

    @Test
    fun serverPage() =
        golden("ServerPage") {
            ServerPageScreen(
                ServerPageUi(
                    ProfileId("l2"), "Laptop beta", "https://desk.example:4097", BackendKind.OpenCode2, ServerStatus.Connected, true,
                    TransportSecurity.HttpsPinned, io.github.eslamasabry.oc.feature.servers.presentation.Fingerprints.display(FakeServersPort.SHA), null,
                ),
                NoPage,
            )
        }

    @Test
    fun switcher() =
        golden("ServerSwitcher") {
            ServerSwitcherContent(
                ServerSwitcherUi(
                    persistentListOf(
                        SwitcherRowUi(ProfileId("lp"), "Laptop", "desk.example", 4096, ServerStatus.Connected, true),
                        SwitcherRowUi(ProfileId("l2"), "Laptop beta", "desk.example", 4097, ServerStatus.NotConnected, false),
                        SwitcherRowUi(ProfileId("ph"), "This phone", "127.0.0.1", null, ServerStatus.NotConnected, false),
                    ),
                ),
                NoSwitcher,
            )
        }

    private object NoServers : ServersActions {
        override fun addServer() = Unit
        override fun openServer(id: ProfileId) = Unit
        override fun connect(id: ProfileId) = Unit
        override fun fix(id: ProfileId, action: FailureAction) = Unit
        override fun forget(id: ProfileId) = Unit
        override fun confirmForget() = Unit
        override fun cancelForget() = Unit
    }

    private object NoEditor : EditorActions {
        override fun useEntry(kind: EntryKind) = Unit
        override fun setAddress(text: String) = Unit
        override fun setName(text: String) = Unit
        override fun setUsername(text: String) = Unit
        override fun setPassword(text: String) = Unit
        override fun replacePassword() = Unit
        override fun setCode(text: String) = Unit
        override fun test() = Unit
        override fun allowHttp() = Unit
        override fun declineHttp() = Unit
        override fun reviewCertificate() = Unit
        override fun trustCertificate() = Unit
        override fun cancelCertificateReview() = Unit
        override fun saveAndConnect() = Unit
        override fun back() = Unit
    }

    private object NoPage : ServerPageActions {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun edit() = Unit
        override fun fix(action: FailureAction) = Unit
        override fun reviewCertificate() = Unit
        override fun trustCertificate() = Unit
        override fun cancelCertificateReview() = Unit
        override fun forget() = Unit
        override fun confirmForget() = Unit
        override fun cancelForget() = Unit
        override fun back() = Unit
    }

    private object NoSwitcher : ServerSwitcherActions {
        override fun choose(id: ProfileId) = Unit
        override fun manage(id: ProfileId) = Unit
        override fun addServer() = Unit
    }
}
