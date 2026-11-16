package oc.buildlogic

import org.gradle.api.Project

/**
 * The version name a build carries (release rule, 2026-11-17). `native/version.properties` holds the plain name of the
 * NEXT public release (`3.0.1`). Only a build marked public (`-Poc.publicRelease=true`: the Native release workflow and
 * `scripts/native-release.sh --lineage public`) carries that name as it is; every other build (debug, preview, owner
 * key, CI test builds) carries `<name>-test.<versionCode>`, so a tester's build can never be counted as the public
 * version in a report. The updater compares build numbers, never names, so the suffix changes nothing about updates.
 */
object ReleaseVersionName {
    const val PUBLIC_PROPERTY = "oc.publicRelease"

    /** A public release name: 3.0.0 or later, three numbers, no suffix. */
    private val PUBLIC = Regex("""[3-9]\.\d+\.\d+|\d{2,}\.\d+\.\d+""")
    private val TEST = Regex("""(?:[3-9]\.\d+\.\d+|\d{2,}\.\d+\.\d+)-test\.(\d+)""")

    fun isPublicBuild(project: Project): Boolean = project.findProperty(PUBLIC_PROPERTY)?.toString() == "true"

    /** The name a build takes from the properties file. A name that already has a suffix is left as it is. */
    fun forBuild(base: String, versionCode: Int, publicBuild: Boolean): String =
        if (publicBuild || base.contains('-')) base else "$base-test.$versionCode"

    /** What is wrong with the names the APKs of one build carry, empty when the identity is right. */
    fun problems(names: List<String>, versionCode: Int?, publicBuild: Boolean): List<String> {
        val distinct = names.distinct()
        if (distinct.size != 1) return listOf("the APKs carry different versionNames $distinct")
        val name = distinct.single()
        if (publicBuild) {
            return if (PUBLIC.matches(name)) emptyList()
            else listOf("versionName $distinct is not one plain release version (3.0.0 or later, no suffix): a public release never carries -test.N")
        }
        val match = TEST.matchEntire(name)
            ?: return listOf("versionName $distinct is not <version>-test.<build>: a build that is not the public release carries -test.<build>")
        return if (versionCode != null && match.groupValues[1] != versionCode.toString()) {
            listOf("versionName $distinct names build ${match.groupValues[1]} but the versionCode is $versionCode")
        } else {
            emptyList()
        }
    }
}
