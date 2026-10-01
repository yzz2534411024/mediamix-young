plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

// ⚠️ 锁定 compose 系版本与 1.7.1 统一：navigation-compose 2.8.0-alpha13 会把
// compose.runtime/annotation/collection 升到 1.8.0-alpha03、skiko 升到 0.8.18，
// 与 1.7.1 的 ui/foundation 混用导致桌面端启动即崩（RenderNodeContext
// UnsatisfiedLinkError / NoClassDefFoundError，实测 2026-10-01）。
configurations.all {
    resolutionStrategy.force(
        "org.jetbrains.skiko:skiko:0.8.4",
        "org.jetbrains.compose.runtime:runtime:1.7.1",
        "org.jetbrains.compose.annotation-internal:annotation:1.7.1",
        "org.jetbrains.compose.collection-internal:collection:1.7.1",
    )
}

kotlin {
    jvm {
        withJava()
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        val jvmMain by getting {
            // ⚠️ 本模块的 target 名是 jvm，但源码与资源一直放在 src/desktopMain 下。
            // 不显式登记的话，jvmMain 源集是空的（构建时报 compileKotlinJvm NO-SOURCE），
            // src/desktopMain/kotlin 不会被编译 —— 打包出的桌面版会一直沿用历史遗留的 class。
            kotlin.srcDir("src/desktopMain/kotlin")
            resources.srcDir("src/desktopMain/resources")

            dependencies {
                implementation(project(":shared"))
                implementation(project(":composeUi"))
                implementation(libs.compose.material3)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.compose.preview)
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor)
                implementation(libs.koin.compose)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// 图标资源由 jvmMain 源集的 resources.srcDir 负责进 jar，
// 打包成 exe 时的图标由 nativeDistributions.windows.iconFile 指定，两者同源（design/icon）。

compose.desktop {
    application {
        mainClass = "com.mediamix.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                // org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi
            )
            packageName = "MediaMix"
            packageVersion = "1.0.0"
            description = "MediaMix - Cross-platform video player"
            copyright = "© 2026 MediaMix. All rights reserved."

            windows {
                menuGroup = "MediaMix"
                upgradeUuid = "515f9605-df43-4595-94d6-aec464c14eec"
                // 图标与 Android 端同源，均由 design/icon/build-icons.js 从 SVG 生成。
                // 注意 iconFile 属于各平台块（windows/macOS/linux），不挂在 nativeDistributions 上。
                // macOS 需要 .icns、Linux 需要 512 PNG，本次只针对 Windows 出包。
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
            }

            jvmArgs(
                "-Xmx2g",
                "-Dfile.encoding=UTF-8",
            )
        }
    }
}
