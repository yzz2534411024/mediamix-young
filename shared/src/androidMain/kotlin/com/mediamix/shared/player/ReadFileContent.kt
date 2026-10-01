package com.mediamix.shared.player

import java.io.File

actual suspend fun readFileContent(filePath: String): String {
    val file = File(filePath)
    if (!file.exists()) throw IllegalArgumentException("File not found: $filePath")
    return file.readText()
}
