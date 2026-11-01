// Slice-0 P5 (native/proof-p5-2026-11-01): the device half of the migration proof. The P5 reader library is compiled
// in from native/tools/flutter-migration-readers unchanged; this module adds the AndroidKeyStore unwrapper and a
// proof runner. The real :android:migration (MigrationRunner, gate, journal) is slice work.
plugins { id("oc.android.library") }

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("main").kotlin.srcDir("../../tools/flutter-migration-readers/src/main/kotlin")
}
