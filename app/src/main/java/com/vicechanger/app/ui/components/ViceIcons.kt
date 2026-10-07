package com.vicechanger.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's icon set, drawn with primitives instead of shipping an icon font.
 *
 * Two reasons: the APK stays small, and the microphone/waveform marks are original artwork
 * rather than borrowed brand assets - VICE CHANGER is required to have its own identity.
 */
enum class VcIcon {
    Mic,
    MicOff,
    Waveform,
    Play,
    Stop,
    Record,
    Delete,
    Share,
    Save,
    Settings,
    Gamepad,
    Chat,
    Sliders,
    Back,
    Check,
    Info,
    Warning,
    Refresh,
    Volume,
    Sparkle,
    Folder,
    Route,
}

@Composable
fun ViceIcon(
    icon: VcIcon,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    strokeWidth: Float = 2f,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        when (icon) {
            VcIcon.Mic -> drawMic(tint, strokeWidth)
            VcIcon.MicOff -> {
                drawMic(tint, strokeWidth)
                drawLine(
                    color = tint,
                    start = Offset(this.size.width * 0.18f, this.size.height * 0.82f),
                    end = Offset(this.size.width * 0.82f, this.size.height * 0.18f),
                    strokeWidth = strokeWidth + 0.4f,
                    cap = StrokeCap.Round,
                )
            }
            VcIcon.Waveform -> drawWaveform(tint, strokeWidth)
            VcIcon.Play -> {
                val path = Path().apply {
                    moveTo(this@Canvas.size.width * 0.32f, this@Canvas.size.height * 0.22f)
                    lineTo(this@Canvas.size.width * 0.80f, this@Canvas.size.height * 0.5f)
                    lineTo(this@Canvas.size.width * 0.32f, this@Canvas.size.height * 0.78f)
                    close()
                }
                drawPath(path, tint)
            }
            VcIcon.Stop -> drawRoundRect(
                color = tint,
                topLeft = Offset(this.size.width * 0.24f, this.size.height * 0.24f),
                size = Size(this.size.width * 0.52f, this.size.height * 0.52f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(this.size.width * 0.1f),
            )
            VcIcon.Record -> {
                drawCircle(color = tint, radius = this.size.minDimension * 0.22f)
                drawCircle(
                    color = tint,
                    radius = this.size.minDimension * 0.40f,
                    style = Stroke(width = strokeWidth),
                )
            }
            VcIcon.Delete -> drawDelete(tint, strokeWidth)
            VcIcon.Share -> drawShare(tint, strokeWidth)
            VcIcon.Save -> drawSave(tint, strokeWidth)
            VcIcon.Settings -> drawGear(tint, strokeWidth)
            VcIcon.Gamepad -> drawGamepad(tint, strokeWidth)
            VcIcon.Chat -> drawChat(tint, strokeWidth)
            VcIcon.Sliders -> drawSliders(tint, strokeWidth)
            VcIcon.Back -> {
                drawLine(
                    color = tint,
                    start = Offset(this.size.width * 0.66f, this.size.height * 0.2f),
                    end = Offset(this.size.width * 0.34f, this.size.height * 0.5f),
                    strokeWidth = strokeWidth + 0.6f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(this.size.width * 0.34f, this.size.height * 0.5f),
                    end = Offset(this.size.width * 0.66f, this.size.height * 0.8f),
                    strokeWidth = strokeWidth + 0.6f,
                    cap = StrokeCap.Round,
                )
            }
            VcIcon.Check -> {
                drawLine(
                    color = tint,
                    start = Offset(this.size.width * 0.24f, this.size.height * 0.54f),
                    end = Offset(this.size.width * 0.44f, this.size.height * 0.74f),
                    strokeWidth = strokeWidth + 0.6f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(this.size.width * 0.44f, this.size.height * 0.74f),
                    end = Offset(this.size.width * 0.78f, this.size.height * 0.28f),
                    strokeWidth = strokeWidth + 0.6f,
                    cap = StrokeCap.Round,
                )
            }
            VcIcon.Info -> {
                drawCircle(
                    color = tint,
                    radius = this.size.minDimension * 0.42f,
                    style = Stroke(width = strokeWidth),
                )
                drawCircle(color = tint, radius = this.size.minDimension * 0.055f,
                    center = Offset(this.size.width * 0.5f, this.size.height * 0.33f))
                drawLine(
                    color = tint,
                    start = Offset(this.size.width * 0.5f, this.size.height * 0.46f),
                    end = Offset(this.size.width * 0.5f, this.size.height * 0.7f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            VcIcon.Warning -> {
                val path = Path().apply {
                    moveTo(this@Canvas.size.width * 0.5f, this@Canvas.size.height * 0.16f)
                    lineTo(this@Canvas.size.width * 0.9f, this@Canvas.size.height * 0.84f)
                    lineTo(this@Canvas.size.width * 0.1f, this@Canvas.size.height * 0.84f)
                    close()
                }
                drawPath(path, tint, style = Stroke(width = strokeWidth, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawLine(
                    color = tint,
                    start = Offset(this.size.width * 0.5f, this.size.height * 0.42f),
                    end = Offset(this.size.width * 0.5f, this.size.height * 0.62f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawCircle(color = tint, radius = this.size.minDimension * 0.05f,
                    center = Offset(this.size.width * 0.5f, this.size.height * 0.73f))
            }
            VcIcon.Refresh -> {
                drawArc(
                    color = tint,
                    startAngle = 40f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = Offset(this.size.width * 0.16f, this.size.height * 0.16f),
                    size = Size(this.size.width * 0.68f, this.size.height * 0.68f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                val path = Path().apply {
                    moveTo(this@Canvas.size.width * 0.74f, this@Canvas.size.height * 0.12f)
                    lineTo(this@Canvas.size.width * 0.88f, this@Canvas.size.height * 0.34f)
                    lineTo(this@Canvas.size.width * 0.62f, this@Canvas.size.height * 0.32f)
                    close()
                }
                drawPath(path, tint)
            }
            VcIcon.Volume -> drawVolume(tint, strokeWidth)
            VcIcon.Sparkle -> {
                val path = Path().apply {
                    moveTo(this@Canvas.size.width * 0.5f, this@Canvas.size.height * 0.12f)
                    lineTo(this@Canvas.size.width * 0.6f, this@Canvas.size.height * 0.42f)
                    lineTo(this@Canvas.size.width * 0.88f, this@Canvas.size.height * 0.5f)
                    lineTo(this@Canvas.size.width * 0.6f, this@Canvas.size.height * 0.58f)
                    lineTo(this@Canvas.size.width * 0.5f, this@Canvas.size.height * 0.88f)
                    lineTo(this@Canvas.size.width * 0.4f, this@Canvas.size.height * 0.58f)
                    lineTo(this@Canvas.size.width * 0.12f, this@Canvas.size.height * 0.5f)
                    lineTo(this@Canvas.size.width * 0.4f, this@Canvas.size.height * 0.42f)
                    close()
                }
                drawPath(path, tint)
            }
            VcIcon.Folder -> {
                val rect = Rect(
                    this.size.width * 0.14f,
                    this.size.height * 0.26f,
                    this.size.width * 0.86f,
                    this.size.height * 0.78f,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(this.size.width * 0.1f),
                    style = Stroke(width = strokeWidth),
                )
                drawLine(
                    color = tint,
                    start = Offset(this.size.width * 0.14f, this.size.height * 0.26f),
                    end = Offset(this.size.width * 0.44f, this.size.height * 0.12f),
                    strokeWidth = strokeWidth,
                )
            }
            VcIcon.Route -> {
                drawCircle(
                    color = tint,
                    radius = this.size.minDimension * 0.12f,
                    center = Offset(this.size.width * 0.24f, this.size.height * 0.26f),
                )
                drawCircle(
                    color = tint,
                    radius = this.size.minDimension * 0.12f,
                    center = Offset(this.size.width * 0.76f, this.size.height * 0.74f),
                )
                drawLine(
                    color = tint,
                    start = Offset(this.size.width * 0.24f, this.size.height * 0.38f),
                    end = Offset(this.size.width * 0.24f, this.size.height * 0.62f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(this.size.width * 0.24f, this.size.height * 0.62f),
                    end = Offset(this.size.width * 0.76f, this.size.height * 0.62f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(this.size.width * 0.76f, this.size.height * 0.62f),
                    end = Offset(this.size.width * 0.76f, this.size.height * 0.74f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun DrawScope.drawMic(tint: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.36f, h * 0.12f),
        size = Size(w * 0.28f, h * 0.44f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f, w * 0.14f),
    )
    drawArc(
        color = tint,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.22f, h * 0.34f),
        size = Size(w * 0.56f, h * 0.42f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
    drawLine(
        color = tint,
        start = Offset(w * 0.5f, h * 0.76f),
        end = Offset(w * 0.5f, h * 0.88f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = tint,
        start = Offset(w * 0.34f, h * 0.9f),
        end = Offset(w * 0.66f, h * 0.9f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawWaveform(tint: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    val heights = listOf(0.28f, 0.58f, 0.86f, 0.5f, 0.7f, 0.34f)
    heights.forEachIndexed { index, factor ->
        val x = w * (0.1f + index * 0.16f)
        val half = h * factor * 0.5f
        drawLine(
            color = tint,
            start = Offset(x, h * 0.5f - half),
            end = Offset(x, h * 0.5f + half),
            strokeWidth = strokeWidth + 0.6f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawShare(tint: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    drawCircle(tint, w * 0.11f, Offset(w * 0.74f, h * 0.22f), style = Stroke(width = strokeWidth))
    drawCircle(tint, w * 0.11f, Offset(w * 0.26f, h * 0.5f), style = Stroke(width = strokeWidth))
    drawCircle(tint, w * 0.11f, Offset(w * 0.74f, h * 0.78f), style = Stroke(width = strokeWidth))
    drawLine(tint, Offset(w * 0.36f, h * 0.44f), Offset(w * 0.64f, h * 0.28f), strokeWidth, StrokeCap.Round)
    drawLine(tint, Offset(w * 0.36f, h * 0.56f), Offset(w * 0.64f, h * 0.72f), strokeWidth, StrokeCap.Round)
}

private fun DrawScope.drawDelete(tint: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    drawLine(tint, Offset(w * 0.2f, h * 0.28f), Offset(w * 0.8f, h * 0.28f), strokeWidth, StrokeCap.Round)
    drawLine(tint, Offset(w * 0.42f, h * 0.2f), Offset(w * 0.5f, h * 0.14f), strokeWidth, StrokeCap.Round)
    drawLine(tint, Offset(w * 0.58f, h * 0.2f), Offset(w * 0.5f, h * 0.14f), strokeWidth, StrokeCap.Round)
    val body = Path().apply {
        moveTo(w * 0.3f, h * 0.36f)
        lineTo(w * 0.36f, h * 0.84f)
        lineTo(w * 0.64f, h * 0.84f)
        lineTo(w * 0.7f, h * 0.36f)
    }
    drawPath(body, tint, style = Stroke(width = strokeWidth, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawLine(tint, Offset(w * 0.44f, h * 0.46f), Offset(w * 0.46f, h * 0.74f), strokeWidth * 0.8f, StrokeCap.Round)
    drawLine(tint, Offset(w * 0.56f, h * 0.46f), Offset(w * 0.54f, h * 0.74f), strokeWidth * 0.8f, StrokeCap.Round)
}

private fun DrawScope.drawSave(tint: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    drawLine(tint, Offset(w * 0.5f, h * 0.16f), Offset(w * 0.5f, h * 0.62f), strokeWidth, StrokeCap.Round)
    val arrow = Path().apply {
        moveTo(w * 0.34f, h * 0.46f)
        lineTo(w * 0.5f, h * 0.66f)
        lineTo(w * 0.66f, h * 0.46f)
    }
    drawPath(arrow, tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    drawLine(tint, Offset(w * 0.2f, h * 0.82f), Offset(w * 0.8f, h * 0.82f), strokeWidth, StrokeCap.Round)
}

private fun DrawScope.drawGear(tint: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    drawCircle(tint, w * 0.2f, Offset(w * 0.5f, h * 0.5f), style = Stroke(width = strokeWidth))
    drawCircle(tint, w * 0.06f, Offset(w * 0.5f, h * 0.5f))
    for (i in 0 until 8) {
        val angle = Math.toRadians(i * 45.0)
        val inner = w * 0.24f
        val outer = w * 0.4f
        drawLine(
            color = tint,
            start = Offset(
                w * 0.5f + (inner * kotlin.math.cos(angle)).toFloat(),
                h * 0.5f + (inner * kotlin.math.sin(angle)).toFloat(),
            ),
            end = Offset(
                w * 0.5f + (outer * kotlin.math.cos(angle)).toFloat(),
                h * 0.5f + (outer * kotlin.math.sin(angle)).toFloat(),
            ),
            strokeWidth = strokeWidth + 1.2f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawGamepad(tint: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.1f, h * 0.3f),
        size = Size(w * 0.8f, h * 0.44f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.22f, h * 0.22f),
        style = Stroke(width = strokeWidth),
    )
    drawLine(tint, Offset(w * 0.26f, h * 0.52f), Offset(w * 0.4f, h * 0.52f), strokeWidth, StrokeCap.Round)
    drawLine(tint, Offset(w * 0.33f, h * 0.45f), Offset(w * 0.33f, h * 0.59f), strokeWidth, StrokeCap.Round)
    drawCircle(tint, w * 0.045f, Offset(w * 0.68f, h * 0.46f))
    drawCircle(tint, w * 0.045f, Offset(w * 0.74f, h * 0.57f))
}

private fun DrawScope.drawChat(tint: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.12f, h * 0.2f),
        size = Size(w * 0.76f, h * 0.5f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f, w * 0.14f),
        style = Stroke(width = strokeWidth),
    )
    val tail = Path().apply {
        moveTo(w * 0.32f, h * 0.7f)
        lineTo(w * 0.34f, h * 0.88f)
        lineTo(w * 0.52f, h * 0.7f)
    }
    drawPath(tail, tint, style = Stroke(width = strokeWidth, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    for (i in 0 until 3) {
        drawCircle(tint, w * 0.045f, Offset(w * (0.32f + i * 0.18f), h * 0.45f))
    }
}

private fun DrawScope.drawSliders(tint: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    val rows = listOf(0.28f to 0.36f, 0.5f to 0.66f, 0.72f to 0.46f)
    rows.forEach { (y, knob) ->
        drawLine(tint, Offset(w * 0.16f, h * y), Offset(w * 0.84f, h * y), strokeWidth, StrokeCap.Round)
        drawCircle(tint, w * 0.1f, Offset(w * knob, h * y))
    }
}

private fun DrawScope.drawVolume(tint: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    val body = Path().apply {
        moveTo(w * 0.16f, h * 0.38f)
        lineTo(w * 0.34f, h * 0.38f)
        lineTo(w * 0.54f, h * 0.2f)
        lineTo(w * 0.54f, h * 0.8f)
        lineTo(w * 0.34f, h * 0.62f)
        lineTo(w * 0.16f, h * 0.62f)
        close()
    }
    drawPath(body, tint, style = Stroke(width = strokeWidth, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawArc(
        color = tint,
        startAngle = -55f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = Offset(w * 0.48f, h * 0.24f),
        size = Size(w * 0.44f, h * 0.52f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}
