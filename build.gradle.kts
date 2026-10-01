plugins {
    kotlin("multiplatform") version "2.1.0" apply false
    kotlin("android") version "2.1.0" apply false
    id("com.android.application") version "8.7.0" apply false
    id("com.android.library") version "8.7.0" apply false
    id("org.jetbrains.compose") version "1.7.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("app.cash.sqldelight") version "2.0.2" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
    // ktlint 管「格式」，detekt 管「代码质量」，两者互补（detekt 不做格式化）
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1" apply false
}

// detekt 全局配置
//
// ⚠️ 关键修复：detekt 默认只扫描 `src/main/java` / `src/main/kotlin`，
// 完全扫不到 KMP 的 `src/commonMain/kotlin`、`src/androidMain/kotlin` 等源集。
// 这会导致「报告显示 0 问题」的假象 —— 实测此前只统计到 2 个文件 / 46 行代码，
// 而项目有 160+ 个源文件。因此这里显式指定各源集路径。
allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    detekt {
        config.setFrom("${rootProject.projectDir}/config/detekt/detekt.yml")
        buildUponDefaultConfig = true
        parallel = true
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        val candidateDirs = listOf(
                "src/commonMain/kotlin",
                "src/androidMain/kotlin",
                "src/desktopMain/kotlin",
                "src/jvmMain/kotlin",
                "src/main/kotlin",
                "src/commonTest/kotlin",
                "src/desktopTest/kotlin",
            ).map { projectDir.resolve(it) }.filter { it.exists() }

        if (candidateDirs.isNotEmpty()) {
            setSource(files(candidateDirs))
        }

        reports {
            html.required.set(true)
            md.required.set(true)
        }
    }

    // ── ktlint ──
    // 只做格式化检查（缩进、导入顺序、尾随逗号、行尾空白等），
    // 与 detekt 的代码质量检查互补 —— detekt 并不负责格式化。
    // 具体风格由根目录的 .editorconfig 决定，避免两处各配一套规则。
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        // 插件默认带的 ktlint 版本解析器偏旧，对本项目的 Kotlin 2.1 代码会
        // 误报 "failed to parse file"（而代码本身能正常编译）。指定新版即可。
        version.set("1.5.0")
        // 不启用 Android 专属的布局检查
        android.set(false)
        // CI 要能真正拦住格式问题，不允许静默通过
        ignoreFailures.set(false)
        filter {
            // 排除构建产物；统一成 / 分隔以兼容 Windows
            // 另外排除 Gradle 脚本：build.gradle.kts 里大量 apply { } / configure { } 链式块，
            // ktlint 的缩进与换行规则与 Gradle 官方风格不一致，强行套用会让构建脚本更难读。
            // 格式规范主要面向 src 下的 Kotlin 源码。
            exclude { element ->
                val p = element.file.path.replace('\\', '/')
                p.contains("/build/") || p.endsWith(".gradle.kts")
            }
        }
    }

    // ktlint 插件对「根项目自己的 build.gradle.kts」不走上面的 filter / .editorconfig
    // （实测该文件仍会被 ktlintKotlinScriptCheck 检查并报缩进类问题）。
    // 构建脚本的格式不影响任何交付物，直接停掉这个任务。
    tasks.matching { it.name.contains("ktlintKotlinScript") }.configureEach {
        enabled = false
    }
}
