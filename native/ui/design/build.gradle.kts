plugins { id("oc.kmp.library") }

kotlin { sourceSets.commonMain.dependencies { implementation(project(":core:common")) } }
