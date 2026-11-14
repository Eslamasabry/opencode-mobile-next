package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The name the engine saves the phone's own server under (`StoredProfile`); it is the phone's identity there. */
internal const val SAVED_PHONE_NAME: String = "This phone"

/**
 * A server's name as home shows it: the phone's own server is saved under its English name, so in another language
 * it reads as that language's "This phone" (audit 2, Arabic: the chip and every row line stayed English). Any other
 * name is the person's own and shown as saved.
 */
internal fun serverNameShown(saved: String, phoneWord: String): String = if (saved == SAVED_PHONE_NAME) phoneWord else saved

@Composable
internal fun serverNameShown(saved: String): String = serverNameShown(saved, stringResource(Res.string.settingsV3ThisPhone))

@Composable
internal fun serverNameShownOrNull(saved: String?): String? = saved?.let { serverNameShown(it) }
