plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

// ⚠️ 锁定 compose 系版本与 1.7.1 统一：navigation/coil/koin 的传递依赖把部分
// compose 构件升到 1.8.0-alpha03（实测包里 animation-core 1.8.0-alpha03 与
// animation 1.7.1 并存），导致 ui-graphics 类引用 skiko 0.8.18 独有 API 时
// 与 1.7.1 的 skiko 解析错配，桌面端启动即崩（实测 2026-10-01）。
// compose 1.7.1 的正确 skiko 就是 0.8.18（ui-unit 1.7.1 原配依赖，勿锁 skiko）。
configurations.all {
    resolutionStrategy.force(
        "org.jetbrains.compose.animation:animation:1.7.1",
        "org.jetbrains.compose.animation:animation-core:1.7.1",
        "org.jetbrains.compose.foundation:foundation:1.7.1",
        "org.jetbrains.compose.foundation:foundation-layout:1.7.1",
        "org.jetbrains.compose.material3:material3:1.7.1",
        "org.jetbrains.compose.material:material:1.7.1",
        "org.jetbrains.compose.runtime:runtime:1.7.1",
        "org.jetbrains.compose.runtime:runtime-saveable:1.7.1",
        "org.jetbrains.compose.ui:ui:1.7.1",
        "org.jetbrains.compose.ui:ui-geometry:1.7.1",
        "org.jetbrains.compose.ui:ui-graphics:1.7.1",
        "org.jetbrains.compose.ui:ui-text:1.7.1",
        "org.jetbrains.compose.ui:ui-unit:1.7.1",
        "org.jetbrains.compose.ui:ui-util:1.7.1",
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
            packageName = "CatVideo"
            packageVersion = "1.0.0"
            description = "CatVideo - Cross-platform video player"
            copyright = "© 2026 CatVideo. All rights reserved."

            // ⚠️ jlink 精简 runtime 的默认模块集很小（java.base/desktop/logging 等）：
            // - java.sql：SQLDelight 的 JDBC 驱动（历史/收藏/播放进度）
            // - jdk.unsupported：okhttp/协程的 sun.misc 兜底
            // - jdk.httpserver：播放链路里的本地缓存代理（JdkLocalProxyServer，
            //   用 com.sun.net.httpserver）—— 缺了它点播放直接 NoClassDefFoundError
            //   白屏（实测 2026-10-02，用户日志定位）
            modules("java.sql", "jdk.unsupported", "jdk.httpserver")

            windows {
                menuGroup = "CatVideo"
                upgradeUuid = "515f9605-df43-4595-94d6-aec464c14eec"
                // 图标与 Android 端同源，均由 design/icon/build-icons.js 从 SVG 生成。
                // 注意 iconFile 属于各平台块（windows/macOS/linux），不挂在 nativeDistributions 上。
                // macOS 需要 .icns、Linux 需要 512 PNG，本次只针对 Windows 出包。
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
            }

            jvmArgs(
                "-Xmx2g",
                "-Dfile.encoding=UTF-8",
                // 启动优化：CDS（类数据共享）。首次运行结束时自动生成归档，之后启动
                // 直接映射已解析的类元数据，省掉大量类加载/校验（实测 JVM 启动 ~2.5s，
                // 这项通常能砍掉 0.3~0.8s）。$APPDIR 由 jpackage 启动器展开；
                // 解压版目录可写即生效，装到只读目录时 JVM 会自行忽略，不会报错。
                "-XX:+AutoCreateSharedArchive",
                "-XX:SharedArchiveFile=\$APPDIR/catvideo.jsa",
            )
        }
    }
}
