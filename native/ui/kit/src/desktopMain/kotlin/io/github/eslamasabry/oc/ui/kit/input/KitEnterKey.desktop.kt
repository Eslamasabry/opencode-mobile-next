package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.ui.input.key.KeyEvent

internal actual fun KeyEvent.fromSoftKeyboard(): Boolean = false
