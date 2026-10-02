package com.mediamix.ui.icons

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 项目自带的 Material 图标集（仅包含实际用到的 50 个）。
 *
 * 为什么不用 `material-icons-extended`：该库把 3000+ 个图标全部打包，
 * 桌面发行包因此多出 36 MB、Android dex 也明显变大，而本项目只用到其中一小部分。
 * 这里把用到的定义内联进来（路径数据取自同版本官方源码，视觉完全一致），
 * 从而可以移除整个图标库依赖。
 *
 * 新增图标：在下面按同样格式追加一个 val + 一个 private 缓存变量即可
 * （或从 material-icons-extended 的源码里复制对应定义）。
 */
object AppIcons {

    val Analytics: ImageVector
        get() {
            if (_analytics != null) {
                return _analytics!!
            }
            _analytics = materialIcon(name = "Filled.Analytics") {
                materialPath {
                    moveTo(19.0f, 3.0f)
                    lineTo(5.0f, 3.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(14.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(14.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(21.0f, 5.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(9.0f, 17.0f)
                    lineTo(7.0f, 17.0f)
                    verticalLineToRelative(-5.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(5.0f)
                    close()
                    moveTo(13.0f, 17.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-3.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(3.0f)
                    close()
                    moveTo(13.0f, 12.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    close()
                    moveTo(17.0f, 17.0f)
                    horizontalLineToRelative(-2.0f)
                    lineTo(15.0f, 7.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(10.0f)
                    close()
                }
            }
            return _analytics!!
        }


    val AspectRatio: ImageVector
        get() {
            if (_aspectRatio != null) {
                return _aspectRatio!!
            }
            _aspectRatio = materialIcon(name = "Filled.AspectRatio") {
                materialPath {
                    moveTo(19.0f, 12.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(-3.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(5.0f)
                    verticalLineToRelative(-5.0f)
                    close()
                    moveTo(7.0f, 9.0f)
                    horizontalLineToRelative(3.0f)
                    lineTo(10.0f, 7.0f)
                    lineTo(5.0f, 7.0f)
                    verticalLineToRelative(5.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(7.0f, 9.0f)
                    close()
                    moveTo(21.0f, 3.0f)
                    lineTo(3.0f, 3.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(14.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(18.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(23.0f, 5.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(21.0f, 19.01f)
                    lineTo(3.0f, 19.01f)
                    lineTo(3.0f, 4.99f)
                    horizontalLineToRelative(18.0f)
                    verticalLineToRelative(14.02f)
                    close()
                }
            }
            return _aspectRatio!!
        }


    val BrightnessAuto: ImageVector
        get() {
            if (_brightnessAuto != null) {
                return _brightnessAuto!!
            }
            _brightnessAuto = materialIcon(name = "Filled.BrightnessAuto") {
                materialPath {
                    moveTo(10.85f, 12.65f)
                    horizontalLineToRelative(2.3f)
                    lineTo(12.0f, 9.0f)
                    lineToRelative(-1.15f, 3.65f)
                    close()
                    moveTo(20.0f, 8.69f)
                    verticalLineTo(4.0f)
                    horizontalLineToRelative(-4.69f)
                    lineTo(12.0f, 0.69f)
                    lineTo(8.69f, 4.0f)
                    horizontalLineTo(4.0f)
                    verticalLineToRelative(4.69f)
                    lineTo(0.69f, 12.0f)
                    lineTo(4.0f, 15.31f)
                    verticalLineTo(20.0f)
                    horizontalLineToRelative(4.69f)
                    lineTo(12.0f, 23.31f)
                    lineTo(15.31f, 20.0f)
                    horizontalLineTo(20.0f)
                    verticalLineToRelative(-4.69f)
                    lineTo(23.31f, 12.0f)
                    lineTo(20.0f, 8.69f)
                    close()
                    moveTo(14.3f, 16.0f)
                    lineToRelative(-0.7f, -2.0f)
                    horizontalLineToRelative(-3.2f)
                    lineToRelative(-0.7f, 2.0f)
                    horizontalLineTo(7.8f)
                    lineTo(11.0f, 7.0f)
                    horizontalLineToRelative(2.0f)
                    lineToRelative(3.2f, 9.0f)
                    horizontalLineToRelative(-1.9f)
                    close()
                }
            }
            return _brightnessAuto!!
        }


    val BrightnessMedium: ImageVector
        get() {
            if (_brightnessMedium != null) {
                return _brightnessMedium!!
            }
            _brightnessMedium = materialIcon(name = "Filled.BrightnessMedium") {
                materialPath {
                    moveTo(20.0f, 15.31f)
                    lineTo(23.31f, 12.0f)
                    lineTo(20.0f, 8.69f)
                    verticalLineTo(4.0f)
                    horizontalLineToRelative(-4.69f)
                    lineTo(12.0f, 0.69f)
                    lineTo(8.69f, 4.0f)
                    horizontalLineTo(4.0f)
                    verticalLineToRelative(4.69f)
                    lineTo(0.69f, 12.0f)
                    lineTo(4.0f, 15.31f)
                    verticalLineTo(20.0f)
                    horizontalLineToRelative(4.69f)
                    lineTo(12.0f, 23.31f)
                    lineTo(15.31f, 20.0f)
                    horizontalLineTo(20.0f)
                    verticalLineToRelative(-4.69f)
                    close()
                    moveTo(12.0f, 18.0f)
                    verticalLineTo(6.0f)
                    curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                    reflectiveCurveToRelative(-2.69f, 6.0f, -6.0f, 6.0f)
                    close()
                }
            }
            return _brightnessMedium!!
        }


    val BugReport: ImageVector
        get() {
            if (_bugReport != null) {
                return _bugReport!!
            }
            _bugReport = materialIcon(name = "Filled.BugReport") {
                materialPath {
                    moveTo(20.0f, 8.0f)
                    horizontalLineToRelative(-2.81f)
                    curveToRelative(-0.45f, -0.78f, -1.07f, -1.45f, -1.82f, -1.96f)
                    lineTo(17.0f, 4.41f)
                    lineTo(15.59f, 3.0f)
                    lineToRelative(-2.17f, 2.17f)
                    curveTo(12.96f, 5.06f, 12.49f, 5.0f, 12.0f, 5.0f)
                    curveToRelative(-0.49f, 0.0f, -0.96f, 0.06f, -1.41f, 0.17f)
                    lineTo(8.41f, 3.0f)
                    lineTo(7.0f, 4.41f)
                    lineToRelative(1.62f, 1.63f)
                    curveTo(7.88f, 6.55f, 7.26f, 7.22f, 6.81f, 8.0f)
                    lineTo(4.0f, 8.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(2.09f)
                    curveToRelative(-0.05f, 0.33f, -0.09f, 0.66f, -0.09f, 1.0f)
                    verticalLineToRelative(1.0f)
                    lineTo(4.0f, 12.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(1.0f)
                    curveToRelative(0.0f, 0.34f, 0.04f, 0.67f, 0.09f, 1.0f)
                    lineTo(4.0f, 16.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(2.81f)
                    curveToRelative(1.04f, 1.79f, 2.97f, 3.0f, 5.19f, 3.0f)
                    reflectiveCurveToRelative(4.15f, -1.21f, 5.19f, -3.0f)
                    lineTo(20.0f, 18.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(-2.09f)
                    curveToRelative(0.05f, -0.33f, 0.09f, -0.66f, 0.09f, -1.0f)
                    verticalLineToRelative(-1.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-1.0f)
                    curveToRelative(0.0f, -0.34f, -0.04f, -0.67f, -0.09f, -1.0f)
                    lineTo(20.0f, 10.0f)
                    lineTo(20.0f, 8.0f)
                    close()
                    moveTo(14.0f, 16.0f)
                    horizontalLineToRelative(-4.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineToRelative(2.0f)
                    close()
                    moveTo(14.0f, 12.0f)
                    horizontalLineToRelative(-4.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineToRelative(2.0f)
                    close()
                }
            }
            return _bugReport!!
        }


    val Cancel: ImageVector
        get() {
            if (_cancel != null) {
                return _cancel!!
            }
            _cancel = materialIcon(name = "Filled.Cancel") {
                materialPath {
                    moveTo(12.0f, 2.0f)
                    curveTo(6.47f, 2.0f, 2.0f, 6.47f, 2.0f, 12.0f)
                    reflectiveCurveToRelative(4.47f, 10.0f, 10.0f, 10.0f)
                    reflectiveCurveToRelative(10.0f, -4.47f, 10.0f, -10.0f)
                    reflectiveCurveTo(17.53f, 2.0f, 12.0f, 2.0f)
                    close()
                    moveTo(17.0f, 15.59f)
                    lineTo(15.59f, 17.0f)
                    lineTo(12.0f, 13.41f)
                    lineTo(8.41f, 17.0f)
                    lineTo(7.0f, 15.59f)
                    lineTo(10.59f, 12.0f)
                    lineTo(7.0f, 8.41f)
                    lineTo(8.41f, 7.0f)
                    lineTo(12.0f, 10.59f)
                    lineTo(15.59f, 7.0f)
                    lineTo(17.0f, 8.41f)
                    lineTo(13.41f, 12.0f)
                    lineTo(17.0f, 15.59f)
                    close()
                }
            }
            return _cancel!!
        }


    val ChevronRight: ImageVector
        get() {
            if (_chevronRight != null) {
                return _chevronRight!!
            }
            _chevronRight = materialIcon(name = "Filled.ChevronRight") {
                materialPath {
                    moveTo(10.0f, 6.0f)
                    lineTo(8.59f, 7.41f)
                    lineTo(13.17f, 12.0f)
                    lineToRelative(-4.58f, 4.59f)
                    lineTo(10.0f, 18.0f)
                    lineToRelative(6.0f, -6.0f)
                    close()
                }
            }
            return _chevronRight!!
        }


    val CleaningServices: ImageVector
        get() {
            if (_cleaningServices != null) {
                return _cleaningServices!!
            }
            _cleaningServices = materialIcon(name = "Filled.CleaningServices") {
                materialPath {
                    moveTo(16.0f, 11.0f)
                    horizontalLineToRelative(-1.0f)
                    verticalLineTo(3.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    horizontalLineToRelative(-2.0f)
                    curveTo(9.9f, 1.0f, 9.0f, 1.9f, 9.0f, 3.0f)
                    verticalLineToRelative(8.0f)
                    horizontalLineTo(8.0f)
                    curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 5.0f)
                    verticalLineToRelative(7.0f)
                    horizontalLineToRelative(18.0f)
                    verticalLineToRelative(-7.0f)
                    curveTo(21.0f, 13.24f, 18.76f, 11.0f, 16.0f, 11.0f)
                    close()
                    moveTo(19.0f, 21.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-3.0f)
                    curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                    reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-3.0f)
                    curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                    reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineTo(9.0f)
                    verticalLineToRelative(-3.0f)
                    curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                    reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineTo(5.0f)
                    verticalLineToRelative(-5.0f)
                    curveToRelative(0.0f, -1.65f, 1.35f, -3.0f, 3.0f, -3.0f)
                    horizontalLineToRelative(8.0f)
                    curveToRelative(1.65f, 0.0f, 3.0f, 1.35f, 3.0f, 3.0f)
                    verticalLineTo(21.0f)
                    close()
                }
            }
            return _cleaningServices!!
        }


    val ClosedCaption: ImageVector
        get() {
            if (_closedCaption != null) {
                return _closedCaption!!
            }
            _closedCaption = materialIcon(name = "Filled.ClosedCaption") {
                materialPath {
                    moveTo(19.0f, 4.0f)
                    lineTo(5.0f, 4.0f)
                    curveToRelative(-1.11f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(12.0f)
                    curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(14.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(21.0f, 6.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(11.0f, 11.0f)
                    lineTo(9.5f, 11.0f)
                    verticalLineToRelative(-0.5f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(9.5f, 13.0f)
                    lineTo(11.0f, 13.0f)
                    verticalLineToRelative(1.0f)
                    curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                    lineTo(7.0f, 15.0f)
                    curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                    verticalLineToRelative(-4.0f)
                    curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                    horizontalLineToRelative(3.0f)
                    curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                    verticalLineToRelative(1.0f)
                    close()
                    moveTo(18.0f, 11.0f)
                    horizontalLineToRelative(-1.5f)
                    verticalLineToRelative(-0.5f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(16.5f, 13.0f)
                    lineTo(18.0f, 13.0f)
                    verticalLineToRelative(1.0f)
                    curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                    horizontalLineToRelative(-3.0f)
                    curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                    verticalLineToRelative(-4.0f)
                    curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                    horizontalLineToRelative(3.0f)
                    curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                    verticalLineToRelative(1.0f)
                    close()
                }
            }
            return _closedCaption!!
        }


    val ClosedCaptionOff: ImageVector
        get() {
            if (_closedCaptionOff != null) {
                return _closedCaptionOff!!
            }
            _closedCaptionOff = materialIcon(name = "Filled.ClosedCaptionOff") {
                materialPath {
                    moveTo(19.5f, 5.5f)
                    verticalLineToRelative(13.0f)
                    horizontalLineToRelative(-15.0f)
                    verticalLineToRelative(-13.0f)
                    horizontalLineToRelative(15.0f)
                    close()
                    moveTo(19.0f, 4.0f)
                    lineTo(5.0f, 4.0f)
                    curveToRelative(-1.11f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(12.0f)
                    curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(14.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(21.0f, 6.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(11.0f, 11.0f)
                    lineTo(9.5f, 11.0f)
                    verticalLineToRelative(-0.5f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(9.5f, 13.0f)
                    lineTo(11.0f, 13.0f)
                    verticalLineToRelative(1.0f)
                    curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                    lineTo(7.0f, 15.0f)
                    curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                    verticalLineToRelative(-4.0f)
                    curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                    horizontalLineToRelative(3.0f)
                    curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                    verticalLineToRelative(1.0f)
                    close()
                    moveTo(18.0f, 11.0f)
                    horizontalLineToRelative(-1.5f)
                    verticalLineToRelative(-0.5f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(16.5f, 13.0f)
                    lineTo(18.0f, 13.0f)
                    verticalLineToRelative(1.0f)
                    curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                    horizontalLineToRelative(-3.0f)
                    curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                    verticalLineToRelative(-4.0f)
                    curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                    horizontalLineToRelative(3.0f)
                    curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                    verticalLineToRelative(1.0f)
                    close()
                }
            }
            return _closedCaptionOff!!
        }


    val Cloud: ImageVector
        get() {
            if (_cloud != null) {
                return _cloud!!
            }
            _cloud = materialIcon(name = "Filled.Cloud") {
                materialPath {
                    moveTo(19.35f, 10.04f)
                    curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
                    curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
                    curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
                    curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                    horizontalLineToRelative(13.0f)
                    curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                    curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                    close()
                }
            }
            return _cloud!!
        }


    val CloudDone: ImageVector
        get() {
            if (_cloudDone != null) {
                return _cloudDone!!
            }
            _cloudDone = materialIcon(name = "Filled.CloudDone") {
                materialPath {
                    moveTo(19.35f, 10.04f)
                    curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
                    curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
                    curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
                    curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                    horizontalLineToRelative(13.0f)
                    curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                    curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                    close()
                    moveTo(10.0f, 17.0f)
                    lineToRelative(-3.5f, -3.5f)
                    lineToRelative(1.41f, -1.41f)
                    lineTo(10.0f, 14.17f)
                    lineTo(15.18f, 9.0f)
                    lineToRelative(1.41f, 1.41f)
                    lineTo(10.0f, 17.0f)
                    close()
                }
            }
            return _cloudDone!!
        }


    val CloudOff: ImageVector
        get() {
            if (_cloudOff != null) {
                return _cloudOff!!
            }
            _cloudOff = materialIcon(name = "Filled.CloudOff") {
                materialPath {
                    moveTo(19.35f, 10.04f)
                    curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
                    curveToRelative(-1.48f, 0.0f, -2.85f, 0.43f, -4.01f, 1.17f)
                    lineToRelative(1.46f, 1.46f)
                    curveTo(10.21f, 6.23f, 11.08f, 6.0f, 12.0f, 6.0f)
                    curveToRelative(3.04f, 0.0f, 5.5f, 2.46f, 5.5f, 5.5f)
                    verticalLineToRelative(0.5f)
                    horizontalLineTo(19.0f)
                    curveToRelative(1.66f, 0.0f, 3.0f, 1.34f, 3.0f, 3.0f)
                    curveToRelative(0.0f, 1.13f, -0.64f, 2.11f, -1.56f, 2.62f)
                    lineToRelative(1.45f, 1.45f)
                    curveTo(23.16f, 18.16f, 24.0f, 16.68f, 24.0f, 15.0f)
                    curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                    close()
                    moveTo(3.0f, 5.27f)
                    lineToRelative(2.75f, 2.74f)
                    curveTo(2.56f, 8.15f, 0.0f, 10.77f, 0.0f, 14.0f)
                    curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                    horizontalLineToRelative(11.73f)
                    lineToRelative(2.0f, 2.0f)
                    lineTo(21.0f, 20.73f)
                    lineTo(4.27f, 4.0f)
                    lineTo(3.0f, 5.27f)
                    close()
                    moveTo(7.73f, 10.0f)
                    lineToRelative(8.0f, 8.0f)
                    horizontalLineTo(6.0f)
                    curveToRelative(-2.21f, 0.0f, -4.0f, -1.79f, -4.0f, -4.0f)
                    reflectiveCurveToRelative(1.79f, -4.0f, 4.0f, -4.0f)
                    horizontalLineToRelative(1.73f)
                    close()
                }
            }
            return _cloudOff!!
        }


    val CloudQueue: ImageVector
        get() {
            if (_cloudQueue != null) {
                return _cloudQueue!!
            }
            _cloudQueue = materialIcon(name = "Filled.CloudQueue") {
                materialPath {
                    moveTo(19.35f, 10.04f)
                    curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
                    curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
                    curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
                    curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                    horizontalLineToRelative(13.0f)
                    curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                    curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                    close()
                    moveTo(19.0f, 18.0f)
                    horizontalLineTo(6.0f)
                    curveToRelative(-2.21f, 0.0f, -4.0f, -1.79f, -4.0f, -4.0f)
                    reflectiveCurveToRelative(1.79f, -4.0f, 4.0f, -4.0f)
                    horizontalLineToRelative(0.71f)
                    curveTo(7.37f, 7.69f, 9.48f, 6.0f, 12.0f, 6.0f)
                    curveToRelative(3.04f, 0.0f, 5.5f, 2.46f, 5.5f, 5.5f)
                    verticalLineToRelative(0.5f)
                    horizontalLineTo(19.0f)
                    curveToRelative(1.66f, 0.0f, 3.0f, 1.34f, 3.0f, 3.0f)
                    reflectiveCurveToRelative(-1.34f, 3.0f, -3.0f, 3.0f)
                    close()
                }
            }
            return _cloudQueue!!
        }


    val DeleteOutline: ImageVector
        get() {
            if (_deleteOutline != null) {
                return _deleteOutline!!
            }
            _deleteOutline = materialIcon(name = "Filled.DeleteOutline") {
                materialPath {
                    moveTo(6.0f, 19.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(8.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(18.0f, 7.0f)
                    lineTo(6.0f, 7.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    moveTo(8.0f, 9.0f)
                    horizontalLineToRelative(8.0f)
                    verticalLineToRelative(10.0f)
                    lineTo(8.0f, 19.0f)
                    lineTo(8.0f, 9.0f)
                    close()
                    moveTo(15.5f, 4.0f)
                    lineToRelative(-1.0f, -1.0f)
                    horizontalLineToRelative(-5.0f)
                    lineToRelative(-1.0f, 1.0f)
                    lineTo(5.0f, 4.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(14.0f)
                    lineTo(19.0f, 4.0f)
                    close()
                }
            }
            return _deleteOutline!!
        }


    val DeleteSweep: ImageVector
        get() {
            if (_deleteSweep != null) {
                return _deleteSweep!!
            }
            _deleteSweep = materialIcon(name = "Filled.DeleteSweep") {
                materialPath {
                    moveTo(15.0f, 16.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(-4.0f)
                    close()
                    moveTo(15.0f, 8.0f)
                    horizontalLineToRelative(7.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(-7.0f)
                    close()
                    moveTo(15.0f, 12.0f)
                    horizontalLineToRelative(6.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(-6.0f)
                    close()
                    moveTo(3.0f, 18.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(6.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(13.0f, 8.0f)
                    lineTo(3.0f, 8.0f)
                    verticalLineToRelative(10.0f)
                    close()
                    moveTo(14.0f, 5.0f)
                    horizontalLineToRelative(-3.0f)
                    lineToRelative(-1.0f, -1.0f)
                    lineTo(6.0f, 4.0f)
                    lineTo(5.0f, 5.0f)
                    lineTo(2.0f, 5.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(12.0f)
                    close()
                }
            }
            return _deleteSweep!!
        }


    val Download: ImageVector
        get() {
            if (_download != null) {
                return _download!!
            }
            _download = materialIcon(name = "Filled.Download") {
                materialPath {
                    moveTo(5.0f, 20.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineTo(5.0f)
                    verticalLineTo(20.0f)
                    close()
                    moveTo(19.0f, 9.0f)
                    horizontalLineToRelative(-4.0f)
                    verticalLineTo(3.0f)
                    horizontalLineTo(9.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineTo(5.0f)
                    lineToRelative(7.0f, 7.0f)
                    lineTo(19.0f, 9.0f)
                    close()
                }
            }
            return _download!!
        }


    val Downloading: ImageVector
        get() {
            if (_downloading != null) {
                return _downloading!!
            }
            _downloading = materialIcon(name = "Filled.Downloading") {
                materialPath {
                    moveTo(18.32f, 4.26f)
                    curveTo(16.84f, 3.05f, 15.01f, 2.25f, 13.0f, 2.05f)
                    verticalLineToRelative(2.02f)
                    curveToRelative(1.46f, 0.18f, 2.79f, 0.76f, 3.9f, 1.62f)
                    lineTo(18.32f, 4.26f)
                    close()
                    moveTo(19.93f, 11.0f)
                    horizontalLineToRelative(2.02f)
                    curveToRelative(-0.2f, -2.01f, -1.0f, -3.84f, -2.21f, -5.32f)
                    lineTo(18.31f, 7.1f)
                    curveTo(19.17f, 8.21f, 19.75f, 9.54f, 19.93f, 11.0f)
                    close()
                    moveTo(18.31f, 16.9f)
                    lineToRelative(1.43f, 1.43f)
                    curveToRelative(1.21f, -1.48f, 2.01f, -3.32f, 2.21f, -5.32f)
                    horizontalLineToRelative(-2.02f)
                    curveTo(19.75f, 14.46f, 19.17f, 15.79f, 18.31f, 16.9f)
                    close()
                    moveTo(13.0f, 19.93f)
                    verticalLineToRelative(2.02f)
                    curveToRelative(2.01f, -0.2f, 3.84f, -1.0f, 5.32f, -2.21f)
                    lineToRelative(-1.43f, -1.43f)
                    curveTo(15.79f, 19.17f, 14.46f, 19.75f, 13.0f, 19.93f)
                    close()
                    moveTo(13.0f, 12.0f)
                    verticalLineTo(7.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(5.0f)
                    horizontalLineTo(7.0f)
                    lineToRelative(5.0f, 5.0f)
                    lineToRelative(5.0f, -5.0f)
                    horizontalLineTo(13.0f)
                    close()
                    moveTo(11.0f, 19.93f)
                    verticalLineToRelative(2.02f)
                    curveToRelative(-5.05f, -0.5f, -9.0f, -4.76f, -9.0f, -9.95f)
                    reflectiveCurveToRelative(3.95f, -9.45f, 9.0f, -9.95f)
                    verticalLineToRelative(2.02f)
                    curveTo(7.05f, 4.56f, 4.0f, 7.92f, 4.0f, 12.0f)
                    reflectiveCurveTo(7.05f, 19.44f, 11.0f, 19.93f)
                    close()
                }
            }
            return _downloading!!
        }


    val Error: ImageVector
        get() {
            if (_error != null) {
                return _error!!
            }
            _error = materialIcon(name = "Filled.Error") {
                materialPath {
                    moveTo(12.0f, 2.0f)
                    curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                    reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                    reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                    close()
                    moveTo(13.0f, 17.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    close()
                    moveTo(13.0f, 13.0f)
                    horizontalLineToRelative(-2.0f)
                    lineTo(11.0f, 7.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(6.0f)
                    close()
                }
            }
            return _error!!
        }


    val ErrorOutline: ImageVector
        get() {
            if (_errorOutline != null) {
                return _errorOutline!!
            }
            _errorOutline = materialIcon(name = "Filled.ErrorOutline") {
                materialPath {
                    moveTo(11.0f, 15.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(-2.0f)
                    close()
                    moveTo(11.0f, 7.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(-2.0f)
                    close()
                    moveTo(11.99f, 2.0f)
                    curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                    curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                    reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                    close()
                    moveTo(12.0f, 20.0f)
                    curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                    reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                    reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                    reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                    close()
                }
            }
            return _errorOutline!!
        }


    val FastForward: ImageVector
        get() {
            if (_fastForward != null) {
                return _fastForward!!
            }
            _fastForward = materialIcon(name = "Filled.FastForward") {
                materialPath {
                    moveTo(4.0f, 18.0f)
                    lineToRelative(8.5f, -6.0f)
                    lineTo(4.0f, 6.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    moveTo(13.0f, 6.0f)
                    verticalLineToRelative(12.0f)
                    lineToRelative(8.5f, -6.0f)
                    lineTo(13.0f, 6.0f)
                    close()
                }
            }
            return _fastForward!!
        }


    val FastRewind: ImageVector
        get() {
            if (_fastRewind != null) {
                return _fastRewind!!
            }
            _fastRewind = materialIcon(name = "Filled.FastRewind") {
                materialPath {
                    moveTo(11.0f, 18.0f)
                    lineTo(11.0f, 6.0f)
                    lineToRelative(-8.5f, 6.0f)
                    lineToRelative(8.5f, 6.0f)
                    close()
                    moveTo(11.5f, 12.0f)
                    lineToRelative(8.5f, 6.0f)
                    lineTo(20.0f, 6.0f)
                    lineToRelative(-8.5f, 6.0f)
                    close()
                }
            }
            return _fastRewind!!
        }


    val FileDownload: ImageVector
        get() {
            if (_fileDownload != null) {
                return _fileDownload!!
            }
            _fileDownload = materialIcon(name = "Filled.FileDownload") {
                materialPath {
                    moveTo(19.0f, 9.0f)
                    horizontalLineToRelative(-4.0f)
                    verticalLineTo(3.0f)
                    horizontalLineTo(9.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineTo(5.0f)
                    lineToRelative(7.0f, 7.0f)
                    lineToRelative(7.0f, -7.0f)
                    close()
                    moveTo(5.0f, 18.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineTo(5.0f)
                    close()
                }
            }
            return _fileDownload!!
        }


    val GraphicEq: ImageVector
        get() {
            if (_graphicEq != null) {
                return _graphicEq!!
            }
            _graphicEq = materialIcon(name = "Filled.GraphicEq") {
                materialPath {
                    moveTo(7.0f, 18.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(9.0f, 6.0f)
                    lineTo(7.0f, 6.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    moveTo(11.0f, 22.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(13.0f, 2.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(20.0f)
                    close()
                    moveTo(3.0f, 14.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-4.0f)
                    lineTo(3.0f, 10.0f)
                    verticalLineToRelative(4.0f)
                    close()
                    moveTo(15.0f, 18.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(17.0f, 6.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    moveTo(19.0f, 10.0f)
                    verticalLineToRelative(4.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-4.0f)
                    horizontalLineToRelative(-2.0f)
                    close()
                }
            }
            return _graphicEq!!
        }


    val HighQuality: ImageVector
        get() {
            if (_highQuality != null) {
                return _highQuality!!
            }
            _highQuality = materialIcon(name = "Filled.HighQuality") {
                materialPath {
                    moveTo(19.0f, 4.0f)
                    lineTo(5.0f, 4.0f)
                    curveToRelative(-1.11f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(12.0f)
                    curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(14.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(21.0f, 6.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(11.0f, 15.0f)
                    lineTo(9.5f, 15.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(2.0f)
                    lineTo(6.0f, 15.0f)
                    lineTo(6.0f, 9.0f)
                    horizontalLineToRelative(1.5f)
                    verticalLineToRelative(2.5f)
                    horizontalLineToRelative(2.0f)
                    lineTo(9.5f, 9.0f)
                    lineTo(11.0f, 9.0f)
                    verticalLineToRelative(6.0f)
                    close()
                    moveTo(18.0f, 14.0f)
                    curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                    horizontalLineToRelative(-0.75f)
                    verticalLineToRelative(1.5f)
                    horizontalLineToRelative(-1.5f)
                    lineTo(14.75f, 15.0f)
                    lineTo(14.0f, 15.0f)
                    curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                    verticalLineToRelative(-4.0f)
                    curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                    horizontalLineToRelative(3.0f)
                    curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                    verticalLineToRelative(4.0f)
                    close()
                    moveTo(14.5f, 13.5f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-3.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(3.0f)
                    close()
                }
            }
            return _highQuality!!
        }


    val History: ImageVector
        get() {
            if (_history != null) {
                return _history!!
            }
            _history = materialIcon(name = "Filled.History") {
                materialPath {
                    moveTo(13.0f, 3.0f)
                    curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
                    lineTo(1.0f, 12.0f)
                    lineToRelative(3.89f, 3.89f)
                    lineToRelative(0.07f, 0.14f)
                    lineTo(9.0f, 12.0f)
                    lineTo(6.0f, 12.0f)
                    curveToRelative(0.0f, -3.87f, 3.13f, -7.0f, 7.0f, -7.0f)
                    reflectiveCurveToRelative(7.0f, 3.13f, 7.0f, 7.0f)
                    reflectiveCurveToRelative(-3.13f, 7.0f, -7.0f, 7.0f)
                    curveToRelative(-1.93f, 0.0f, -3.68f, -0.79f, -4.94f, -2.06f)
                    lineToRelative(-1.42f, 1.42f)
                    curveTo(8.27f, 19.99f, 10.51f, 21.0f, 13.0f, 21.0f)
                    curveToRelative(4.97f, 0.0f, 9.0f, -4.03f, 9.0f, -9.0f)
                    reflectiveCurveToRelative(-4.03f, -9.0f, -9.0f, -9.0f)
                    close()
                    moveTo(12.0f, 8.0f)
                    verticalLineToRelative(5.0f)
                    lineToRelative(4.28f, 2.54f)
                    lineToRelative(0.72f, -1.21f)
                    lineToRelative(-3.5f, -2.08f)
                    lineTo(13.5f, 8.0f)
                    lineTo(12.0f, 8.0f)
                    close()
                }
            }
            return _history!!
        }


    val Memory: ImageVector
        get() {
            if (_memory != null) {
                return _memory!!
            }
            _memory = materialIcon(name = "Filled.Memory") {
                materialPath {
                    moveTo(15.0f, 9.0f)
                    lineTo(9.0f, 9.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(6.0f)
                    lineTo(15.0f, 9.0f)
                    close()
                    moveTo(13.0f, 13.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    close()
                    moveTo(21.0f, 11.0f)
                    lineTo(21.0f, 9.0f)
                    horizontalLineToRelative(-2.0f)
                    lineTo(19.0f, 7.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    horizontalLineToRelative(-2.0f)
                    lineTo(15.0f, 3.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(-2.0f)
                    lineTo(11.0f, 3.0f)
                    lineTo(9.0f, 3.0f)
                    verticalLineToRelative(2.0f)
                    lineTo(7.0f, 5.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(2.0f)
                    lineTo(3.0f, 9.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    lineTo(3.0f, 13.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(2.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(2.0f)
                    close()
                    moveTo(17.0f, 17.0f)
                    lineTo(7.0f, 17.0f)
                    lineTo(7.0f, 7.0f)
                    horizontalLineToRelative(10.0f)
                    verticalLineToRelative(10.0f)
                    close()
                }
            }
            return _memory!!
        }


    val Movie: ImageVector
        get() {
            if (_movie != null) {
                return _movie!!
            }
            _movie = materialIcon(name = "Filled.Movie") {
                materialPath {
                    moveTo(18.0f, 4.0f)
                    lineToRelative(2.0f, 4.0f)
                    horizontalLineToRelative(-3.0f)
                    lineToRelative(-2.0f, -4.0f)
                    horizontalLineToRelative(-2.0f)
                    lineToRelative(2.0f, 4.0f)
                    horizontalLineToRelative(-3.0f)
                    lineToRelative(-2.0f, -4.0f)
                    horizontalLineTo(8.0f)
                    lineToRelative(2.0f, 4.0f)
                    horizontalLineTo(7.0f)
                    lineTo(5.0f, 4.0f)
                    horizontalLineTo(4.0f)
                    curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                    lineTo(2.0f, 18.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(16.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineTo(4.0f)
                    horizontalLineToRelative(-4.0f)
                    close()
                }
            }
            return _movie!!
        }


    val NetworkCheck: ImageVector
        get() {
            if (_networkCheck != null) {
                return _networkCheck!!
            }
            _networkCheck = materialIcon(name = "Filled.NetworkCheck") {
                materialPath {
                    moveTo(15.9f, 5.0f)
                    curveToRelative(-0.17f, 0.0f, -0.32f, 0.09f, -0.41f, 0.23f)
                    lineToRelative(-0.07f, 0.15f)
                    lineToRelative(-5.18f, 11.65f)
                    curveToRelative(-0.16f, 0.29f, -0.26f, 0.61f, -0.26f, 0.96f)
                    curveToRelative(0.0f, 1.11f, 0.9f, 2.01f, 2.01f, 2.01f)
                    curveToRelative(0.96f, 0.0f, 1.77f, -0.68f, 1.96f, -1.59f)
                    lineToRelative(0.01f, -0.03f)
                    lineTo(16.4f, 5.5f)
                    curveToRelative(0.0f, -0.28f, -0.22f, -0.5f, -0.5f, -0.5f)
                    close()
                    moveTo(1.0f, 9.0f)
                    lineToRelative(2.0f, 2.0f)
                    curveToRelative(2.88f, -2.88f, 6.79f, -4.08f, 10.53f, -3.62f)
                    lineToRelative(1.19f, -2.68f)
                    curveTo(9.89f, 3.84f, 4.74f, 5.27f, 1.0f, 9.0f)
                    close()
                    moveTo(21.0f, 11.0f)
                    lineToRelative(2.0f, -2.0f)
                    curveToRelative(-1.64f, -1.64f, -3.55f, -2.82f, -5.59f, -3.57f)
                    lineToRelative(-0.53f, 2.82f)
                    curveToRelative(1.5f, 0.62f, 2.9f, 1.53f, 4.12f, 2.75f)
                    close()
                    moveTo(17.0f, 15.0f)
                    lineToRelative(2.0f, -2.0f)
                    curveToRelative(-0.8f, -0.8f, -1.7f, -1.42f, -2.66f, -1.89f)
                    lineToRelative(-0.55f, 2.92f)
                    curveToRelative(0.42f, 0.27f, 0.83f, 0.59f, 1.21f, 0.97f)
                    close()
                    moveTo(5.0f, 13.0f)
                    lineToRelative(2.0f, 2.0f)
                    curveToRelative(1.13f, -1.13f, 2.56f, -1.79f, 4.03f, -2.0f)
                    lineToRelative(1.28f, -2.88f)
                    curveToRelative(-2.63f, -0.08f, -5.3f, 0.87f, -7.31f, 2.88f)
                    close()
                }
            }
            return _networkCheck!!
        }


    val Pause: ImageVector
        get() {
            if (_pause != null) {
                return _pause!!
            }
            _pause = materialIcon(name = "Filled.Pause") {
                materialPath {
                    moveTo(6.0f, 19.0f)
                    horizontalLineToRelative(4.0f)
                    lineTo(10.0f, 5.0f)
                    lineTo(6.0f, 5.0f)
                    verticalLineToRelative(14.0f)
                    close()
                    moveTo(14.0f, 5.0f)
                    verticalLineToRelative(14.0f)
                    horizontalLineToRelative(4.0f)
                    lineTo(18.0f, 5.0f)
                    horizontalLineToRelative(-4.0f)
                    close()
                }
            }
            return _pause!!
        }


    val PauseCircle: ImageVector
        get() {
            if (_pauseCircle != null) {
                return _pauseCircle!!
            }
            _pauseCircle = materialIcon(name = "Filled.PauseCircle") {
                materialPath {
                    moveTo(12.0f, 2.0f)
                    curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                    reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                    reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                    close()
                    moveTo(11.0f, 16.0f)
                    horizontalLineTo(9.0f)
                    verticalLineTo(8.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineTo(16.0f)
                    close()
                    moveTo(15.0f, 16.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineTo(8.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineTo(16.0f)
                    close()
                }
            }
            return _pauseCircle!!
        }


    val PlayCircle: ImageVector
        get() {
            if (_playCircle != null) {
                return _playCircle!!
            }
            _playCircle = materialIcon(name = "Filled.PlayCircle") {
                materialPath {
                    moveTo(12.0f, 2.0f)
                    curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                    reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                    reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                    close()
                    moveTo(9.5f, 16.5f)
                    verticalLineToRelative(-9.0f)
                    lineToRelative(7.0f, 4.5f)
                    lineTo(9.5f, 16.5f)
                    close()
                }
            }
            return _playCircle!!
        }


    val PlaylistPlay: ImageVector
        get() {
            if (_playlistPlay != null) {
                return _playlistPlay!!
            }
            _playlistPlay = materialIcon(name = "Filled.PlaylistPlay") {
                materialPath {
                    moveTo(3.0f, 10.0f)
                    horizontalLineToRelative(11.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(-11.0f)
                    close()
                }
                materialPath {
                    moveTo(3.0f, 6.0f)
                    horizontalLineToRelative(11.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(-11.0f)
                    close()
                }
                materialPath {
                    moveTo(3.0f, 14.0f)
                    horizontalLineToRelative(7.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(-7.0f)
                    close()
                }
                materialPath {
                    moveTo(16.0f, 13.0f)
                    lineToRelative(0.0f, 8.0f)
                    lineToRelative(6.0f, -4.0f)
                    close()
                }
            }
            return _playlistPlay!!
        }


    val Repeat: ImageVector
        get() {
            if (_repeat != null) {
                return _repeat!!
            }
            _repeat = materialIcon(name = "Filled.Repeat") {
                materialPath {
                    moveTo(7.0f, 7.0f)
                    horizontalLineToRelative(10.0f)
                    verticalLineToRelative(3.0f)
                    lineToRelative(4.0f, -4.0f)
                    lineToRelative(-4.0f, -4.0f)
                    verticalLineToRelative(3.0f)
                    lineTo(5.0f, 5.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(7.0f, 7.0f)
                    close()
                    moveTo(17.0f, 17.0f)
                    lineTo(7.0f, 17.0f)
                    verticalLineToRelative(-3.0f)
                    lineToRelative(-4.0f, 4.0f)
                    lineToRelative(4.0f, 4.0f)
                    verticalLineToRelative(-3.0f)
                    horizontalLineToRelative(12.0f)
                    verticalLineToRelative(-6.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(4.0f)
                    close()
                }
            }
            return _repeat!!
        }


    val RepeatOne: ImageVector
        get() {
            if (_repeatOne != null) {
                return _repeatOne!!
            }
            _repeatOne = materialIcon(name = "Filled.RepeatOne") {
                materialPath {
                    moveTo(7.0f, 7.0f)
                    horizontalLineToRelative(10.0f)
                    verticalLineToRelative(3.0f)
                    lineToRelative(4.0f, -4.0f)
                    lineToRelative(-4.0f, -4.0f)
                    verticalLineToRelative(3.0f)
                    lineTo(5.0f, 5.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(7.0f, 7.0f)
                    close()
                    moveTo(17.0f, 17.0f)
                    lineTo(7.0f, 17.0f)
                    verticalLineToRelative(-3.0f)
                    lineToRelative(-4.0f, 4.0f)
                    lineToRelative(4.0f, 4.0f)
                    verticalLineToRelative(-3.0f)
                    horizontalLineToRelative(12.0f)
                    verticalLineToRelative(-6.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(4.0f)
                    close()
                    moveTo(13.0f, 15.0f)
                    lineTo(13.0f, 9.0f)
                    horizontalLineToRelative(-1.0f)
                    lineToRelative(-2.0f, 1.0f)
                    verticalLineToRelative(1.0f)
                    horizontalLineToRelative(1.5f)
                    verticalLineToRelative(4.0f)
                    lineTo(13.0f, 15.0f)
                    close()
                }
            }
            return _repeatOne!!
        }


    val Schedule: ImageVector
        get() {
            if (_schedule != null) {
                return _schedule!!
            }
            _schedule = materialIcon(name = "Filled.Schedule") {
                materialPath {
                    moveTo(11.99f, 2.0f)
                    curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                    curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                    reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                    close()
                    moveTo(12.0f, 20.0f)
                    curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                    reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                    reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                    reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                    close()
                }
                materialPath {
                    moveTo(12.5f, 7.0f)
                    horizontalLineTo(11.0f)
                    verticalLineToRelative(6.0f)
                    lineToRelative(5.25f, 3.15f)
                    lineToRelative(0.75f, -1.23f)
                    lineToRelative(-4.5f, -2.67f)
                    close()
                }
            }
            return _schedule!!
        }


    val ScreenRotation: ImageVector
        get() {
            if (_screenRotation != null) {
                return _screenRotation!!
            }
            _screenRotation = materialIcon(name = "Filled.ScreenRotation") {
                materialPath {
                    moveTo(16.48f, 2.52f)
                    curveToRelative(3.27f, 1.55f, 5.61f, 4.72f, 5.97f, 8.48f)
                    horizontalLineToRelative(1.5f)
                    curveTo(23.44f, 4.84f, 18.29f, 0.0f, 12.0f, 0.0f)
                    lineToRelative(-0.66f, 0.03f)
                    lineToRelative(3.81f, 3.81f)
                    lineToRelative(1.33f, -1.32f)
                    close()
                    moveTo(10.23f, 1.75f)
                    curveToRelative(-0.59f, -0.59f, -1.54f, -0.59f, -2.12f, 0.0f)
                    lineTo(1.75f, 8.11f)
                    curveToRelative(-0.59f, 0.59f, -0.59f, 1.54f, 0.0f, 2.12f)
                    lineToRelative(12.02f, 12.02f)
                    curveToRelative(0.59f, 0.59f, 1.54f, 0.59f, 2.12f, 0.0f)
                    lineToRelative(6.36f, -6.36f)
                    curveToRelative(0.59f, -0.59f, 0.59f, -1.54f, 0.0f, -2.12f)
                    lineTo(10.23f, 1.75f)
                    close()
                    moveTo(14.83f, 21.19f)
                    lineTo(2.81f, 9.17f)
                    lineToRelative(6.36f, -6.36f)
                    lineToRelative(12.02f, 12.02f)
                    lineToRelative(-6.36f, 6.36f)
                    close()
                    moveTo(7.52f, 21.48f)
                    curveTo(4.25f, 19.94f, 1.91f, 16.76f, 1.55f, 13.0f)
                    lineTo(0.05f, 13.0f)
                    curveTo(0.56f, 19.16f, 5.71f, 24.0f, 12.0f, 24.0f)
                    lineToRelative(0.66f, -0.03f)
                    lineToRelative(-3.81f, -3.81f)
                    lineToRelative(-1.33f, 1.32f)
                    close()
                }
            }
            return _screenRotation!!
        }


    val SearchOff: ImageVector
        get() {
            if (_searchOff != null) {
                return _searchOff!!
            }
            _searchOff = materialIcon(name = "Filled.SearchOff") {
                materialPath {
                    moveTo(15.5f, 14.0f)
                    horizontalLineToRelative(-0.79f)
                    lineToRelative(-0.28f, -0.27f)
                    curveTo(15.41f, 12.59f, 16.0f, 11.11f, 16.0f, 9.5f)
                    curveTo(16.0f, 5.91f, 13.09f, 3.0f, 9.5f, 3.0f)
                    curveTo(6.08f, 3.0f, 3.28f, 5.64f, 3.03f, 9.0f)
                    horizontalLineToRelative(2.02f)
                    curveTo(5.3f, 6.75f, 7.18f, 5.0f, 9.5f, 5.0f)
                    curveTo(11.99f, 5.0f, 14.0f, 7.01f, 14.0f, 9.5f)
                    reflectiveCurveTo(11.99f, 14.0f, 9.5f, 14.0f)
                    curveToRelative(-0.17f, 0.0f, -0.33f, -0.03f, -0.5f, -0.05f)
                    verticalLineToRelative(2.02f)
                    curveTo(9.17f, 15.99f, 9.33f, 16.0f, 9.5f, 16.0f)
                    curveToRelative(1.61f, 0.0f, 3.09f, -0.59f, 4.23f, -1.57f)
                    lineTo(14.0f, 14.71f)
                    verticalLineToRelative(0.79f)
                    lineToRelative(5.0f, 4.99f)
                    lineTo(20.49f, 19.0f)
                    lineTo(15.5f, 14.0f)
                    close()
                }
                materialPath {
                    moveTo(6.47f, 10.82f)
                    lineToRelative(-2.47f, 2.47f)
                    lineToRelative(-2.47f, -2.47f)
                    lineToRelative(-0.71f, 0.71f)
                    lineToRelative(2.47f, 2.47f)
                    lineToRelative(-2.47f, 2.47f)
                    lineToRelative(0.71f, 0.71f)
                    lineToRelative(2.47f, -2.47f)
                    lineToRelative(2.47f, 2.47f)
                    lineToRelative(0.71f, -0.71f)
                    lineToRelative(-2.47f, -2.47f)
                    lineToRelative(2.47f, -2.47f)
                    close()
                }
            }
            return _searchOff!!
        }


    val SkipNext: ImageVector
        get() {
            if (_skipNext != null) {
                return _skipNext!!
            }
            _skipNext = materialIcon(name = "Filled.SkipNext") {
                materialPath {
                    moveTo(6.0f, 18.0f)
                    lineToRelative(8.5f, -6.0f)
                    lineTo(6.0f, 6.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    moveTo(16.0f, 6.0f)
                    verticalLineToRelative(12.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineTo(6.0f)
                    horizontalLineToRelative(-2.0f)
                    close()
                }
            }
            return _skipNext!!
        }


    val SkipPrevious: ImageVector
        get() {
            if (_skipPrevious != null) {
                return _skipPrevious!!
            }
            _skipPrevious = materialIcon(name = "Filled.SkipPrevious") {
                materialPath {
                    moveTo(6.0f, 6.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(12.0f)
                    lineTo(6.0f, 18.0f)
                    close()
                    moveTo(9.5f, 12.0f)
                    lineToRelative(8.5f, 6.0f)
                    lineTo(18.0f, 6.0f)
                    close()
                }
            }
            return _skipPrevious!!
        }


    val Source: ImageVector
        get() {
            if (_source != null) {
                return _source!!
            }
            _source = materialIcon(name = "Filled.Source") {
                materialPath {
                    moveTo(20.0f, 6.0f)
                    horizontalLineToRelative(-8.0f)
                    lineToRelative(-2.0f, -2.0f)
                    horizontalLineTo(4.0f)
                    curveTo(2.9f, 4.0f, 2.01f, 4.9f, 2.01f, 6.0f)
                    lineTo(2.0f, 18.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(16.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineTo(8.0f)
                    curveTo(22.0f, 6.9f, 21.1f, 6.0f, 20.0f, 6.0f)
                    close()
                    moveTo(14.0f, 16.0f)
                    horizontalLineTo(6.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(8.0f)
                    verticalLineTo(16.0f)
                    close()
                    moveTo(18.0f, 12.0f)
                    horizontalLineTo(6.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(12.0f)
                    verticalLineTo(12.0f)
                    close()
                }
            }
            return _source!!
        }


    val Storage: ImageVector
        get() {
            if (_storage != null) {
                return _storage!!
            }
            _storage = materialIcon(name = "Filled.Storage") {
                materialPath {
                    moveTo(2.0f, 20.0f)
                    horizontalLineToRelative(20.0f)
                    verticalLineToRelative(-4.0f)
                    lineTo(2.0f, 16.0f)
                    verticalLineToRelative(4.0f)
                    close()
                    moveTo(4.0f, 17.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    lineTo(4.0f, 19.0f)
                    verticalLineToRelative(-2.0f)
                    close()
                    moveTo(2.0f, 4.0f)
                    verticalLineToRelative(4.0f)
                    horizontalLineToRelative(20.0f)
                    lineTo(22.0f, 4.0f)
                    lineTo(2.0f, 4.0f)
                    close()
                    moveTo(6.0f, 7.0f)
                    lineTo(4.0f, 7.0f)
                    lineTo(4.0f, 5.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    close()
                    moveTo(2.0f, 14.0f)
                    horizontalLineToRelative(20.0f)
                    verticalLineToRelative(-4.0f)
                    lineTo(2.0f, 10.0f)
                    verticalLineToRelative(4.0f)
                    close()
                    moveTo(4.0f, 11.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    lineTo(4.0f, 13.0f)
                    verticalLineToRelative(-2.0f)
                    close()
                }
            }
            return _storage!!
        }


    val SwapHoriz: ImageVector
        get() {
            if (_swapHoriz != null) {
                return _swapHoriz!!
            }
            _swapHoriz = materialIcon(name = "Filled.SwapHoriz") {
                materialPath {
                    moveTo(6.99f, 11.0f)
                    lineTo(3.0f, 15.0f)
                    lineToRelative(3.99f, 4.0f)
                    verticalLineToRelative(-3.0f)
                    horizontalLineTo(14.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineTo(6.99f)
                    verticalLineToRelative(-3.0f)
                    close()
                    moveTo(21.0f, 9.0f)
                    lineToRelative(-3.99f, -4.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineTo(10.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(7.01f)
                    verticalLineToRelative(3.0f)
                    lineTo(21.0f, 9.0f)
                    close()
                }
            }
            return _swapHoriz!!
        }


    val VolumeOff: ImageVector
        get() {
            if (_volumeOff != null) {
                return _volumeOff!!
            }
            _volumeOff = materialIcon(name = "Filled.VolumeOff") {
                materialPath {
                    moveTo(16.5f, 12.0f)
                    curveToRelative(0.0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                    verticalLineToRelative(2.21f)
                    lineToRelative(2.45f, 2.45f)
                    curveToRelative(0.03f, -0.2f, 0.05f, -0.41f, 0.05f, -0.63f)
                    close()
                    moveTo(19.0f, 12.0f)
                    curveToRelative(0.0f, 0.94f, -0.2f, 1.82f, -0.54f, 2.64f)
                    lineToRelative(1.51f, 1.51f)
                    curveTo(20.63f, 14.91f, 21.0f, 13.5f, 21.0f, 12.0f)
                    curveToRelative(0.0f, -4.28f, -2.99f, -7.86f, -7.0f, -8.77f)
                    verticalLineToRelative(2.06f)
                    curveToRelative(2.89f, 0.86f, 5.0f, 3.54f, 5.0f, 6.71f)
                    close()
                    moveTo(4.27f, 3.0f)
                    lineTo(3.0f, 4.27f)
                    lineTo(7.73f, 9.0f)
                    lineTo(3.0f, 9.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(4.0f)
                    lineToRelative(5.0f, 5.0f)
                    verticalLineToRelative(-6.73f)
                    lineToRelative(4.25f, 4.25f)
                    curveToRelative(-0.67f, 0.52f, -1.42f, 0.93f, -2.25f, 1.18f)
                    verticalLineToRelative(2.06f)
                    curveToRelative(1.38f, -0.31f, 2.63f, -0.95f, 3.69f, -1.81f)
                    lineTo(19.73f, 21.0f)
                    lineTo(21.0f, 19.73f)
                    lineToRelative(-9.0f, -9.0f)
                    lineTo(4.27f, 3.0f)
                    close()
                    moveTo(12.0f, 4.0f)
                    lineTo(9.91f, 6.09f)
                    lineTo(12.0f, 8.18f)
                    lineTo(12.0f, 4.0f)
                    close()
                }
            }
            return _volumeOff!!
        }


    val VolumeUp: ImageVector
        get() {
            if (_volumeUp != null) {
                return _volumeUp!!
            }
            _volumeUp = materialIcon(name = "Filled.VolumeUp") {
                materialPath {
                    moveTo(3.0f, 9.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(4.0f)
                    lineToRelative(5.0f, 5.0f)
                    lineTo(12.0f, 4.0f)
                    lineTo(7.0f, 9.0f)
                    lineTo(3.0f, 9.0f)
                    close()
                    moveTo(16.5f, 12.0f)
                    curveToRelative(0.0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                    verticalLineToRelative(8.05f)
                    curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
                    close()
                    moveTo(14.0f, 3.23f)
                    verticalLineToRelative(2.06f)
                    curveToRelative(2.89f, 0.86f, 5.0f, 3.54f, 5.0f, 6.71f)
                    reflectiveCurveToRelative(-2.11f, 5.85f, -5.0f, 6.71f)
                    verticalLineToRelative(2.06f)
                    curveToRelative(4.01f, -0.91f, 7.0f, -4.49f, 7.0f, -8.77f)
                    reflectiveCurveToRelative(-2.99f, -7.86f, -7.0f, -8.77f)
                    close()
                }
            }
            return _volumeUp!!
        }


    val CalendarToday: ImageVector
        get() {
            if (_calendarToday != null) {
                return _calendarToday!!
            }
            _calendarToday = materialIcon(name = "Filled.CalendarToday") {
                materialPath {
                    moveTo(20.0f, 3.0f)
                    horizontalLineToRelative(-1.0f)
                    lineTo(19.0f, 1.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(2.0f)
                    lineTo(7.0f, 3.0f)
                    lineTo(7.0f, 1.0f)
                    lineTo(5.0f, 1.0f)
                    verticalLineToRelative(2.0f)
                    lineTo(4.0f, 3.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(16.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(16.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(22.0f, 5.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(20.0f, 21.0f)
                    lineTo(4.0f, 21.0f)
                    lineTo(4.0f, 8.0f)
                    horizontalLineToRelative(16.0f)
                    verticalLineToRelative(13.0f)
                    close()
                }
            }
            return _calendarToday!!
        }


    val Category: ImageVector
        get() {
            if (_category != null) {
                return _category!!
            }
            _category = materialIcon(name = "Filled.Category") {
                materialPath {
                    moveTo(12.0f, 2.0f)
                    lineToRelative(-5.5f, 9.0f)
                    horizontalLineToRelative(11.0f)
                    close()
                }
                materialPath {
                    moveTo(17.5f, 17.5f)
                    moveToRelative(-4.5f, 0.0f)
                    arcToRelative(4.5f, 4.5f, 0.0f, true, true, 9.0f, 0.0f)
                    arcToRelative(4.5f, 4.5f, 0.0f, true, true, -9.0f, 0.0f)
                }
                materialPath {
                    moveTo(3.0f, 13.5f)
                    horizontalLineToRelative(8.0f)
                    verticalLineToRelative(8.0f)
                    horizontalLineTo(3.0f)
                    close()
                }
            }
            return _category!!
        }


    val ExpandLess: ImageVector
        get() {
            if (_expandLess != null) {
                return _expandLess!!
            }
            _expandLess = materialIcon(name = "Filled.ExpandLess") {
                materialPath {
                    moveTo(12.0f, 8.0f)
                    lineToRelative(-6.0f, 6.0f)
                    lineToRelative(1.41f, 1.41f)
                    lineTo(12.0f, 10.83f)
                    lineToRelative(4.59f, 4.58f)
                    lineTo(18.0f, 14.0f)
                    close()
                }
            }
            return _expandLess!!
        }


    val ExpandMore: ImageVector
        get() {
            if (_expandMore != null) {
                return _expandMore!!
            }
            _expandMore = materialIcon(name = "Filled.ExpandMore") {
                materialPath {
                    moveTo(16.59f, 8.59f)
                    lineTo(12.0f, 13.17f)
                    lineTo(7.41f, 8.59f)
                    lineTo(6.0f, 10.0f)
                    lineToRelative(6.0f, 6.0f)
                    lineToRelative(6.0f, -6.0f)
                    close()
                }
            }
            return _expandMore!!
        }


    val Public: ImageVector
        get() {
            if (_public != null) {
                return _public!!
            }
            _public = materialIcon(name = "Filled.Public") {
                materialPath {
                    moveTo(12.0f, 2.0f)
                    curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                    reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                    reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                    close()
                    moveTo(11.0f, 19.93f)
                    curveToRelative(-3.95f, -0.49f, -7.0f, -3.85f, -7.0f, -7.93f)
                    curveToRelative(0.0f, -0.62f, 0.08f, -1.21f, 0.21f, -1.79f)
                    lineTo(9.0f, 15.0f)
                    verticalLineToRelative(1.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    verticalLineToRelative(1.93f)
                    close()
                    moveTo(17.9f, 17.39f)
                    curveToRelative(-0.26f, -0.81f, -1.0f, -1.39f, -1.9f, -1.39f)
                    horizontalLineToRelative(-1.0f)
                    verticalLineToRelative(-3.0f)
                    curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                    lineTo(8.0f, 12.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(2.0f)
                    curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                    lineTo(11.0f, 7.0f)
                    horizontalLineToRelative(2.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineToRelative(-0.41f)
                    curveToRelative(2.93f, 1.19f, 5.0f, 4.06f, 5.0f, 7.41f)
                    curveToRelative(0.0f, 2.08f, -0.8f, 3.97f, -2.1f, 5.39f)
                    close()
                }
            }
            return _public!!
        }

}

private var _analytics: ImageVector? = null
private var _aspectRatio: ImageVector? = null
private var _brightnessAuto: ImageVector? = null
private var _brightnessMedium: ImageVector? = null
private var _bugReport: ImageVector? = null
private var _cancel: ImageVector? = null
private var _chevronRight: ImageVector? = null
private var _cleaningServices: ImageVector? = null
private var _closedCaption: ImageVector? = null
private var _closedCaptionOff: ImageVector? = null
private var _cloud: ImageVector? = null
private var _cloudDone: ImageVector? = null
private var _cloudOff: ImageVector? = null
private var _cloudQueue: ImageVector? = null
private var _deleteOutline: ImageVector? = null
private var _deleteSweep: ImageVector? = null
private var _download: ImageVector? = null
private var _downloading: ImageVector? = null
private var _error: ImageVector? = null
private var _errorOutline: ImageVector? = null
private var _fastForward: ImageVector? = null
private var _fastRewind: ImageVector? = null
private var _fileDownload: ImageVector? = null
private var _graphicEq: ImageVector? = null
private var _highQuality: ImageVector? = null
private var _history: ImageVector? = null
private var _memory: ImageVector? = null
private var _movie: ImageVector? = null
private var _networkCheck: ImageVector? = null
private var _pause: ImageVector? = null
private var _pauseCircle: ImageVector? = null
private var _playCircle: ImageVector? = null
private var _playlistPlay: ImageVector? = null
private var _repeat: ImageVector? = null
private var _repeatOne: ImageVector? = null
private var _schedule: ImageVector? = null
private var _screenRotation: ImageVector? = null
private var _searchOff: ImageVector? = null
private var _skipNext: ImageVector? = null
private var _skipPrevious: ImageVector? = null
private var _source: ImageVector? = null
private var _storage: ImageVector? = null
private var _swapHoriz: ImageVector? = null
private var _volumeOff: ImageVector? = null
private var _volumeUp: ImageVector? = null
private var _calendarToday: ImageVector? = null
private var _category: ImageVector? = null
private var _expandLess: ImageVector? = null
private var _expandMore: ImageVector? = null
private var _public: ImageVector? = null
