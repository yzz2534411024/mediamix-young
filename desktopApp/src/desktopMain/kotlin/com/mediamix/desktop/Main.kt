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
    // 日志落盘必须在任何输出之前：jpackage 启动器是 GUI 子系统程序，
    // stdout/stderr 不会进父进程（批处理）的重定向 —— 用户实测
    // 「日志模式.bat」抓不到任何内容。改为应用自己写文件。
    installFileLogging(args)

    // 命令行自检模式：MediaMix.exe --self-test
    // 跳过 UI/Koin/数据库，独立跑 TVBox 接口自检，结果打印到 stdout —— 供自动化测试。
    if ("--self-test" in args) {
        runSelfTestCli()
        return
    }
    // 播放链路自检：MediaMix.exe --mpv-probe [url]
    // 造一个真实窗口 + HWND，走与播放页完全相同的 mpv 路径播一个测试片，
    // 用于在无人点击 UI 的情况下验证「mpv 加载 → wid 绑定 → 出画/推进」。
    if ("--mpv-probe" in args) {
        runMpvProbe(args)
        return
    }
    // 逐 API 探针：定位 JNA "Invalid memory access" 具体出在哪个 mpv 调用
    if ("--mpv-raw" in args) {
        val frame = javax.swing.JFrame("mpv raw probe")
        val canvas = java.awt.Canvas()
        frame.add(canvas)
        frame.setSize(320, 180)
        frame.isVisible = true
        Thread.sleep(1200)
        val wid = com.mediamix.shared.player.awtComponentId(canvas)
        println("=== mpv 逐步 API 探针 (wid=$wid) ===")
        com.mediamix.shared.player.mpvStepProbe(wid).forEach { println("  $it") }
        runCatching { frame.dispose() }
        kotlin.system.exitProcess(0)
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
 * 日志落盘。
 *
 * ⚠️ 为什么不能靠 `MediaMix.exe > run-log.txt`：jpackage 生成的启动器是 Windows
 * **GUI 子系统**程序，没有控制台，stdout 不会流向父进程（批处理）的重定向 ——
 * 实测用户拿到的 run-log.txt 是空的。因此改为应用自己把 System.out/err 重定向到
 * 文件，Kermit 的默认 writer（println）与异常堆栈都会随之进去。
 *
 * mpv 的原生报错另经 `MPV_EVENT_LOG_MESSAGE` 事件转成 Kermit 日志 ——
 * GUI 抓不到 mpv 的 stderr，只能走事件（见 MpvPlayerEngine.handleLogMessage）。
 *
 * 默认路径 `%USERPROFILE%\mediamix-logs\mediamix-<时间戳>.log`；
 * 也可用 `--log-file <path>` 指定。
 */
private fun installFileLogging(args: Array<String>) {
    val explicit =
        if ("--log-file" in args) {
            args.getOrNull(args.indexOf("--log-file") + 1)
        } else {
            null
        }
    val file =
        explicit?.let { File(it) } ?: run {
            val dir = File(System.getProperty("user.home"), "mediamix-logs")
            dir.mkdirs()
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(java.util.Date())
            File(dir, "mediamix-$stamp.log")
        }
    runCatching {
        file.parentFile?.mkdirs()
        val ps = java.io.PrintStream(java.io.FileOutputStream(file, true), true, "UTF-8")
        System.setOut(ps)
        System.setErr(ps)
        println("[log] 本次运行日志: ${file.absolutePath}")
    }
}

/**
 * 命令行 TVBox 接口自检（`MediaMix.exe --self-test`）。
 *
 * 不启动 Koin/DB/UI —— SpiderService 零依赖可构造，CmsApiSite.defaultSites 是纯数据。
 * 退出码：全部通过 = 0，任一环节失败 = 1。
 */
/**
 * 播放链路自检（`MediaMix.exe --mpv-probe [url]`）。
 *
 * 造一个真实 AWT 窗口（拿 HWND）→ PlayerEngine.initialize() → setSurface(HWND)
 * → setSource(测试片) → play()，随后轮询位置 / 状态 / 缓冲。
 *
 * 关键价值：`wid` 必须在 mpv_initialize() 之前设置，而生产路径里 HWND 回调早于
 * initialize —— 这条链路此前坏了（画面全白）。这里能自动化复现/验证它。
 */
private fun runMpvProbe(args: Array<String>) {
    val idx = args.indexOf("--mpv-probe")
    val url =
        args.getOrNull(idx + 1)
            ?: "https://media.w3.org/2010/05/sintel/trailer.mp4"

    println("=== MediaMix 播放链路自检 ===")
    println("测试地址: $url")

    val frame = javax.swing.JFrame("MediaMix mpv probe")
    val canvas = java.awt.Canvas()
    canvas.background = java.awt.Color.BLACK
    frame.add(canvas)
    frame.setSize(640, 360)
    frame.setLocationRelativeTo(null)
    frame.isVisible = true
    Thread.sleep(1500) // 等窗口真正显示（HWND 才有效）

    val hwnd = com.mediamix.shared.player.awtComponentId(canvas)
    println("窗口 HWND: $hwnd")
    if (hwnd == 0L) {
        println("✗ 拿不到 HWND，无法继续")
        kotlin.system.exitProcess(1)
    }

    val engine = com.mediamix.shared.player.PlayerEngine()
    engine.setListener(
        object : com.mediamix.shared.player.PlayerEngineListener {
            override fun onStateChanged(state: com.mediamix.shared.player.PlayerState) {
                println("  [state] $state")
            }

            override fun onPositionChanged(positionMs: Long) = Unit

            override fun onBufferChanged(bufferedPercent: Int) = Unit

            override fun onError(
                error: String,
                code: Int?,
            ) {
                println("  [error] $error")
            }

            override fun onFirstFrameRendered() {
                println("  [first-frame] ✓")
            }

            override fun onPlaybackEnded() {
                println("  [ended]")
            }
        },
    )

    val ok =
        runCatching {
            println("  step: engine.initialize()")
            engine.initialize()
            println("  step: engine.setSurface(hwnd)")
            engine.setSurface(hwnd)
            println("  step: engine.setSource(url)")
            engine.setSource(url)
            println("  step: engine.play()")
            engine.play()
        }.fold(onSuccess = { true }, onFailure = {
            println("✗ 启动失败: ${it.javaClass.simpleName}: ${it.message}")
            it.printStackTrace() // JNA 的堆栈能指出崩在哪个 native 调用
            false
        })

    if (!ok) {
        runCatching { frame.dispose() }
        kotlin.system.exitProcess(1)
    }

    var lastPos = 0L
    var advanced = false
    repeat(12) {
        Thread.sleep(2000)
        val pos = runCatching { engine.getPosition() }.getOrDefault(0L)
        val state = runCatching { engine.getPlayerState() }.getOrDefault(com.mediamix.shared.player.PlayerState.IDLE)
        val buf = runCatching { engine.getBufferedPercentage() }.getOrDefault(0)
        println("  t=${(it + 1) * 2}s pos=${pos}ms state=$state buffered=$buf%")
        if (pos > lastPos) advanced = true
        lastPos = pos
    }

    runCatching { engine.release() }
    runCatching { frame.dispose() }
    println(if (advanced) "=== 结果: ✓ 画面推进正常（position 有增长）===" else "=== 结果: ✗ position 未推进 ===")
    kotlin.system.exitProcess(if (advanced) 0 else 1)
}

private fun runSelfTestCli() {
    // mpv 运行库可用性 —— 桌面端播放的前提，先测它省得等用户点播放才发现缺 dll。
    // 打包内置的 libmpv-2.dll 会由 MpvLib 从 jar 资源解压到临时目录后加载。
    println("=== MediaMix 桌面端自检 ===")
    val mpvStatus =
        runCatching {
            com.mediamix.shared.player.MpvLib.getInstance()
            "OK（已加载）"
        }.fold(
            onSuccess = { it },
            onFailure = { "FAIL: ${it.javaClass.simpleName}: ${it.message?.take(100)}" },
        )
    println("[mpv 运行库] $mpvStatus")

    // 播放链路里的本地缓存代理（JdkLocalProxyServer 用 com.sun.net.httpserver）——
    // 该 API 需要 jlink runtime 含 jdk.httpserver 模块，缺了就是点播放即白屏
    // （NoClassDefFoundError，实测用户日志定位）。
    val proxyStatus =
        runCatching {
            val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            server.start()
            val port = server.address.port
            server.stop(0)
            "OK（可创建，测试端口 $port）"
        }.fold(
            onSuccess = { it },
            onFailure = { "FAIL: ${it.javaClass.simpleName}: ${it.message}" },
        )
    println("[本地缓存代理 jdk.httpserver] $proxyStatus")
    println()

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
