package io.github.eslamasabry.oc.feature.diagnostics.ui

import io.github.eslamasabry.oc.feature.diagnostics.presentation.ReportHeadline
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.crashKindClosed
import io.github.eslamasabry.oc.ui.words.res.crashKindNotResponding
import io.github.eslamasabry.oc.ui.words.res.recentErrorAndroidExit
import io.github.eslamasabry.oc.ui.words.res.recentErrorConnection
import io.github.eslamasabry.oc.ui.words.res.recentErrorGeneric
import io.github.eslamasabry.oc.ui.words.res.recentErrorReportStore
import io.github.eslamasabry.oc.ui.words.res.recentErrorScreen
import io.github.eslamasabry.oc.ui.words.res.recentErrorStartup
import io.github.eslamasabry.oc.ui.words.res.recentErrorTemperature
import io.github.eslamasabry.oc.ui.words.res.reportHeadlineLowMemory
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** The words of each [ReportHeadline] (E19-S04), the same ones Report a problem's kept errors use. */
public val ReportHeadline.words: StringResource
    get() =
        when (this) {
            ReportHeadline.AppClosedUnexpectedly -> Res.string.crashKindClosed
            ReportHeadline.AppNotResponding -> Res.string.crashKindNotResponding
            ReportHeadline.AndroidFreedMemory -> Res.string.reportHeadlineLowMemory
            ReportHeadline.AndroidClosedApp -> Res.string.recentErrorAndroidExit
            ReportHeadline.Connection -> Res.string.recentErrorConnection
            ReportHeadline.Temperature -> Res.string.recentErrorTemperature
            ReportHeadline.Screen -> Res.string.recentErrorScreen
            ReportHeadline.Startup -> Res.string.recentErrorStartup
            ReportHeadline.ReportStore -> Res.string.recentErrorReportStore
            ReportHeadline.Generic -> Res.string.recentErrorGeneric
        }

/** The headline in the app's language, for a report built outside composition. */
public suspend fun ReportHeadline.text(): String = getString(words)
