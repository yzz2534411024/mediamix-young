package com.mediamix.shared.network

/**
 * 把主机名解析为 IP 地址列表；解析失败返回空列表（不抛异常）。
 *
 * 之所以放在 expect/actual 而不是在 commonMain 直接写 `java.net.InetAddress`：
 * 后者会让 commonMain 变成「只有 JVM 系 target 才能编」的伪 common
 * （见 docs/phase7-plan.md 的 P1-9）。
 */
expect suspend fun resolveHostAddresses(host: String): List<String>
