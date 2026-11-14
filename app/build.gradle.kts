import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.paparazzi)
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
    // Release signing: HEYPARRI_STORE_FILE / HEYPARRI_STORE_PASSWORD / HEYPARRI_KEY_ALIAS (CI release-apk job, key password =
    // store password), else the 600 properties file outside the repo. Neither present: release falls back to the debug key
    // (the debug CI path has no key material and keeps building).
    val signingFile = File(System.getProperty("user.home"), ".config/hey/signing.properties")
    val envStore = System.getenv("HEYPARRI_STORE_FILE")
    val releaseSigning = if (envStore != null) {
        signingConfigs.create("release") {
            storeFile = file(envStore)
            storePassword = System.getenv("HEYPARRI_STORE_PASSWORD")
            keyAlias = System.getenv("HEYPARRI_KEY_ALIAS")
            keyPassword = System.getenv("HEYPARRI_STORE_PASSWORD")
        }
    } else if (signingFile.isFile) {
        val p = Properties().apply { signingFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    } else signingConfigs.getByName("debug")
    // Companion server (same API as the phone web). https only. Override with -PserverUrl=https://...
    val serverUrl = providers.gradleProperty("serverUrl").getOrElse("https://hey.lastmile.ltd/api")
    require(serverUrl.startsWith("https://")) { "serverUrl must be https" }
    // e2e only (debug and releaseE2e, never release): the test server on darren-vm over tailscale (the phone emulator reaches
    // it directly; mbp-16 forbids ssh -R). -Pe2eServerUrl=http://100.69.201.20:<port>
    val e2eServerUrl = providers.gradleProperty("e2eServerUrl").orNull
    require(e2eServerUrl == null || Regex("http://100\\.69\\.201\\.20:\\d+").matches(e2eServerUrl)) { "e2eServerUrl must be http://100.69.201.20:<port>" }
    val debugServerUrl = e2eServerUrl ?: serverUrl
    buildTypes {
        debug { buildConfigField("String", "SERVER_URL", "\"$debugServerUrl\"") }
        release {
            buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
        // R8 proof: minified and shrunk exactly like release but debug-signed. Never shipped. Build: ./gradlew :app:assembleReleaseE2e
        create("releaseE2e") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            buildConfigField("String", "SERVER_URL", "\"$debugServerUrl\"")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // Loopback cleartext (src/e2e/res/xml/network_security.xml) is compiled into debug and releaseE2e only; release keeps the https-only main config.
    sourceSets {
        getByName("debug") { res.srcDir("src/e2e/res") }
        getByName("releaseE2e") { res.srcDir("src/e2e/res") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests { isReturnDefaultValues = true; isIncludeAndroidResources = true } }
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
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.zxing.core)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
