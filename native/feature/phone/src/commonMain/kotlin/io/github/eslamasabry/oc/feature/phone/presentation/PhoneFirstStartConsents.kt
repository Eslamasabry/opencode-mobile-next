package io.github.eslamasabry.oc.feature.phone.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.settings.consent.InFlowConsentKind
import kotlinx.coroutines.flow.StateFlow

/** A first-start question, claimed durably before it is displayed. */
public data class PhoneConsentUi(val kind: InFlowConsentKind, val maker: String)

/** App-scoped queue: the saved answer must succeed before any Android settings prompt opens. */
public interface PhoneFirstStartConsents {
    public val pending: StateFlow<PhoneConsentUi?>

    public suspend fun request(
        profile: ProfileId,
        batteryAlreadyExempt: Boolean,
        makerHasAutoStartStep: Boolean,
        maker: String,
    )

    public suspend fun answer(allow: Boolean): Outcome<Unit>
}
