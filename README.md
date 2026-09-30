# MediaMix KMP

跨平台视频聚合播放器，基于 Kotlin Multiplatform + Compose Multiplatform 构建。

## 技术栈

| 层级 | 技术 |
|------|------|
| **UI** | Compose Multiplatform 1.7.1 (Material 3) |
| **共享逻辑** | Kotlin Multiplatform 2.1.0 |
| **视频播放** | Media3 ExoPlayer 1.5 (Android) + mpv via JNA 5.15 (Desktop) |
| **网络** | Ktor Client 3.0.2 |
| **数据库** | SQLDelight 2.0.2 |
| **依赖注入** | Koin 4.0.0 |
| **序列化** | kotlinx-serialization 1.7.3 |
| **图片加载** | Coil 3.0.4 |

## 项目结构

```
mediamix-kmp/
├── shared/          # 跨平台共享模块
│   ├── core/        # 平台抽象 (DeviceCapability, PowerManager, PlatformPaths)
│   ├── network/     # 网络层 (Ktor HttpClient)
│   ├── models/      # 数据模型
│   ├── spider/      # 蜘蛛引擎 (CMS/JSON/XPath/JavaBridge)
│   ├── cache/       # 四级缓存系统
│   ├── player/      # 播放核心 (PlayerEngine, PlayerCoreManager, ABR, 字幕)
│   ├── services/    # 核心服务 (PreloadService)
│   └── di/          # Koin DI 注册
├── composeUi/       # 跨平台 Compose UI
│   ├── theme/       # Material 3 主题
│   ├── navigation/  # 导航框架
│   ├── screens/     # 9 个功能页面
│   ├── components/  # 共享 UI 组件
│   ├── viewmodel/   # 8 个 ViewModel
│   └── di/          # UI 层 DI
├── androidApp/      # Android 入口
└── desktopApp/      # Desktop (Windows) 入口
```

## 构建

### 前提条件
- JDK 17+
- Android SDK (compileSdk 35)
- Kotlin 2.1.0

### 命令

```bash
# 全量构建
./gradlew build

# Desktop 编译
./gradlew :composeUi:compileKotlinDesktop

# Android Debug APK
./gradlew :androidApp:assembleDebug

# 运行测试
./gradlew :shared:desktopTest

# Desktop 应用打包
./gradlew :desktopApp:packageDistributionForCurrentOS
```

## 模块说明

### 蜘蛛引擎 (shared/spider/)
支持 4 种数据源类型：CMS API、JSON API、XPath 解析、Java Bridge (TVBox)。
通过 SpiderAdapter 接口统一抽象，SpiderRegistry 管理注册，SpiderService 提供统一调用入口。

### 四级缓存 (shared/cache/)
L1 内存缓存 → L2 磁盘缓存 → L3 本地代理 → L4 网络请求。
支持 HLS 分段缓存、边播边缓存、缓存策略自动管理。

### 播放核心 (shared/player/)
PlayerEngine expect/actual 统一 Android/Desktop 播放 API。
PlayerCoreManager 编排器协调：错误降级链(10级)、ABR 自适应码率(加权评分)、AV 同步监控、字幕同步、进度记忆。

### UI 层 (composeUi/)
9 个页面：首页、详情、播放器、搜索、历史、收藏、设置、源管理、下载。
8 个 ViewModel 管理状态，Material 3 主题，Navigation Compose 导航。

## 平台支持

| 平台 | 最低版本 | 播放器 | 状态 |
|------|---------|--------|------|
| Android | API 24 (7.0) | Media3 ExoPlayer | ✅ |
| Desktop (Windows) | Windows 10+ | mpv via JNA | ✅ |

## 许可证

Private project. All rights reserved.
