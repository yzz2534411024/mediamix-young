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
}

// detekt 全局配置
allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    detekt {
        config.setFrom("${rootProject.projectDir}/config/detekt/detekt.yml")
        buildUponDefaultConfig = true
        parallel = true
    }
}
