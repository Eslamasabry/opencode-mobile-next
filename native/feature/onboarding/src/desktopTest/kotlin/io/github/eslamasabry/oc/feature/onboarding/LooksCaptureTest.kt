package io.github.eslamasabry.oc.feature.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.feature.onboarding.presentation.PhoneWelcomeUi
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeActions
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomePlace
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeUi
import io.github.eslamasabry.oc.feature.onboarding.ui.WelcomeScreen
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LooksCaptureTest {
    private object A : WelcomeActions {
        override fun connect() {}
        override fun openSettings() {}
    }

    @Test
    fun welcome() = Looks.shoot("native-01-welcome") {
        WelcomeScreen(WelcomeUi(phone = PhoneWelcomeUi(available = true), place = WelcomePlace.Phone), A)
    }
}
