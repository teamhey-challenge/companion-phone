plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.paparazzi) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt)
}

// detekt over the app's main and test sources. config/detekt/detekt.yml + baseline.xml: the findings that existed when
// the gate was added are recorded in the baseline; any new finding fails `detekt` (part of `check` and a blocking CI job).
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
    source.setFrom(fileTree(layout.projectDirectory) { include("app/src/**/*.kt") })
    parallel = true
}
tasks.named("check") { dependsOn("detekt") }
