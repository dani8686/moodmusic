plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.daniel.mood"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.daniel.mood"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing is injected by GitHub Actions. No key or password is stored in source.
    val signingFile = providers.environmentVariable("MOOD_KEYSTORE_FILE").orNull
    val signingPassword = providers.environmentVariable("MOOD_KEYSTORE_PASSWORD").orNull
    val signingAlias = providers.environmentVariable("MOOD_KEY_ALIAS").orNull
    if (!signingFile.isNullOrBlank() && !signingPassword.isNullOrBlank() && !signingAlias.isNullOrBlank()) {
        signingConfigs.create("release") {
            storeFile = file(signingFile)
            storePassword = signingPassword
            keyAlias = signingAlias
            keyPassword = providers.environmentVariable("MOOD_KEY_PASSWORD").orNull ?: signingPassword
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Without protected secrets CI produces an installable, debug-signed TEST release.
            // Configure the MOOD_* secrets before distribution or seamless future updates.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
}

kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core-ktx:1.6.1")
}
