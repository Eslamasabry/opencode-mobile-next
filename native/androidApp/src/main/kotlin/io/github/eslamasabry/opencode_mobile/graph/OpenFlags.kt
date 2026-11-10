package io.github.eslamasabry.opencode_mobile.graph

import android.os.Build
import android.system.OsConstants

/**
 * `O_CLOEXEC` for `Os.open`. `OsConstants.O_CLOEXEC` only exists from Android 8.1 (API 27); reading it on Android 8.0
 * throws NoSuchFieldError, which took down files, plans and the Linux backup there. The flag itself is plain Linux
 * (0x80000 on every ABI Android ships), so 8.0 gets the number.
 */
internal val oCloexec: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) OsConstants.O_CLOEXEC else 0x80000
