import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val nikoLocalProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun configString(envName: String, propertyName: String, defaultValue: String = ""): String {
    // Accept configuration from previous installations/deployments during the rename.
    val raw = System.getenv(envName) ?: nikoLocalProperties.getProperty(propertyName)
        ?: System.getenv(envName.replaceFirst("NIKO_", "EDDY_"))
        ?: nikoLocalProperties.getProperty(propertyName.replaceFirst("niko.", "eddy.")) ?: defaultValue
    val escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

val sherpaVersion = "1.13.7"

android {
    namespace = "com.niko.assistant"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.eddy.assistant"
        minSdk = 29
        targetSdk = 36
        versionCode = 21
        versionName = "0.9.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "NIKO_AI_BASE_URL", configString("NIKO_AI_BASE_URL", "niko.ai.baseUrl", "https://eddy-ai-ny8o.onrender.com"))

        // NIKO is optimized for modern Android phones. Packaging only ARM64 removes
        // duplicate Sherpa/MediaPipe native binaries for x86/x86_64/32-bit ARM while
        // keeping the exact same recognition, TTS and local-AI model quality.
        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }
    signingConfigs { getByName("debug") { enableV1Signing = true; enableV2Signing = true } }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    androidResources { noCompress += listOf("bundle") }
    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        unitTests.all {
            it.testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            System.getenv("NIKO_NATIVE_LIB_DIR")?.let { nativeDirectory ->
                it.systemProperty("java.library.path", nativeDirectory)
            }
        }
    }
    packaging {
        // Compress native .so files inside the APK. Android extracts them when needed;
        // inference precision and model quality are unchanged.
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/LICENSE*"
        }
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("k2-fsa:sherpa-onnx:$sherpaVersion@aar")
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
