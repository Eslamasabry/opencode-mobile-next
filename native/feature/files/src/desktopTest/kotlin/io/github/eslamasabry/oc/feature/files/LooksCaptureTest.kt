package io.github.eslamasabry.oc.feature.files

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.engine.files.FileBody
import io.github.eslamasabry.oc.feature.files.presentation.FileActions
import io.github.eslamasabry.oc.feature.files.presentation.FilePhase
import io.github.eslamasabry.oc.feature.files.presentation.FileUi
import io.github.eslamasabry.oc.feature.files.ui.FileScreen
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LooksCaptureTest {
    private val code = """
package io.github.eslamasabry.oc.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where the app sends a person after sign-in. */
class LoginRedirect(private val session: SessionStore) {
    private val target = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> get() = target

    fun remember(route: String) {
        // Never remember the login page itself, or sign-in loops forever.
        if (route.startsWith("/login")) return
        target.value = route
    }

    suspend fun afterSignIn(): String {
        val next = target.value ?: "/chats"
        target.value = null
        check(session.isSignedIn()) { "redirect before sign-in" }
        return next
    }
}
""".trimIndent()

    private class FileCalls : FileActions {
        val calls = mutableListOf<String>()

        override fun openLink(url: String) {
            calls += "openLink"
        }

        override fun confirmLink() {
            calls += "confirmLink"
        }

        override fun cancelLink() {
            calls += "cancelLink"
        }

        override fun back() {
            calls += "back"
        }

        override fun retry() {
            calls += "retry"
        }

        override fun ask() {
            calls += "ask"
        }

        override fun openWith() {
            calls += "openWith"
        }

        override fun save() {
            calls += "save"
        }

        override fun shareOnWifi() {
            calls += "share"
        }

        override fun stopSharing() {
            calls += "stop"
        }

        override fun closeShare() {
            calls += "close"
        }

        override fun dismissNotice() {}

        override fun pdfPage(index: Int) {}
    }

    @Test
    fun viewer() {
        val ui = FileUi(
            path = "src/main/kotlin/auth/LoginRedirect.kt",
            phase = FilePhase.Ready,
            body = FileBody.Code(code, "kotlin", truncated = false, line = null),
            canShare = true,
            canOpenWith = true,
            canSave = true,
            canAttach = true,
            canChooseWrap = true,
        )
        Looks.shoot("native-07-file-viewer") { FileScreen(ui, FileCalls()) }
    }
}
