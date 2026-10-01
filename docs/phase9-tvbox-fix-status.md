# 阶段九 · 饭太硬全链路打通 + 播放器/缓存/下载重设计（实施记录）

> 承接 `phase8-tvbox-status.md`。阶段八把 jar 蜘蛛桥推到了「43 个站点拿到 `getSpider`
> 实例、`homeContent` 调用已发出」，本文档记录按方案把**业务层链路真正打通**的改动。
>
> 核心结论：**阶段八之后真正的阻断不在桥接层，而在业务层** —— 详情页对 TVBox 源
> 直接抛异常，所以即使桥完美工作，饭太硬也没有任何路径能播。

---

## 1. 关键诊断（逐条已核实并修复）

### A. 业务层硬阻断 —— 桥修好了也走不到播放

| # | 位置 | 问题 | 修复 |
|---|---|---|---|
| A1 | `VideoDetailViewModel` | `if (site.isTvBox) throw SourceUnavailableException(...)`，播放链路无旁路 | 删除；TVBox 走 `SourceContentGateway.loadDetail` |
| A2 | `VideoHomeViewModel` | 点分类仍调 `homeContent(...).recommend` | 改走 `gateway.loadList(tid)` → `categoryContent(tid, page)` |
| A3 | `VideoHomeViewModel` | 47 个站点当分类、`typeId = index + 1` 伪装 typeId，从未进入站内 class 层级 | 两层目录：一级站点（`SiteNode`）→ 二级站内 class（`SpiderCategory`） |
| A4 | `JavaBridgeSpider` / `VideoDetailViewModel` | TVBox 影片 `sourceKey` 只有站点 key，详情页反查不到 | 新增 `SourceRef`，sourceKey 为 `配置源::站点` |
| A5 | `PlayerScreen.buildPlaybackHeaders` | 靠 URL 猜 Referer；蜘蛛给的真实请求头从未被使用 | 会话携带真实 headers，`buildPlaybackHeaders` 降级为兜底 |
| A6 | `PlaybackSessionStore.sessionFor` | 按 URL 字符串对齐；TVBox 的「标识 ≠ 解析后地址」→ 选集/回退静默失效 | 增加 `resolveKey`，同时接受标识与解析后地址 |

### B. jar 蜘蛛桥缺陷

| # | 问题 | 修复 |
|---|---|---|
| B1 | `repeat(3)` 内层无条件跑完两个候选 key，后者 null 会覆盖已成功的实例 → 误报「未取到实例」，且成功也要白等 4 秒 | 抽 `pickSpiderInstance`（`firstNotNullOfOrNull`），结构上保证「取到即停」；重试退避 2s/4s |
| B2 | 壳 `Init` 被 `newInstance()` 两次 | 收敛到 `ensureShellInit()`，`release()` 一并清 `shellInitInstance`/`siteInited` |
| B3 | `.split("$$\$")` 实为 `"$$"`，TVBox 约定是 `$$$` → 多线路解析必错 | 删除手工拆解，复用 `VideoDetail.fromJson`（已按 `$$$` + `indexOf('$')` 正确实现） |
| B4 | 读 `map["headers"]`（对象），约定是 `header`（JSON 字符串） | `SpiderPlayResult` 补 `header/jx/format/playUrl` + `headerMap()` 兼容两种形态 |

### C. 配置层「静默失效」

| # | 问题 | 修复 |
|---|---|---|
| C1 | `parseSpiderUrl` 截掉 `;md5;<hash>` → md5 校验与「按 md5 缓存」双双失效，每次冷启重下 1.1MB | `TvBoxConfig` 新增 `spiderSpec` 保留原文；`loadSpiderJar` 从任意段识别 32 位 hex 作为 md5 |
| C2 | `SourceRepository` 落盘的是常量串 `${it.key}:${it.value}` → 测速延迟从未持久化 | 改为真正的模板字符串 |
| C3 | 饭太硬只写死 `.net`，无多线路 | `CmsApiSite.apiUrlCandidates` + 网关线路探测（8s/条）+ 成功后写回仓库 |

### D. 播放器 / 缓存 / 下载

- **「引擎有 API、UI 无入口」的断点在中间层**：`PlayerCoreManager` 现在透传
  `getAudioTracks/getVideoTracks/setAudioTrack/setVideoTrack`，`PlayerMoreSheet`
  新增「音轨 / 视频轨 / 画质」入口（列表为空时隐藏，不做假按钮）。
- **剧集状态改事件驱动**：新增 `onEpisodeChanged`，删掉 500ms 轮询。
- **三套缓存**：新增 `CacheManager` 门面统一「读数 + 清理」；`DiskCache` 的 GBK 乱码
  画质数组改为引用 `QualityLevel`；Android 上 `CacheEngineImpl` 直连（不再绕恒等代理）。
- **下载链路**：m3u8 从「裸 GET 存成 .mp4」改为「解析清单 → 并发拉分片 → 按序合并」；
  请求头随任务持久化；断点续传；并发上限 3 路。

---

## 2. 架构落点

### 2.1 `SourceContentGateway`（shared/commonMain，Koin single）

**唯一**分流「CMS 协议」与「TVBox 蜘蛛」的地方。ViewModel 里已无任何 `if (site.isTvBox)`：

```kotlin
suspend fun loadCatalog(site): HomeCatalog          // Flat(CMS) | Tree(站点列表)
suspend fun loadSiteClasses(site, siteKey)          // 站内 class（按站点缓存）
suspend fun loadList(site, siteKey, tid, page)      // categoryContent / ac=detail
suspend fun loadDetail(site, vodId, sourceKey): VideoDetail
suspend fun resolvePlay(site, flag, episodeId, sourceKey): ResolvedPlay  // 含 header + parse==1
```

UI 只做一次 `when (catalog)`：CMS 渲染一行分类 chip，TVBox 渲染**两行**（站点行 + class 行）。

### 2.2 `PlaybackResolver`（shared/commonMain）

`CmsApiSite` 的查找依赖 UI 层的 `SourceRepository`（自定义源只存在于用户配置里），
因此把「反查站点 + 调网关 + 必要时补建蜘蛛」收敛到一个详情页与播放页共用的对象，
TVBox 的**切集/连播也能按需解析**（不再只能播第一集）。

### 2.3 按需解析剧集（`PlayerCoreManager.episodeResolver`）

TVBox 的 `episodeUrls` 存的是 `playerContent` 的入参（标识，不是地址）。
切集/自动连播发生在管理器内部，因此由管理器在装载前回调解析器换出真实地址与请求头；
解析失败**不装载**（拿着无效标识去请求必然失败，还会把排查方向带偏）。

### 2.4 新增文件

| 文件 | 作用 |
|---|---|
| `shared/.../services/SourceContentGateway.kt` | 协议分流唯一入口 |
| `shared/.../services/PlaybackResolver.kt` | 剧集标识 → 真实地址（详情页/播放页共用） |
| `shared/.../services/DownloadService.kt` | HLS 分片合并 / 请求头 / 续传 / 并发 |
| `shared/.../services/M3u8Parser.kt` | m3u8 纯字符串解析（Desktop 可测） |
| `shared/.../services/M3u8SegmentStore.kt` | 分片暂存（断点续传落点） |
| `shared/.../services/DownloadFileSink.kt` | expect/actual 文件写入端 |
| `shared/.../cache/CacheManager.kt` | 缓存门面（统一读数与清理） |
| `shared/.../spider/SpiderHealthStore.kt` | 站点连续失败标记 |
| `shared/.../spider/SpiderInstancePicker.kt` | 候选遍历「取到即停」的纯函数 |
| `shared/.../core/PlatformInfo.kt` | 编译期平台标识（expect/actual） |

---

## 3. 验证

### 3.1 统一门禁（本次全部通过）

```bash
./gradlew :shared:compileKotlinDesktop :composeUi:compileKotlinDesktop \
          :shared:desktopTest :composeUi:desktopTest \
          detekt ktlintCheck
./gradlew :androidApp:assembleDebug
```

- `:shared:detekt` 加权问题数 **50/50**（配置上限 50）—— 本次新增代码已清零。
- 新增测试：`M3u8ParserTest`（清单/分片/加密/URL 补全）、`SourceRefTest`（复合标识往返 +
  `CmsApiSiteCandidatesTest` 多线路）、`SpiderHealthStoreTest`（阈值/清零/持久化）、
  `DiskCacheQualityTest`（画质标签一致 + 优先级命中）。

### 3.2 真机端到端判据

```bash
ADB="D:/Android/platform-tools/adb.exe"; export MSYS_NO_PATHCONV=1
$ADB logcat -G 16M && $ADB logcat -c
$ADB shell am force-stop com.mediamix.app.debug
$ADB shell am start -n com.mediamix.app.debug/com.mediamix.android.MainActivity
$ADB logcat -d | grep -E "JavaBridgeManager|SourceContentGateway|PlayerCore|ExoPlayer"
```

链路：首页选饭太硬 → **站点行**点站点 → `homeContent` 返回 `class` →
**class 行**点分类 → `categoryContent` 出列表 → 点影片 → 详情出线路/剧集 →
点第 1 集 → `playerContent` 成功 → `setSource(url, headers=...)` → 出画。

**阶段 0 的判定依据**：诊断页「TVBox 蜘蛛桥」区块新增**一键探测**，
依次跑 `homeContent → categoryContent → detailContent → playerContent`，
结果写成可复制文本。判据：

- `homeContent` 的 `list` 长度 > 0 且后续步骤走通 → 链路通畅；
- `list` 缺失/为 0 → 站点自身没吐数据，**不是 app 缺陷**；
- 「建蜘蛛」为 0 → 壳解密/类名问题，看 logcat 的 `JavaBridgeManager`。

---

## 4. 行为变化（用户可感知）

| 变化 | 说明 |
|---|---|
| 首页 TVBox 变成两行 chip | 先选站点，再选站内分类；不再是「47 个假分类」 |
| 首屏不再白等 | 不再对 43 个站点并发 `homeContent`；只拉选中站点 |
| 站点连续失败 3 次会被隐藏 | 由上表健康度记录决定，诊断页可清除重试 |
| 备用线路自动切换 | `.net` 挂了会试 `.com`/`.top`，成功后写回 |
| TVBox 支持切集/连播 | 按需 `playerContent` 解析（原先只能播第一集） |
| 播放器「更多」新增音轨/视频轨/画质 | 引擎早就有 API，原先 UI 无入口 |
| 上/下一集与自动连播即时刷新 | 事件驱动取代 500ms 轮询 |
| 下载支持 m3u8 | 产物是可播文件（`.ts`），不再是一份文本清单 |
| 下载带请求头、可续传、并发上限 3 | 防盗链 CDN 不再 403；中断不必从头再来 |
| 数据源管理可导入 TVBox 配置 | 粘贴地址 → 自动判定 CMS/TVBox → 探测后落库 |

---

## 5. 明确不做 / 后置

| 项 | 原因 |
|---|---|
| Desktop 端 jar 桥 | JVM 无法执行 dex（保持现状） |
| `drpy`(`.js`) 站点 | 项目无 JS 引擎，配置里 3 个 `dr_*` 保持不可用 |
| 直播模块 | 用户已排除 |
| `hosts` 覆写 | **P2 后置**。只能作用于自己发的请求（配置拉取/图片/直播），jar 蜘蛛内部用自带的 OkHttp，壳未暴露注入点，拦不住 —— 收益有限 |
| ExoPlayer `SimpleCache` 纳入 `CacheManager` | 由 Media3 托管，清理需释放 `SimpleCache` 实例，属独立改动 |

---

## 6. 后续任务

| 优先级 | 任务 | 说明 |
|---|---|---|
| P0 | 真机跑一次「一键探测」 | 定论 `homeContent` 到底返回什么（唯一判据） |
| P1 | 详情页/播放页「下载本集」入口 | 播放页已完成（带 headers）；详情页可选补 |
| P1 | 阶段 5 剩余：进度轮询三处收敛 | 非前台降频、字幕轮询并入 250ms、AV 同步与进度上报计时器合并 |
| P2 | `hosts` 覆写 | 见上表 |
| P2 | Koin viewModel 作用域（阶段七 P1-6） | 需先验证 Koin 4 KMP 下 DSL 行为 |
