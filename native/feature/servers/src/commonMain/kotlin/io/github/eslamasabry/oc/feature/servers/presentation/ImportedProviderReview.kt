package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId

/**
 * Advisory fixed provider labels from the selected server's receipt-verified private import, never
 * sign-in state.
 */
public interface ImportedProviderReview {
    public suspend fun read(profile: ProfileId): Outcome<List<String>>
}

public data class ImportedSignInUi(
    val profile: ProfileId,
    val names: List<String>,
    val failed: Boolean = false,
)
