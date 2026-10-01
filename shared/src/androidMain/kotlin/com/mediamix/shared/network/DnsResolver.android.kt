package com.mediamix.shared.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

actual suspend fun resolveHostAddresses(host: String): List<String> =
    withContext(Dispatchers.IO) {
        try {
            InetAddress.getAllByName(host).mapNotNull { it.hostAddress }
        } catch (_: Exception) {
            emptyList()
        }
    }
