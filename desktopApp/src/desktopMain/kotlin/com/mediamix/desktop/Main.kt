package com.mediamix.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mediamix.shared.di.sharedModule
import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.network.HttpClientFactory
import com.mediamix.shared.spider.SpiderService
import com.mediamix.shared.spider.TvBoxSelfTestRunner
import com.mediamix.ui.App
import com.mediamix.ui.di.uiModule
import com.mediamix.ui.theme.MediaMixTheme
import com.mediamix.ui.theme.ThemeConfig
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import java.io.File

fun main(args: Array<String>) {
    // 命令行自检模式：MediaMix.exe --self-test
    // 跳过 UI/Koin/数据库，独立跑 TVBox 接口自检，结果打印到 stdout —— 供自动化测试。
    if ("--self-test" in args) {
        runSelfTestCli()
        return
    }
    startKoin {
        modules(sharedModule, uiModule)
    }
    application {
        val appIcon = remember { loadAppIcon() }
        Window(
            onCloseRequest = ::exitApplication,
            title = "MediaMix",
            // 窗口 / 任务栏图标。打包进 exe 的图标由 build.gradle.kts 的 windows.iconFile 指定，
            // 这里管的是运行期窗口本身（含任务栏缩略图）。
            icon = appIcon,
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            val themeMode by ThemeConfig.themeMode.collectAsState()
            MediaMixTheme(themeMode = themeMode) {
                App()
            }
        }
    }
}

/**
 * 命令行 TVBox 接口自检（`MediaMix.exe --self-test`）。
 *
 * 不启动 Koin/DB/UI —— SpiderService 零依赖可构造，CmsApiSite.defaultSites 是纯数据。
 * 退出码：全部通过 = 0，任一环节失败 = 1。
 */
private fun runSelfTestCli() {
    val sites = CmsApiSite.defaultSites.filter { it.isTvBox }
    val httpClient = HttpClientFactory.createHttpClient(
        connectTimeoutSeconds = 10,
        // 蜘蛛包可能上兆（老刘备 fty.jar 实测 >20s），请求超时给足
        requestTimeoutSeconds = 60,
    )
    val runner = TvBoxSelfTestRunner(spiderService = SpiderService(), httpClient = httpClient)
    var failed = 0
    var total = 0
    runBlocking {
        sites.forEach { site ->
            println()
            println("########## ${site.name} [${site.key}] ##########")
            val results = runner.run(site)
            results.forEach { item ->
                println("${if (item.ok) "[PASS]" else "[FAIL]"} ${item.name}: ${item.detail}")
            }
            total += results.size
            failed += results.count { !it.ok }
        }
    }
    println()
    println("=== 全部 TVBox 源（${sites.size} 个）：$total 项检查，${total - failed} 通过 / $failed 失败 ===")
    if (failed > 0) kotlin.system.exitProcess(1)
}

/**
 * 加载应用图标。
 *
 * 图标缺失不该让应用起不来 —— 早先直接用 painterResource() 时，资源一旦没打进 jar
 * 就会在启动瞬间抛异常。这里按下面顺序尝试，全失败就返回 null（窗口用系统默认图标）：
 *
 *  1. classpath：正常打包后 icon.png 位于 jar 根（由 build.gradle.kts 的 jvmJar 挂入）
 *  2. Compose Desktop 的 appResources 目录（安装后位于 exe 同级的 app/resources）
 *  3. 开发期的工作目录相对路径
 */
private fun loadAppIcon(): Painter? {
    // 用匿名对象拿 classloader：MainKt 是文件名生成的类，在文件内部无法直接引用。
    // ClassLoader.getResourceAsStream 不接受前导 "/"（那是 Class.getResourceAsStream 的写法）。
    val loader: ClassLoader? = object {}.javaClass.classLoader
    val stream =
        loader?.getResourceAsStream("icon.png")
            ?: System
                .getProperty("compose.application.resources.dir")
                ?.let { File(it, "icon.png").takeIf(File::isFile)?.inputStream() }
            ?: File("desktopApp/src/desktopMain/resources/icon.png")
                .takeIf(File::isFile)
                ?.inputStream()
            ?: return null

    return runCatching { stream.use { BitmapPainter(loadImageBitmap(it)) } }.getOrNull()
}
