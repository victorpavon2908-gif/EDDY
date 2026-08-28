import java.io.File
import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val eddyLocalProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun configString(envName: String, propertyName: String, defaultValue: String = ""): String {
    val raw = System.getenv(envName)
        ?: eddyLocalProperties.getProperty(propertyName)
        ?: defaultValue
    val escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

val sherpaVersion = "1.13.6"
val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx-$sherpaVersion.aar").asFile
val downloadSherpaAar = tasks.register("downloadSherpaAar") {
    outputs.file(sherpaAar)
    doLast {
        if (!sherpaAar.exists() || sherpaAar.length() < 40_000_000L) {
            sherpaAar.parentFile.mkdirs()
            val temp = File(sherpaAar.parentFile, sherpaAar.name + ".part")
            URI("https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar")
                .toURL()
                .openStream()
                .use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
            check(temp.length() > 40_000_000L) { "sherpa-onnx AAR incompleto" }
            if (sherpaAar.exists()) sherpaAar.delete()
            check(temp.renameTo(sherpaAar)) { "No se pudo instalar sherpa-onnx AAR" }
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(downloadSherpaAar)
}

android {
    namespace = "com.eddy.assistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.eddy.assistant"
        minSdk = 29
        targetSdk = 36
        versionCode = 12
        versionName = "0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "EDDY_AI_BASE_URL",
            configString(
                "EDDY_AI_BASE_URL",
                "eddy.ai.baseUrl",
                "https://eddy-ai-ny8o.onrender.com",
            ),
        )
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/LICENSE*"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(files(sherpaAar))
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
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
