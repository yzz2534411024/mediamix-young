plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
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
            implementation(libs.kotlin.stdlib)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.koin.core)
            implementation(libs.kermit)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.jsoup)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.mockk)
            implementation(libs.turbine)
            implementation(libs.multiplatform.settings.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.driver.android)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.hls)
            implementation(libs.media3.exoplayer.dash)
            // TVBox 蜘蛛生态的宿主契约依赖：spider.jar 里的 csp_* 类大量引用
            // okhttp3/gson/jsoup（网络、解析），宿主必须自带，否则壳线程池里
            // NoClassDefFoundError 会直接杀进程（实测 WoGG 站点）
            implementation("com.squareup.okhttp3:okhttp:4.12.0")
            implementation("com.google.code.gson:gson:2.10.1")
            implementation("org.jsoup:jsoup:1.17.2")
            // 边播边缓存的 CacheDataSource/SimpleCache 在 media3-datasource（上面 exoplayer 的传递依赖）
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.sqldelight.driver.jvm)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.jna)
                implementation(libs.jna.platform)
                // libmpv 按需下载：官方 dev 包是 7z（BCJ2 过滤器），Java 侧用它们解
                implementation(libs.commons.compress)
                implementation(libs.xz)
                // Force a sqlite-jdbc version compatible with JDK 21
                implementation("org.xerial:sqlite-jdbc:3.45.1.0")
            }
        }
    }
}

android {
    namespace = "com.mediamix.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("MediaMixDatabase") {
            packageName.set("com.mediamix.shared.database")
            // Skip migration verification — sqlite-jdbc native lib may differ across JDK versions
            verifyMigrations.set(false)
        }
    }
}

// Disable migration verification tasks completely (sqlite-jdbc compatibility workaround)
tasks.matching { it.name.contains("verify") && it.name.contains("Migration") }.configureEach {
    enabled = false
}
