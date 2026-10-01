# 阶段八 · TVBox 可用性攻坚现状（2026-10-01）

> 前置：阶段七的工程化补课（P1 清单 7/9 完成）与边播边缓存已落地。
> 本文档记录 10-01 当天「饭太硬接口攻坚 + jar 蜘蛛桥真机调试」的完整现状，
> 供后续接手的会话/人员快速对齐。提交序列：`317da41 → 16eda74 → 47bc0f4 → 0897afa → db32c38 → aa283b4`。

---

## 1. 饭太硬接口的完整技术画像（全部实测实证）

### 1.1 配置获取

| 项 | 结论 |
|---|---|
| 实际可用地址 | `http://www.xn--sss604efuw.net/tv`（= 饭太硬.net）。**曾误判为抢注占位图，实为真配置** |
| 响应格式 | 「图片伪装」：`FF D8...图片...FF D9 + 8 字节随机标识 + ** + Base64(JSON)`，约 19KB |
| 解码器 | 项目 `TvBoxImageDecoder` **本来就支持**（真实响应回归测试 `TvBoxImageDecoderRealDataTest` 通过） |
| JSON 杂质 | 内含 `//` 注释行（TVBox 停用站点惯例），解析前需剥离（`parseJsonWithComments` 已处理） |
| 官方域名 | `.com` / `.top` 实测直连超时、代理 403/502 —— 当前网络不可达；`.net` 可用。**待做：多线路自动切换** |
| 配置规模 | 47 站点 + spider jar + 直播分组（虎牙/斗鱼/儿童启蒙 3 个 HTTP 直连） |

### 1.2 蜘蛛包（spider jar）

| 项 | 实测结论 |
|---|---|
| 文件形态 | `https://...jpg;md5;<hash>` —— **实为 ZIP 包**（`PK` 头，1.1MB），md5 校验通过 |
| 内容 | 36KB **加固壳** `classes.dex` + `assets/ftyguard_v7/v8.so` + `ftyshinidie.guard`（加密的真实代码） |
| 壳类命名 | `com.github.catvod.spider.<Xxx>Guard`，与配置 `csp_XxxGuard` **直接对应** |
| 真实类定义 | **在解密产物里**，壳 dex 只有字符串引用 —— 直接 `loadClass` Guard 类必然 ClassNotFoundException |
| 壳公开 API | **`Init.getSpider(String) -> Spider`**（native 层管理实例）、`Init.init(Context)`、`Init.loader()` |
| Spider 基类签名 | `init(Context, String ext)` 双参注入站点 ext；`homeContent(Z)` / `categoryContent(String,String,Z,HashMap)` / `detailContent(List)` / `searchContent(String,Z)` / `playerContent(String,String,List)`，返回 JSON 字符串 |

### 1.3 站点构成与可用性

| 类型 | 数量 | 本项目可用性 |
|---|---|---|
| `csp_*` jar 蜘蛛 | 43 | **走壳 API 可用**（getSpider 实例已全部拿到，homeContent 调用已发出；内容映射待验证） |
| HTTP 直连 | 3 | 可用（虎牙/斗鱼直播 m3u8、儿童启蒙） |
| `csp_XPathGuard` | 1 | **无 ext 规则，已剔除**（曾是打 localhost:80 的元凶） |

---

## 2. 真机调试修复清单（荣耀 Magic6 Pro / Android 15，日志级证据）

| # | 问题 | 修复 | 验证 |
|---|---|---|---|
| 1 | `SecurityException: Writable dex file is not allowed`（API 29+ W^X 策略） | 下载后 `setReadOnly()`（TVBox 原版同款） | ✅ 壳 Init 执行成功，native 解密工作 |
| 2 | 自己 `loadClass` Guard 类必然 CNF | 改走壳 API `Init.getSpider(key)` | ✅ 43 站点全部拿到蜘蛛实例 |
| 3 | 站点 ext 未注入 | `init(Context, ext)` 双参调用 | ✅ |
| 4 | `CancellationException` 被吞（破坏取消语义） | rethrow | ✅ |
| 5 | `csp_XPathGuard` 无 ext 打 localhost:80 | XPATH 且 ext 空 → 剔除 | ✅ 测试用例覆盖 |
| 6 | 聚合 43 站点串行过慢 | async/awaitAll 并发 + distinctBy | ✅ |
| 7 | 分类点击每次重拉 TVBox 配置 | `tvBoxConfigCache`，切源清理 | ✅ |
| 8 | 壳解密等待分散在各站点（每站最长 16.5s） | `loadSpiderJar` 里全局探针轮询一次（`csp_DouDouGuard`） | ✅ |

---

## 3. 当前能力状态

**已通**：配置拉取 → 伪装解码 → 站点分类展示（含 jar 站点名）→ jar 下载/校验/缓存 → W^X 绕过 → 壳解密 → getSpider 实例 → homeContent 反射调用（全站发出）。

**未定论（下次真机 30 分钟可验）**：
1. **homeContent 的实际返回内容** —— 已加成功摘要日志（`蜘蛛方法成功: $key.$method → keys=[...]`）。若 keys 含 `list` 但 UI 无数据 → 映射问题；若 keys 为空 → 壳返回空（站点本身没数据）。
2. **壳解密探针是否命中**（`壳解密完成（第 N 轮探针命中）` 日志）—— 决定首启等待时间是否有效。
3. **冷启动首页自动加载的时有时无** —— 疑似 radio 日志刷爆 logcat 缓冲造成的观测假象（`logcat -G 16M` 后未复现异常）。

**架构边界（明确不做）**：Desktop 无法执行 dex（JVM 限制），桌面端 jar 桥维持现状。

---

## 4. 已知限制与体验注意

- 首次进饭太硬：下载 1.1MB 蜘蛛包 + 壳解密 + 43 站点 homeContent（已并发化），预计十几秒到一分钟，之后走缓存。
- 豆豆分类曾显示"还没有影片数据"—— 未定论（见 3.1），成功摘要日志可一次定位。
- TVBox 源的站点随时可能失效（第三方性质），App 的 notice 文案已区分「域名解析失败」「蜘蛛内核不可用」等场景。

---

## 5. 后续任务（按优先级）

| 优先级 | 任务 | 说明 |
|---|---|---|
| P0 | 真机验证 homeContent 返回 | 一次 logcat 定位内容映射 vs 空返回 |
| P0 | 饭太硬多线路自动切换 | `.com → .top → .net` 依次尝试，解决官方域名可达性波动 |
| P1 | 用户自定义 TVBox 配置导入 | 源管理页加"粘贴配置地址"，HTTP 直连站点自动注册 —— 扩大可用面 |
| P1 | P1-6 Koin viewModel 作用域 | 挪自阶段八（需先验证 Koin 4 KMP 下 DSL 行为） |
| P2 | P1-9 伪 common 收敛 | `java.io.File` 等 expect/actual 化 |
| P2 | jar 站点级缓存与失败标记 | 站点连续失败 N 次后 UI 置灰，避免每次白等 |

---

## 6. 复测/调试方法（速查）

```bash
ADB="D:/Android/platform-tools/adb.exe"
$ADB logcat -G 16M                       # 先加大缓冲（系统 radio 日志会刷爆默认缓冲）
$ADB logcat -c && $ADB shell am force-stop com.mediamix.app.debug
$ADB shell am start -n com.mediamix.app.debug/com.mediamix.android.MainActivity
$ADB logcat -d | grep -E "JavaBridgeManager|SpiderService|VideoHome"
```

- 首页选饭太硬 → 分类 → 点站点 → 看 `蜘蛛方法成功: csp_Xxx.homeContent → keys=[...]`
- 诊断页新增「TVBox 蜘蛛桥」区块：`蜘蛛包=已加载/未加载 · 已建蜘蛛 N 个`
- 注意：荣耀每次 adb server 重启要求重新授权（单次会话内完成全部操作）；
  Git Bash 需 `MSYS_NO_PATHCONV=1`；手机息屏会导致点击无效。

---

## 7. ✅ 2026-10-01 19:57：饭太硬全链路打通

真机（荣耀 Magic6 Pro）逐层调试，最后三块拼图：

1. **宿主缺 `com.github.catvod.crawler.Spider` 基类**（终极根因）—— 壳 dex 75 个
   Guard 类全部继承它，基类须由宿主 APK 提供。已补 `Spider.kt`
   （⚠️ 必须 Kotlin：KMP 的 androidTarget **不编译** `src/androidMain/java` 目录，
   Java 版实测未进 APK）。ProGuard 已加 keep 规则。
   → 探针 L1(Init)/L2(DouDouGuard) 双命中。
2. **NetworkOnMainThreadException** —— 壳的 homeContent 内部是 okhttp 同步请求；
   `invokeMethod` 已包 `withContext(Dispatchers.IO)`。壳会吞该异常返回空串，
   表象为「调用成功但没有数据」。
3. **豆瓣源无 `vod_id`**（豆豆影片只有 vod_name/vod_pic/vod_remarks）——
   三处按 vodId 过滤/去重的代码已适配（id 空时用 名字+海报 兜底）：
   `loadSpiderVideos` 过滤、`mergeVideoItems` 去重键、`LazyGrid` item key
   （原空 key 重复会直接崩 App）。

**真机实证（19:57 截屏）**：豆豆┃片单 → 豆瓣热榜真实数据：
轮播「兰香如故 7.5」+ 影片网格（神探之痕迹/无可替代…带评分）+ 7 个站内分类，零崩溃。

**遗留小项**：
- 双路线中 getSpider 后备路线仍未验证（主路线已通）；
- P0 多线路切换已由 gateway 实现（.com/.top 备选 + 换线路写回），
  冷启动偶发「加载失败」为 .net 线路抖动，重试即可；
- diagnostics「蜘蛛桥」区块与 JavaBridgeManager.lastErrorMessage 已接通（desktop 补齐 actual）。
