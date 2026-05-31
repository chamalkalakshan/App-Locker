plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ── Release signing ───────────────────────────────────────────────────────────
//
// Copy keystore.properties.template → keystore.properties and fill in your values.
// That file is git-ignored so credentials never enter version control.
//
// Signed release APK:   ./gradlew assembleRelease
// Signed release AAB:   ./gradlew bundleRelease  (recommended for Play Store)
//
// Without keystore.properties the release build falls back to the debug keystore
// so CI and local testing still produce a working APK — but DO NOT distribute that APK.
fun loadKeystoreProps(): Map<String, String> {
    val f = rootProject.file("keystore.properties")
    if (!f.exists()) return emptyMap()
    return f.readLines()
        .filter { '=' in it && !it.trimStart().startsWith('#') }
        .associate { line ->
            line.substringBefore('=').trim() to line.substringAfter('=').trim()
        }
}

android {
    namespace = "com.locker.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.locker.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val keystoreProps = loadKeystoreProps()
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile      = file(keystoreProps.getValue("storeFile"))
                storePassword  = keystoreProps.getValue("storePassword")
                keyAlias       = keystoreProps.getValue("keyAlias")
                keyPassword    = keystoreProps.getValue("keyPassword")
            } else {
                // No keystore.properties found — fall back to debug keystore.
                // See keystore.properties.template for production setup instructions.
                val dbg = signingConfigs.getByName("debug")
                storeFile      = dbg.storeFile
                storePassword  = dbg.storePassword
                keyAlias       = dbg.keyAlias
                keyPassword    = dbg.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
