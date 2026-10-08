package io.github.eslamasabry.opencode_mobile

import android.content.Context

fun main(args: Array<String>) {
    val scenario = args.single()
    val context = Context(if (scenario == "english-override" || scenario == "system-ar") "ar" else "en")
    when (scenario) {
        "arabic-override" -> {
            context.preferences.choice = "ar"
            check(NativeStrings.get(context, R.string.native_stop) == "إيقاف")
            check(context.resources.configuration.locales[0].language == "en")
            val restarted = Context("en", context.preferences)
            check(NativeStrings.get(restarted, R.string.native_stop) == "إيقاف")
        }
        "english-override" -> {
            context.preferences.choice = "en"
            check(NativeStrings.get(context, R.string.native_stop) == "Stop")
            check(context.resources.configuration.locales[0].language == "ar")
        }
        "system-ar" -> check(NativeStrings.get(context, R.string.native_stop) == "إيقاف")
        "untranslated-app" -> {
            val arabic = Context("ar")
            for (language in listOf("es", "ja", "pt", "ru", "zh")) {
                arabic.preferences.choice = language
                check(NativeStrings.get(arabic, R.string.native_stop) == "Stop")
            }
            check(NativeStrings.get(Context(systemLanguages = listOf("fr", "ja", "ar")), R.string.native_stop) == "Stop")
        }
        "system-list" -> check(
            NativeStrings.get(Context(systemLanguages = listOf("fr", "ar", "en")), R.string.native_stop) == "إيقاف"
        )
        "unsupported-system" -> check(NativeStrings.get(Context("de"), R.string.native_stop) == "Stop")
        "invalid-choice" -> {
            val arabic = Context("ar")
            arabic.preferences.choice = "de"
            check(NativeStrings.get(arabic, R.string.native_stop) == "إيقاف")
            arabic.preferences.choice = 42
            check(NativeStrings.get(arabic, R.string.native_stop) == "إيقاف")
        }
        "read-refused" -> {
            val arabic = Context("ar")
            arabic.preferences.failRead = true
            check(NativeStrings.get(arabic, R.string.native_stop) == "إيقاف")
        }
        "choice-changes" -> {
            context.preferences.choice = "ar"
            check(NativeStrings.get(context, R.string.native_stop) == "إيقاف")
            context.preferences.choice = "en"
            check(NativeStrings.get(context, R.string.native_stop) == "Stop")
            context.preferences.choice = null
            check(NativeStrings.get(context, R.string.native_stop) == "Stop")
        }
        "resource-formatting" -> {
            for (language in listOf("en", "ar")) {
                context.preferences.choice = language
                for ((id, name) in ResourceNames.names) {
                    val formatted = if (id in ResourceNames.pluralIds) {
                        (listOf(0, 1, 2, 3, 11, 100)).map {
                            NativeStrings.quantity(context, id, it, it)
                        }
                    } else {
                        listOf(NativeStrings.get(context, id, if (id in ResourceNames.numericStringIds) 3 else "Claude Code", "3"))
                    }
                    check(formatted.all { it.isNotBlank() && !it.contains(Regex("%[0-9]+\\$[sd]")) }) {
                        "Unformatted resource $name/$language"
                    }
                }
            }
        }
        else -> error("unknown scenario")
    }
    if (context.preferences.reads.isNotEmpty()) {
        check(context.preferenceFiles.all { it == "FlutterSharedPreferences" })
        check(context.preferences.reads.all { it == "flutter.oc.appLocale" })
    }
    println("PASS $scenario")
}
