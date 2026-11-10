plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

// Release signing material is NEVER stored in the repository.
// CI supplies a keystore outside the checkout plus passwords through environment variables
// (backed by GitHub Actions secrets, or a runner-generated key held in the Actions cache).
val releaseStoreFile = System.getenv("MOOD_STORE_FILE")
    ?.takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it) }
    ?.takeIf { it.isFile }
val releaseStorePassword = System.getenv("MOOD_STORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("MOOD_KEY_ALIAS").orEmpty().ifBlank { "moodmusic" }
val releaseKeyPassword = System.getenv("MOOD_KEY_PASSWORD").orEmpty().ifBlank { releaseStorePassword }

android {
    namespace = "com.daniel.mood"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.daniel.mood"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"
        // Redmi Note 10S is arm64-v8a; keeping a single ABI produces a smaller APK.
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storeType = "PKCS12"
                require(releaseStorePassword.isNotBlank()) {
                    "MOOD_STORE_PASSWORD must be set when MOOD_STORE_FILE is provided"
                }
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // R8 is intentionally off: this build is delivered without on-device QA, so
            // reflection-free, predictable bytecode is safer than aggressive shrinking.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (signingConfigs.findByName("release") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
    lint {
        abortOnError = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
