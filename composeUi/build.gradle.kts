plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    jvm("desktop") {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.preview)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.coil.compose)
            implementation(libs.kotlinx.coroutines.core)
            // ⚠️ navigation 2.8.0-alpha13 的传递依赖会把桌面 compose.runtime/ui 升到
            // 1.8.0-alpha03、skiko 到 0.8.18 —— 与 1.7.1 的 foundation/material 混用，
            // 桌面端启动即崩（RenderNodeContext NoClassDefFoundError，实测）。
            // 排除其 compose 传递依赖，统一走本文件上面锁定的 1.7.1。
            // Android 端不受影响（androidx.compose 是不同 group）。
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha13") {
                exclude(group = "org.jetbrains.compose.runtime")
                exclude(group = "org.jetbrains.compose.annotation-internal")
                exclude(group = "org.jetbrains.compose.collection-internal")
                exclude(group = "org.jetbrains.skiko")
            }
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(compose.materialIconsExtended)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
            implementation(libs.kotlinx.datetime)
            implementation(libs.multiplatform.settings)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.mockk)
            implementation(libs.turbine)
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.compose.ui)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.jna)
                implementation(libs.jna.platform)
            }
        }
    }
}

android {
    namespace = "com.mediamix.ui"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
