plugins { id("oc.kmp.compose") }

kotlin { sourceSets.commonMain.dependencies { implementation(project(":core:common")) } }
