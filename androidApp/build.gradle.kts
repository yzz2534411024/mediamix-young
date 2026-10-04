import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

// 读取签名配置：优先环境变量 (CI)，其次 local.properties (本地开发)
val keystoreProps =
    Properties().apply {
        val envStore = System.getenv("KEYSTORE_PATH")
        if (envStore != null) {
            put("storeFile", envStore)
            put("storePassword", System.getenv("KEYSTORE_PASSWORD") ?: "")
            put("keyAlias", System.getenv("KEY_ALIAS") ?: "")
            put("keyPassword", System.getenv("KEY_PASSWORD") ?: "")
        } else {
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) {
                load(FileInputStream(localPropsFile))
            }
        }
    }

android {
    namespace = "com.mediamix.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mediamix.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProps.getProperty("storeFile")
            if (!storeFilePath.isNullOrEmpty()) {
                this.storeFile = rootProject.file(storeFilePath)
                this.storePassword = keystoreProps.getProperty("storePassword", "")
                this.keyAlias = keystoreProps.getProperty("keyAlias", "")
                this.keyPassword = keystoreProps.getProperty("keyPassword", "")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val storeFile = keystoreProps.getProperty("storeFile")
            if (!storeFile.isNullOrEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":composeUi"))
    implementation(libs.activity.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)
    implementation(libs.koin.compose)
}
