package app.ailiaili.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.canFollowSystemAccent
import app.ailiaili.core.designsystem.AliColors
import app.ailiaili.core.designsystem.AliMaterial
import app.ailiaili.core.designsystem.BiliFilterChip
import app.ailiaili.core.designsystem.BiliSettingsLabel
import app.ailiaili.core.designsystem.aliMaterial

private val themePresets = listOf(
    "" to "默认",
    "007AFF" to "亮蓝",
    "FF8D28" to "暖橙",
    "8A78AC" to "烟紫",
    "6F85AC" to "雾蓝",
    "66877C" to "石绿",
    "AA8C62" to "砂金",
)
private val themeHex = Regex("[0-9A-Fa-f]{6}")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeAccentSettings(
    current: String,
    followSystem: Boolean,
    onSelect: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .36f
    BiliSettingsLabel("主题色", modifier, supporting = "默认用雾蓝突出主要操作、选中状态和进度。")
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (canFollowSystemAccent()) {
            BiliFilterChip(
                selected = followSystem,
                onClick = { onSelect(current, !followSystem) },
                label = { Text("跟随系统") },
            )
        }
        themePresets.forEach { (hex, label) ->
            val dot = hex.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
                ?: if (dark) AliColors.DarkAccent else AliColors.LightAccent
            BiliFilterChip(
                selected = !followSystem && current.equals(hex, ignoreCase = true),
                onClick = { onSelect(hex, false) },
                label = { Text(label) },
                leadingIcon = { Box(Modifier.size(13.dp).background(dot, CircleShape)) },
            )
        }
    }
    ThemeSurfacePreview()
    var draft by rememberSaveable(current) { mutableStateOf(current) }
    val hex = draft.trim().removePrefix("#")
    val isMalformed = hex.length > 6 || hex.any { it !in '0'..'9' && it !in 'A'..'F' && it !in 'a'..'f' }
    val canApply = (draft.isBlank() || themeHex.matches(hex)) && (followSystem || !hex.equals(current, ignoreCase = true))
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        singleLine = true,
        isError = isMalformed,
        label = { Text("自定义色值") },
        placeholder = { Text("例如 #6F85AC") },
        supportingText = if (isMalformed) ({ Text("请输入 6 位十六进制色值") }) else null,
        trailingIcon = { TextButton(onClick = { onSelect(hex.uppercase(), false) }, enabled = canApply) { Text("使用") } },
    )
}

@Composable
private fun ThemeSurfacePreview() {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)
            .background(colors.surfaceContainerLow, MaterialTheme.shapes.medium).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val samples = listOf(
            "页面" to Modifier.background(colors.background, shape),
            "卡片" to Modifier.aliMaterial(AliMaterial.Panel, shape),
            "浮层" to Modifier.aliMaterial(AliMaterial.Floating, shape),
            "选中" to Modifier.background(colors.primaryContainer, shape),
        )
        samples.forEach { (label, material) ->
            Box(Modifier.weight(1f).heightIn(min = 35.dp).then(material), contentAlignment = Alignment.Center) {
                Text(
                    label,
                    color = if (label == "选中") colors.onPrimaryContainer else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
