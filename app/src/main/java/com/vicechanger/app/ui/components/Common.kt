package com.vicechanger.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vicechanger.app.ui.theme.ActionBrush
import com.vicechanger.app.ui.theme.ViceColors
import com.vicechanger.app.voice.VoicePreset

/** Rounded card used for every panel in the app. */
@Composable
fun VcPanel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = accent?.copy(alpha = 0.45f) ?: MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        content()
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    ViceIcon(VcIcon.Back, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun BrandHeader(tagline: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "\uD83C\uDF80", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "VICE CHANGER",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = tagline,
            style = MaterialTheme.typography.bodyMedium,
            color = ViceColors.RoseSoft,
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** Big tappable action used on the home screen and inside modes. */
@Composable
fun ActionCard(
    icon: VcIcon,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    VcPanel(modifier = modifier.fillMaxWidth(), accent = accent, onClick = if (enabled) onClick else null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                ViceIcon(icon, size = 24.dp, tint = if (enabled) accent else accent.copy(alpha = 0.4f))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    icon: VcIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    brush: Brush = ActionBrush,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(shape)
            .background(if (enabled) brush else Brush.linearGradient(listOf(Color(0xFF3A3350), Color(0xFF2C2740))))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ViceIcon(icon, size = 22.dp, tint = if (enabled) Color.White else ViceColors.TextSecondary)
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) Color.White else ViceColors.TextSecondary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    icon: VcIcon? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.secondary,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (enabled) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            ViceIcon(icon, size = 20.dp, tint = if (enabled) accent else ViceColors.TextSecondary)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else ViceColors.TextSecondary,
        )
    }
}

/** A measured level bar. Always fed from a real meter value, never a decorative animation. */
@Composable
fun LevelBar(
    label: String,
    value01: Float,
    color: Color = ViceColors.Mint,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(targetValue = value01.coerceIn(0f, 1f), label = "level")
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(animated * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated.coerceIn(0.001f, 1f))
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.65f), color))),
            )
        }
    }
}

/** Waveform drawn from the recorder's real level history. */
@Composable
fun WaveformStrip(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = ViceColors.RoseSoft,
    bars: Int = 48,
) {
    Canvas(modifier = modifier) {
        val data = when {
            levels.isEmpty() -> List(bars) { 0.04f }
            levels.size >= bars -> levels.takeLast(bars)
            else -> List(bars - levels.size) { 0.04f } + levels
        }
        val slot = size.width / bars
        data.forEachIndexed { index, level ->
            val height = (size.height * level.coerceIn(0.04f, 1f))
            val x = slot * index + slot / 2f
            drawLine(
                color = color.copy(alpha = 0.35f + 0.65f * level.coerceIn(0f, 1f)),
                start = Offset(x, size.height / 2f - height / 2f),
                end = Offset(x, size.height / 2f + height / 2f),
                strokeWidth = slot * 0.55f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable
fun StatusDot(active: Boolean, label: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "status")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(12.dp)) {
            val color = if (active) ViceColors.Mint else ViceColors.TextSecondary
            if (active) {
                drawCircle(color.copy(alpha = 0.28f * pulse), radius = size.minDimension * 0.5f)
            }
            drawCircle(color, radius = size.minDimension * 0.3f)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) ViceColors.Mint else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    accent: Color = ViceColors.Mint,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
        )
    }
}

/** Voice card used in the picker and in the horizontal strip. */
@Composable
fun PresetCard(
    preset: VoicePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (selected) ViceColors.Rose else MaterialTheme.colorScheme.outline
    VcPanel(modifier = modifier, accent = accent, onClick = onClick) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) ViceColors.Rose else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    ViceIcon(VcIcon.Check, size = 18.dp, tint = ViceColors.Rose)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = preset.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = preset.summary(),
                style = MaterialTheme.typography.labelMedium,
                color = ViceColors.Mint,
            )
        }
    }
}

@Composable
fun VcSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueText: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = ViceColors.Mint,
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = ViceColors.Rose,
                activeTrackColor = ViceColors.Rose,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
fun VcSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ViceColors.Rose,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

enum class BannerTone { INFO, SUCCESS, WARNING, ERROR }

@Composable
fun MessageBanner(text: String, tone: BannerTone = BannerTone.INFO, modifier: Modifier = Modifier) {
    val accent = when (tone) {
        BannerTone.INFO -> ViceColors.Violet
        BannerTone.SUCCESS -> ViceColors.Mint
        BannerTone.WARNING -> ViceColors.Amber
        BannerTone.ERROR -> ViceColors.Red
    }
    val icon = when (tone) {
        BannerTone.SUCCESS -> VcIcon.Check
        BannerTone.WARNING -> VcIcon.Warning
        BannerTone.ERROR -> VcIcon.Warning
        BannerTone.INFO -> VcIcon.Info
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ViceIcon(icon, size = 18.dp, tint = accent)
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Small chip showing the voice currently armed. */
@Composable
fun VoiceChip(preset: VoicePreset, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ViceColors.Rose.copy(alpha = 0.16f))
            .border(1.dp, ViceColors.Rose.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawRoundRect(
                color = ViceColors.RoseSoft,
                topLeft = Offset(0f, size.height * 0.3f),
                size = Size(size.width, size.height * 0.4f),
                cornerRadius = CornerRadius(size.height * 0.2f),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelLarge,
            color = ViceColors.RoseSoft,
        )
    }
}
