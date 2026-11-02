plugins { id("oc.kmp.library") }

// :engine:usage (unit P12-SEARCH-USAGE). Calendar arithmetic in the person's own time zone for the Usage page: features
// stay kit-only (K1), so the time-zone math lives here, like FeedDay in :engine:agents. Core only, like every engine.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.kotlinx.datetime) // UsageCalendar: calendar days in an IANA zone (B-PROTO-122)
    }
}
