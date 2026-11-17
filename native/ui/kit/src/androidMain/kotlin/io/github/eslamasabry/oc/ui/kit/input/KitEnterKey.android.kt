package io.github.eslamasabry.oc.ui.kit.input

import android.view.KeyCharacterMap
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent

public actual fun KeyEvent.fromSoftKeyboard(): Boolean = nativeKeyEvent.isFromSoftKeyboard()

/** An on-screen keyboard delivers keys from the virtual device and flags them soft or as an editor action. */
internal fun AndroidKeyEvent.isFromSoftKeyboard(): Boolean =
    deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD ||
        flags and (AndroidKeyEvent.FLAG_SOFT_KEYBOARD or AndroidKeyEvent.FLAG_EDITOR_ACTION) != 0
