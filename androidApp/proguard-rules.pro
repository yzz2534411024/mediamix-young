# Koin
-keep class org.koin.** { *; }
-keep class com.mediamix.** { *; }

# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.mediamix.**$$serializer { *; }
-keepclassmembers class com.mediamix.** {
    *** Companion;
}
-keepclasseswithmembers class com.mediamix.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# SQLDelight
-keep class app.cash.sqldelight.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# General
-keep class * implements java.io.Serializable { *; }
-keepattributes Signature
-keepattributes Exceptions

# TVBox 蜘蛛契约类：spider.jar（dex）里的 csp_* 类 extends 本基类，
# 由 DexClassLoader 按名解析，裁剪或改名都会导致壳类加载失败
-keep class com.github.catvod.crawler.** { *; }
-keep class com.github.catvod.spider.** { *; }
-dontwarn com.github.catvod.**

# TVBox 蜘蛛 dex 硬编码依赖 okhttp3（TVBox 生态标准 HTTP 库）：
# DexClassLoader 按原名 loadClass("okhttp3.Request$Builder")，
# R8 混淆改名后找不到 → NoClassDefFoundError → 切换饭太硬闪透（2026-10-09 实测）
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# 蜘蛛 dex 同样硬编码依赖 Gson（第二处实测缺失）
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
