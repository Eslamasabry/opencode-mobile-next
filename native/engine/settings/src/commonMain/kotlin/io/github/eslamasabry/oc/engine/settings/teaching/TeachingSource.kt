package io.github.eslamasabry.oc.engine.settings.teaching

/** One app-wide controller shared by contextual tips and Help › Tips replay/reset. */
public fun interface TeachingSource {
    public suspend fun controller(): TipController?
}
