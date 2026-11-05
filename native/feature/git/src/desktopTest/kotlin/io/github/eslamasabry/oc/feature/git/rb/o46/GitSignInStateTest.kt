package io.github.eslamasabry.oc.feature.git.rb.o46

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.git.*
import io.github.eslamasabry.oc.feature.git.presentation.*
import io.github.eslamasabry.oc.feature.git.ui.GitScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GitSignInStateTest {
    @Test
    fun `B-SEC-084 requesting a device code retains the explicit cancel action`() =
        runComposeUiTest {
            val calls = mutableListOf<String>()
            val actions =
                Proxy.newProxyInstance(
                    GitActions::class.java.classLoader,
                    arrayOf(GitActions::class.java),
                ) { proxy, method, args ->
                    when (method.name) {
                        "equals" -> proxy === args?.firstOrNull()
                        "hashCode" -> System.identityHashCode(proxy)
                        "toString" -> "SignInActions"
                        else -> {
                            calls += method.name
                            null
                        }
                    }
                } as GitActions
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    GitScreen(
                        GitUi(
                            view = GitPageView.NotRepository,
                            offersGitHubSignIn = true,
                            signIn = GitHubSignIn.RequestingCode,
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("Cancel").performScrollTo().performClick()
            assertEquals(listOf("cancelSignIn"), calls)
        }
}
