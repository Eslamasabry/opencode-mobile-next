package io.github.eslamasabry.oc.feature.chat.header.world

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.engine.agents.sessionimport.SessionImportFiles

@ContributesTo(AppScope::class)
public interface SessionImportBindings {
    @Multibinds(allowEmpty = true) public val sessionImportFiles: Set<SessionImportFiles>
}
