import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties: Properties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.github.biltudas1.sequence"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.github.biltudas1.sequence"
        minSdk = 24
        targetSdk = 37
        versionCode = project.findProperty("sequence.versionCode")?.toString()?.toInt() ?: 1
        versionName = project.findProperty("sequence.versionName")?.toString() ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Centralized configurations from gradle.properties
        val serverMajorVersion = project.findProperty("sequence.compatibleServerMajorVersion") ?: "2"
        val stunServer = project.findProperty("sequence.defaultStunServer") ?: "stun:stun.l.google.com:19302"
        val repoUrl = project.findProperty("sequence.githubRepoUrl") ?: "https://github.com/BiltuDas1/Sequence"
        val licenseUrl = project.findProperty("sequence.licenseUrl") ?: "https://github.com/BiltuDas1/Sequence/blob/main/LICENSE"
        val releasesApiUrl = project.findProperty("sequence.githubReleasesApiUrl") ?: "https://api.github.com/repos/BiltuDas1/Sequence/releases"

        buildConfigField("int", "COMPATIBLE_SERVER_MAJOR_VERSION", serverMajorVersion.toString())
        buildConfigField("String", "DEFAULT_STUN_SERVER", "\"$stunServer\"")
        buildConfigField("String", "GITHUB_REPO_URL", "\"$repoUrl\"")
        buildConfigField("String", "LICENSE_URL", "\"$licenseUrl\"")
        buildConfigField("String", "GITHUB_RELEASES_API_URL", "\"$releasesApiUrl\"")

        resValue("string", "app_name", project.findProperty("sequence.appName")?.toString() ?: "Sequence")
        resValue("string", "google_web_client_id", project.findProperty("sequence.googleWebClientId")?.toString() ?: "")

        ndk {
            if (project.hasProperty("targetAbis")) {
                val abis = project.property("targetAbis").toString().split(",")
                abiFilters.addAll(abis)
            } else {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
            }
        }
    }

    signingConfigs {
        create("release") {
            val sFile = keystoreProperties.getProperty("storeFile")
            if (sFile != null) {
                storeFile = file(sFile)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val sFile = keystoreProperties.getProperty("storeFile")
            if (sFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.webrtc)
    implementation(libs.webrtc.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.timber)
    implementation(libs.play.services.mlkit.barcode.scanning)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.guava)
    implementation(libs.zxing.core)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
