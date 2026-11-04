package io.github.eslamasabry.oc.core.domain.platform

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId

/**
 * A known host permission block. None means no known block: the actual file read remains
 * authoritative.
 */
public enum class SharedStorageBlock {
    None,
    AppAccess,
    TermuxAccess,
}

/**
 * Only the saved local host may ask about this phone's storage; remote directories never enter the
 * phone gate.
 */
public interface SharedStorageAccess {
    public suspend fun check(
        profile: ProfileId,
        directory: String?,
        expectedSource: Any?,
    ): Outcome<SharedStorageBlock>

    /**
     * Opens the relevant system page or Termux. This is a request to the person, never a permission
     * grant.
     */
    public suspend fun request(
        profile: ProfileId,
        directory: String?,
        expected: SharedStorageBlock,
        expectedSource: Any?,
    ): Outcome<Unit>
}
