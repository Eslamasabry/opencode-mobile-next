package io.github.eslamasabry.oc.feature.git

import io.github.eslamasabry.oc.engine.git.GitProblem
import io.github.eslamasabry.oc.feature.git.presentation.GitWay
import io.github.eslamasabry.oc.feature.git.presentation.gitSavedNeedsSignIn
import io.github.eslamasabry.oc.feature.git.presentation.gitWayForward
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** G5: a failed Send/Save carries its one way forward (Flutter git_sheet.dart `_wayForward`). */
class GitWayTest {
    @Test
    fun `a refused push offers Get latest, a missing name offers the name step`() {
        assertEquals(GitWay.GetLatest, gitWayForward(GitProblem.PushRejected, offersGitHubSignIn = false, canSignIn = false))
        assertEquals(GitWay.SetIdentity, gitWayForward(GitProblem.IdentityMissing, offersGitHubSignIn = false, canSignIn = false))
    }

    @Test
    fun `sign-in is offered only where the app's own GitHub sign-in is what a push uses`() {
        assertEquals(GitWay.SignIn, gitWayForward(GitProblem.AuthRequired, offersGitHubSignIn = true, canSignIn = true))
        assertEquals(GitWay.SignIn, gitWayForward(GitProblem.GitHubTokenRejected, offersGitHubSignIn = true, canSignIn = true))
        assertNull(gitWayForward(GitProblem.AuthRequired, offersGitHubSignIn = false, canSignIn = true))
        assertNull(gitWayForward(GitProblem.AuthRequired, offersGitHubSignIn = true, canSignIn = false))
    }

    @Test
    fun `other problems have no step`() {
        assertNull(gitWayForward(GitProblem.Network, offersGitHubSignIn = true, canSignIn = true))
    }

    @Test
    fun `a save whose send needs sign-in says sign in first only with a place to name`() {
        assertTrue(gitSavedNeedsSignIn(GitProblem.AuthRequired, offersGitHubSignIn = true, hasPlace = true))
        assertFalse(gitSavedNeedsSignIn(GitProblem.AuthRequired, offersGitHubSignIn = true, hasPlace = false))
        assertFalse(gitSavedNeedsSignIn(GitProblem.PushRejected, offersGitHubSignIn = true, hasPlace = true))
    }
}
