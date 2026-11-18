package io.github.eslamasabry.oc.engine.analytics

/**
 * The learned phrase suggestions of a server profile could not be read (a damaged stored blob). The app keeps the
 * unreadable text aside, starts empty, and sends one non-fatal behind the crash-report switch: only the fixed name
 * leaves the phone, never the stored text.
 */
public fun Reporting.reportPhraseChipsUnreadable() {
    reportFailure("phrase_chips_unreadable")
}

/**
 * The phone server's own recovery could not be told that the app is in front, twice in a row in one visit. One
 * non-fatal behind the crash-report switch: only the fixed name leaves the phone, never the error's text.
 */
public fun Reporting.reportPhoneServerFollowRefused() {
    reportFailure("phone_server_follow_refused")
}
