plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ai.hey.phone"
    compileSdk = 36
    defaultConfig {
        applicationId = "ai.hey.phone"
        minSdk = 28
        targetSdk = 35
        versionCode = providers.gradleProperty("hey.versionCode").get().toInt()
        versionName = providers.gradleProperty("hey.versionName").get()
    }
    // Companion server (same API as the phone web). https only. Override with -PserverUrl=https://...
    val serverUrl = providers.gradleProperty("serverUrl").getOrElse("https://hey.lastmile.ltd/api")
    require(serverUrl.startsWith("https://")) { "serverUrl must be https" }
    buildTypes {
        debug { buildConfigField("String", "SERVER_URL", "\"$serverUrl\"") }
        release {
            buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Not shipped: only the debug-signed APK is distributed (team debug key, see CI).
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform(libs.compose.bom))
    // Generated design tokens (companion-shared, git submodule `shared/`); substituted by includeBuild in settings.gradle.kts.
    implementation("ai.hey:tokens-compose")
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
