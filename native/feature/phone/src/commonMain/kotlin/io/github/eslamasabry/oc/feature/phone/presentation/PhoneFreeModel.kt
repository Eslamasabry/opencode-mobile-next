package io.github.eslamasabry.oc.feature.phone.presentation

/**
 * This phone's free-model notice (Flutter this_phone_screen.dart:692, B-AGENT-110): while the app is connected to
 * this phone's server and its replies come from OpenCode's free model with nobody signed in, the page says the
 * replies are slower and offers "Add an API key", which opens that server's providers. The app binds it over the
 * phone's connection (the same rule as the chat header's FreeModel.uses); unbound, the notice never shows.
 */
public interface PhoneFreeModel {
    /** True only when connected here, nobody is signed in and the OpenCode free model is in use. */
    public suspend fun inUse(): Boolean

    /** "Add an API key": this phone's server's providers. */
    public fun addApiKey()
}

public class NoPhoneFreeModel : PhoneFreeModel {
    override suspend fun inUse(): Boolean = false

    override fun addApiKey(): Unit = Unit
}
