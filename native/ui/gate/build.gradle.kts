plugins { id("oc.kmp.compose") }

// The golden-diff gate (docs/design/native-ui-rebuild/GOLDEN-STANDARD.md §8.2). Its sources are desktopMain files
// that call KitTheme, so they compile only where :ui:kit is on the classpath. ModuleRules (build-logic, lead-owned)
// has no `:ui:gate` edges yet, so the consumers (:ui:kit, :ui:kit-chat, :ui:kit-content) add
// `ui/gate/src/desktopMain/{kotlin,resources}` to their own desktopTest source set (see their build files) and this
// module compiles nothing itself.
kotlin {
    sourceSets.getByName("desktopMain").kotlin.setSrcDirs(emptyList<String>())
}
