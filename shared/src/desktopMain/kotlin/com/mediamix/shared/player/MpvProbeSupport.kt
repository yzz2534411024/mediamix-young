package com.mediamix.shared.player

import com.sun.jna.Memory

/**
 * 逐步调用 mpv C API 并记录每一步结果。
 *
 * 用途：`--mpv-raw` 自检模式 —— JNA 的 "Invalid memory access" 是 `Error`（不是
 * Exception），堆栈不指向具体调用，只能靠二分/逐步执行定位。这里把 initialize()
 * 内部的每个 native 调用拆开单独执行，哪一步炸一目了然。
 */
fun mpvStepProbe(wid: Long): List<String> {
    val out = mutableListOf<String>()

    val lib =
        runCatching { MpvLib.getInstance() }.getOrElse {
            out += "getInstance FAIL: ${it.javaClass.simpleName}: ${it.message}"
            return out
        }
    out += "getInstance OK"

    val handle =
        runCatching { lib.mpv_create() }.getOrElse {
            out += "mpv_create FAIL: ${it.javaClass.simpleName}: ${it.message}"
            return out
        }
    if (handle == null) {
        out += "mpv_create 返回 null"
        return out
    }
    val h = handle
    out += "mpv_create OK (handle=$h)"

    fun step(
        name: String,
        block: () -> Int,
    ) {
        try {
            val rc = block()
            out += "$name OK (rc=$rc)"
        } catch (t: Throwable) {
            out += "$name FAIL: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    step("set_option(input-default-bindings, FLAG, 4B)") {
        lib.mpv_set_option(h, "input-default-bindings", MpvLib.MPV_FORMAT_FLAG, Memory(4).apply { setInt(0, 0) })
    }
    step("set_option(osc, FLAG, 4B)") {
        lib.mpv_set_option(h, "osc", MpvLib.MPV_FORMAT_FLAG, Memory(4).apply { setInt(0, 0) })
    }
    step("set_option(osd-level, INT64, 8B)") {
        lib.mpv_set_option(h, "osd-level", MpvLib.MPV_FORMAT_INT64, Memory(8).apply { setLong(0, 0) })
    }
    step("set_option(wid=$wid, INT64, 8B)") {
        lib.mpv_set_option(h, "wid", MpvLib.MPV_FORMAT_INT64, Memory(8).apply { setLong(0, wid) })
    }
    step("mpv_initialize") { lib.mpv_initialize(h) }
    step("observe(time-pos, DOUBLE, id=1)") {
        lib.mpv_observe_property(h, 1L, "time-pos", MpvLib.MPV_FORMAT_DOUBLE)
    }
    step("mpv_command(loadfile demo)") {
        lib.mpv_command(h, arrayOf("loadfile", "https://media.w3.org/2010/05/sintel/trailer.mp4"))
    }
    step("mpv_destroy") {
        lib.mpv_destroy(h)
        0
    }

    return out
}
