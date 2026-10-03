package com.ponkutools.gameassistant

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ponkutools.gameassistant.automation.AutomationAccessibilityService
import com.ponkutools.gameassistant.automation.AutomationRuntimeState
import com.ponkutools.gameassistant.automation.AutomationService
import com.ponkutools.gameassistant.automation.GameTarget
import com.ponkutools.gameassistant.data.AppSettings
import com.ponkutools.gameassistant.logging.AutomationLogger
import com.ponkutools.gameassistant.logging.LogEntry
import com.ponkutools.gameassistant.tasks.forge.ForgeAttributeCategory
import com.ponkutools.gameassistant.tasks.forge.ForgeBookPolicy
import com.ponkutools.gameassistant.tasks.forge.ForgeConfig
import com.ponkutools.gameassistant.tasks.forge.ForgeSlotConfig
import com.ponkutools.gameassistant.tasks.forge.ForgeStats
import com.ponkutools.gameassistant.tasks.forge.ForgeMode
import com.ponkutools.gameassistant.tasks.forge.ForgeStatDefinition
import com.ponkutools.gameassistant.tasks.AutomationTaskKind
import com.ponkutools.gameassistant.ui.GlassCard
import com.ponkutools.gameassistant.ui.MdSpacing
import com.ponkutools.gameassistant.ui.PonkuBackground
import com.ponkutools.gameassistant.ui.PonkuTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var appSettings: AppSettings
    private var accessibilityEnabledInSettings by mutableStateOf(false)
    private var notificationAllowed by mutableStateOf(false)
    private var gameInstalled by mutableStateOf(false)
    private var waitingForNotificationPermission = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ContextCompat.startForegroundService(
                this,
                AutomationService.startIntent(this, result.resultCode, data),
            )
            AutomationLogger.log(this, "必要权限已通过，正在启动${appSettings.loadTaskKind().displayName}服务")
        } else {
            AutomationLogger.log(this, "已取消必要权限")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) AutomationLogger.log(this, "通知权限未授予，前台服务通知可能不可见")
        refreshStatus()
        if (waitingForNotificationPermission) {
            waitingForNotificationPermission = false
            requestProjection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appSettings = AppSettings(this)
        refreshStatus()
        AutomationLogger.compactVerboseHistory(this)

        setContent {
            PonkuTheme {
                val logs by AutomationLogger.entries.collectAsStateWithLifecycle()
                val running by AutomationRuntimeState.running.collectAsStateWithLifecycle()
                val taskActive by AutomationRuntimeState.taskActive.collectAsStateWithLifecycle()
                val paused by AutomationRuntimeState.paused.collectAsStateWithLifecycle()
                val accessibilityConnected by AutomationAccessibilityService.connected.collectAsStateWithLifecycle()
                var config by remember { mutableStateOf(appSettings.loadForgeConfig()) }
                var taskKind by remember { mutableStateOf(appSettings.loadTaskKind()) }
                var tapDurationMs by remember { mutableStateOf(appSettings.loadTapDurationMs()) }

                if (accessibilityConnected && notificationAllowed) {
                    AppScreen(
                        config = config,
                        taskKind = taskKind,
                        onTaskKindChanged = {
                            taskKind = it
                            when (it) {
                                AutomationTaskKind.SOULMATE_FORGE -> config = config.withMode(ForgeMode.SOULMATE)
                                AutomationTaskKind.ARTIFACT_FORGE -> config = config.withMode(ForgeMode.ARTIFACT)
                                AutomationTaskKind.DAILY_CLEANUP -> Unit
                            }
                        },
                        onConfigChanged = { config = it },
                        onSaveConfig = {
                            appSettings.saveForgeConfig(it)
                            AutomationLogger.log(this, "三个词条槽配置已保存")
                        },
                        tapDurationMs = tapDurationMs,
                        onTapDurationChanged = { durationMs ->
                            tapDurationMs = durationMs
                            appSettings.saveTapDurationMs(durationMs)
                        },
                        accessibilityAllowed = accessibilityConnected,
                        gameInstalled = gameInstalled,
                        running = running,
                        taskActive = taskActive,
                        paused = paused,
                        logs = logs,
                        onStart = { startAutomation(config, taskKind) },
                        onStop = { startService(AutomationService.stopIntent(this)) },
                        onClearLog = { AutomationLogger.clear(this) },
                    )
                } else {
                    PermissionGate(
                        accessibilityEnabledInSettings = accessibilityEnabledInSettings,
                        accessibilityConnected = accessibilityConnected,
                        notificationAllowed = notificationAllowed,
                        onAccessibilityPermission = ::openAccessibilitySettings,
                        onNotificationPermission = ::requestNotificationPermission,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun startAutomation(config: ForgeConfig, taskKind: AutomationTaskKind) {
        appSettings.saveTaskKind(taskKind)
        appSettings.saveForgeConfig(config)
        if (taskKind != AutomationTaskKind.DAILY_CLEANUP &&
            config.slots.any { slot -> slot.enabled && slot.thresholdFor(slot.targetStat()) <= 0 }
        ) {
            AutomationLogger.log(this, "目标阈值必须大于 0")
            return
        }
        if (!AutomationAccessibilityService.isConnected) {
            AutomationLogger.log(
                this,
                if (isAccessibilityServiceEnabledInSettings()) {
                    "无障碍设置已开启，但点击服务未连接；请在系统设置中关闭后重新开启"
                } else {
                    "请先启用自动操作权限"
                },
            )
            openAccessibilitySettings()
            return
        }
        if (!gameInstalled) {
            AutomationLogger.log(this, "未安装 ${GameTarget.DISPLAY_NAME}，无法自动返回游戏")
            return
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            waitingForNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestProjection()
        }
    }

    private fun requestProjection() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            refreshStatus()
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun refreshStatus() {
        accessibilityEnabledInSettings = isAccessibilityServiceEnabledInSettings()
        notificationAllowed = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        gameInstalled = packageManager.getLaunchIntentForPackage(GameTarget.PACKAGE_NAME) != null
    }

    private fun isAccessibilityServiceEnabledInSettings(): Boolean {
        val expected = ComponentName(this, AutomationAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { component ->
            component.equals(expected, ignoreCase = true) ||
                ComponentName.unflattenFromString(component) == ComponentName(this, AutomationAccessibilityService::class.java)
        }
    }
}

@Composable
private fun PermissionGate(
    accessibilityEnabledInSettings: Boolean,
    accessibilityConnected: Boolean,
    notificationAllowed: Boolean,
    onAccessibilityPermission: () -> Unit,
    onNotificationPermission: () -> Unit,
) {
    PonkuBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("启动前需要权限", style = MaterialTheme.typography.headlineMedium)
                        Text("全部权限通过后才会进入任务配置页面。")
                        PermissionRow("自动操作", accessibilityConnected)
                        PermissionRow("前台服务通知", notificationAllowed)
                        if (accessibilityEnabledInSettings && !accessibilityConnected) {
                            Text(
                                "系统设置中显示已开启，但自动操作服务没有连接。请进入无障碍设置，先关闭邦古工具箱，再重新开启。",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (!accessibilityConnected) {
                            Button(onClick = onAccessibilityPermission, modifier = Modifier.fillMaxWidth()) {
                                Text(if (accessibilityEnabledInSettings) "重新连接自动操作" else "启用自动操作")
                            }
                        } else if (!notificationAllowed) {
                            Button(onClick = onNotificationPermission, modifier = Modifier.fillMaxWidth()) { Text("授予通知权限") }
                        }
                        Text("启动任务时，系统会再请求一次必要权限。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (granted) "●" else "○", color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(8.dp))
        Text("$label：${if (granted) "已授权" else "待授权"}")
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppScreen(
    config: ForgeConfig,
    taskKind: AutomationTaskKind,
    onTaskKindChanged: (AutomationTaskKind) -> Unit,
    onConfigChanged: (ForgeConfig) -> Unit,
    onSaveConfig: (ForgeConfig) -> Unit,
    tapDurationMs: Int,
    onTapDurationChanged: (Int) -> Unit,
    accessibilityAllowed: Boolean,
    gameInstalled: Boolean,
    running: Boolean,
    taskActive: Boolean,
    paused: Boolean,
    logs: List<LogEntry>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClearLog: () -> Unit,
) {
    PonkuBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 1.dp,
                ) {
                    CenterAlignedTopAppBar(
                        title = { Text("邦古工具箱") },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                        actions = {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text(if (running) "运行中" else "就绪") },
                            )
                        },
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(MdSpacing.sm).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MdSpacing.xs),
            ) {
                StatusCard(accessibilityAllowed, gameInstalled, running, taskActive, paused)

                TaskKindCard(
                    selected = taskKind,
                    enabled = !taskActive,
                    onSelected = onTaskKindChanged,
                )
                TapDurationCard(
                    durationMs = tapDurationMs,
                    enabled = !taskActive,
                    onDurationChanged = onTapDurationChanged,
                )
                if (taskKind == AutomationTaskKind.DAILY_CLEANUP) {
                    GlassCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(MdSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(MdSpacing.xs),
                        ) {
                            Text("清理日常（beta）", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "要求位于首页或者关卡页开始，自动训练室，魂仗，探索，欧特，答题，每日商店50石，未经测试完善可能不是很好用。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else {
                    ConfigCard(config, onConfigChanged, onSaveConfig, enabled = !taskActive)
                }

                Button(onClick = if (running) onStop else onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(if (running) "停止服务" else "启动服务并返回 CraveSaga")
                }
                Text(
                    if (running) "点击悬浮 ▶ 开始；点击 Ⅱ 会清空当前任务，下次从主页面重新识别。" else "启动后不会立刻执行任务。",
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("运行日志", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onClearLog) { Text("清空") }
                }
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(340.dp).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (logs.isEmpty()) item { Text("暂无日志") }
                        else items(logs.asReversed()) { entry ->
                            Text(entry.toString(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

}

@Composable
private fun TapDurationCard(
    durationMs: Int,
    enabled: Boolean,
    onDurationChanged: (Int) -> Unit,
) {
    GlassCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MdSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MdSpacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("点击按住时长", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("${durationMs}ms", style = MaterialTheme.typography.labelLarge)
            }
            Slider(
                value = durationMs.toFloat(),
                onValueChange = { rawValue ->
                    val stepped = (
                        rawValue / AppSettings.TAP_DURATION_STEP_MS
                    ).roundToInt() * AppSettings.TAP_DURATION_STEP_MS
                    onDurationChanged(
                        stepped.coerceIn(
                            AppSettings.MIN_TAP_DURATION_MS,
                            AppSettings.MAX_TAP_DURATION_MS,
                        ),
                    )
                },
                enabled = enabled,
                valueRange = AppSettings.MIN_TAP_DURATION_MS.toFloat()..
                    AppSettings.MAX_TAP_DURATION_MS.toFloat(),
                steps = (
                    (AppSettings.MAX_TAP_DURATION_MS - AppSettings.MIN_TAP_DURATION_MS) /
                        AppSettings.TAP_DURATION_STEP_MS
                ) - 1,
            )
        }
    }
}

@Composable
private fun TaskKindCard(
    selected: AutomationTaskKind,
    enabled: Boolean,
    onSelected: (AutomationTaskKind) -> Unit,
) {
    GlassCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MdSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MdSpacing.xs),
        ) {
            Text("任务类型", style = MaterialTheme.typography.titleLarge)
            SelectionBox(
                selectedLabel = selected.displayName,
                options = AutomationTaskKind.entries.toList(),
                optionLabel = { it.displayName },
                enabled = enabled,
                onSelected = onSelected,
            )
        }
    }
}

@Composable
private fun ConfigCard(
    config: ForgeConfig,
    onConfigChanged: (ForgeConfig) -> Unit,
    onSaveConfig: (ForgeConfig) -> Unit,
    enabled: Boolean,
) {
    var expandedSlot by rememberSaveable { mutableStateOf<Int?>(null) }

    GlassCard {
        Column(modifier = Modifier.fillMaxWidth().padding(MdSpacing.sm), verticalArrangement = Arrangement.spacedBy(MdSpacing.xs)) {
            Text("启动前配置", style = MaterialTheme.typography.titleLarge)
            Text(
                "先选择目标词条，保存词条配置启动服务，开始前要进入锻造页面，书不够了可能不会自动换，防御和体力没有调优过，可能会不准（但估计也没人洗）",
                style = MaterialTheme.typography.bodySmall,
            )

            repeat(ForgeConfig.SLOT_COUNT) { slotIndex ->
                val slotConfig = config[slotIndex]
                SlotConfigPanel(
                    slotIndex = slotIndex,
                    slotConfig = slotConfig,
                    expanded = expandedSlot == slotIndex,
                    editingEnabled = enabled,
                    onExpandedChange = {
                        expandedSlot = if (expandedSlot == slotIndex) null else slotIndex
                    },
                    onSlotChanged = { updated ->
                        onConfigChanged(config.updateSlot(slotIndex, updated))
                        if (!updated.enabled && expandedSlot == slotIndex) expandedSlot = null
                    },
                )
            }

            Button(
                onClick = { onSaveConfig(config) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            ) {
                Text("保存三个词条槽配置")
            }
        }
    }
}

private enum class ConfigTone {
    CATEGORY,
    TARGET,
    ALTERNATIVE_ONE,
    ALTERNATIVE_TWO,
    POLICY,
}

private data class ConfigToneColors(
    val container: Color,
    val content: Color,
    val accent: Color,
)

@Composable
private fun configToneColors(tone: ConfigTone): ConfigToneColors = when (tone) {
    ConfigTone.CATEGORY -> ConfigToneColors(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.outline,
    )
    ConfigTone.TARGET -> ConfigToneColors(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.primary,
    )
    ConfigTone.ALTERNATIVE_ONE -> ConfigToneColors(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.secondary,
    )
    ConfigTone.ALTERNATIVE_TWO -> ConfigToneColors(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.tertiary,
    )
    ConfigTone.POLICY -> ConfigToneColors(
        MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun ConfigSection(
    title: String,
    tone: ConfigTone,
    supportingText: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = configToneColors(tone)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.container.copy(alpha = 0.72f), MaterialTheme.shapes.small)
            .padding(MdSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(MdSpacing.xs),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.content)
        supportingText?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.content.copy(alpha = 0.78f))
        }
        content()
    }
}

@Composable
private fun SlotConfigPanel(
    slotIndex: Int,
    slotConfig: ForgeSlotConfig,
    expanded: Boolean,
    editingEnabled: Boolean,
    onExpandedChange: () -> Unit,
    onSlotChanged: (ForgeSlotConfig) -> Unit,
) {
    val targetStat = slotConfig.targetStat()
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(MdSpacing.xs), verticalArrangement = Arrangement.spacedBy(MdSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("属性槽 ${slotIndex + 1}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (slotConfig.enabled) {
                            val configured = slotConfig.acceptedStats().joinToString(" / ") { stat ->
                                "${stat.displayName} ≥ ${slotConfig.thresholdFor(stat)}${if (stat.percentage) "%" else ""}"
                            }
                            "${slotConfig.category.displayName} → $configured"
                        } else {
                            "手动跳过"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (slotConfig.enabled) "启用" else "跳过", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = slotConfig.enabled,
                        onCheckedChange = { onSlotChanged(slotConfig.copy(enabled = it)) },
                        enabled = editingEnabled,
                    )
                }
            }

            if (slotConfig.enabled) {
                OutlinedButton(
                    onClick = onExpandedChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = editingEnabled,
                ) {
                    Text(if (expanded) "收起具体配置 ▲" else "展开具体配置 ▼")
                }
            }

            if (expanded && slotConfig.enabled) {
                HorizontalDivider()
                if (slotConfig.mode == ForgeMode.SOULMATE) {
                    ConfigSection("属性类别", ConfigTone.CATEGORY) {
                        SelectionBox(
                            selectedLabel = slotConfig.category.displayName,
                            options = ForgeAttributeCategory.forMode(slotConfig.mode),
                            optionLabel = { it.displayName },
                            enabled = editingEnabled,
                            tone = ConfigTone.CATEGORY,
                            onSelected = { onSlotChanged(slotConfig.withCategory(it)) },
                        )
                    }
                }

                ConfigSection("目标词条", ConfigTone.TARGET) {
                    SelectionBox(
                        selectedLabel = targetStat.displayName,
                        options = ForgeStats.forCategory(slotConfig.mode, slotConfig.category),
                        optionLabel = { it.displayName },
                        enabled = editingEnabled,
                        tone = ConfigTone.TARGET,
                        onSelected = { onSlotChanged(slotConfig.copy(targetStatId = it.id)) },
                    )
                    ThresholdField(
                        slotIndex = slotIndex,
                        slotConfig = slotConfig,
                        stat = targetStat,
                        enabled = editingEnabled,
                        tone = ConfigTone.TARGET,
                        onSlotChanged = onSlotChanged,
                    )
                }

                repeat(ForgeSlotConfig.MAX_ALTERNATIVES) { alternativeIndex ->
                    val tone = if (alternativeIndex == 0) ConfigTone.ALTERNATIVE_ONE else ConfigTone.ALTERNATIVE_TWO
                    val alternative = slotConfig.alternativeStats().getOrNull(alternativeIndex)
                    val configuredAlternativeIds = slotConfig.alternativeStats().map { it.id }
                    val alternatives = ForgeStats.forCategory(slotConfig.mode, slotConfig.category)
                        .filter { candidate ->
                            candidate.id != targetStat.id &&
                                candidate.id !in configuredAlternativeIds ||
                                candidate.id == alternative?.id
                        }
                    ConfigSection(
                        title = "备选词条 ${alternativeIndex + 1}",
                        tone = tone,
                        supportingText = "达标后直接完成，不参与同类型替换",
                    ) {
                        SelectionBox(
                            selectedLabel = alternative?.displayName ?: "不设置",
                            options = listOf<ForgeStatDefinition?>(null) + alternatives,
                            optionLabel = { option -> option?.displayName ?: "不设置" },
                            enabled = editingEnabled,
                            tone = tone,
                            onSelected = { option ->
                                onSlotChanged(slotConfig.withAlternative(alternativeIndex, option?.id))
                            },
                        )
                        alternative?.let {
                            ThresholdField(
                                slotIndex = slotIndex,
                                slotConfig = slotConfig,
                                stat = it,
                                enabled = editingEnabled,
                                tone = tone,
                                onSlotChanged = onSlotChanged,
                            )
                        }
                    }
                }

                ConfigSection("洗练道具策略", ConfigTone.POLICY) {
                    SelectionBox(
                        selectedLabel = slotConfig.bookPolicy.displayName,
                        options = ForgeBookPolicy.entries.toList(),
                        optionLabel = { it.displayName },
                        enabled = editingEnabled,
                        tone = ConfigTone.POLICY,
                        onSelected = { onSlotChanged(slotConfig.copy(bookPolicy = it)) },
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("始终保留较大的同类型结果", style = MaterialTheme.typography.bodyLarge)
                        Text("目标词条未达标时继续洗练", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = slotConfig.preserveBetterSameType,
                        onCheckedChange = { onSlotChanged(slotConfig.copy(preserveBetterSameType = it)) },
                        enabled = editingEnabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThresholdField(
    slotIndex: Int,
    slotConfig: ForgeSlotConfig,
    stat: com.ponkutools.gameassistant.tasks.forge.ForgeStatDefinition,
    enabled: Boolean,
    tone: ConfigTone,
    onSlotChanged: (ForgeSlotConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val toneColors = configToneColors(tone)
    var thresholdText by rememberSaveable(slotIndex, stat.id) {
        mutableStateOf(slotConfig.thresholdFor(stat).toString())
    }
    fun commitThreshold() {
        val normalized = thresholdText.toIntOrNull()
            ?.coerceIn(1, stat.defaultThreshold)
            ?: slotConfig.thresholdFor(stat)
        thresholdText = normalized.toString()
        onSlotChanged(slotConfig.withThreshold(stat.id, normalized))
    }
    OutlinedTextField(
        value = thresholdText,
        onValueChange = { raw ->
            thresholdText = raw.filter(Char::isDigit).take(6)
            thresholdText.toIntOrNull()?.let { onSlotChanged(slotConfig.withThreshold(stat.id, it)) }
        },
        modifier = Modifier.fillMaxWidth().onFocusChanged {
            if (!it.isFocused) commitThreshold()
        },
        enabled = enabled,
        label = { Text("${stat.displayName} 达标数值（最大 ${stat.defaultThreshold}）") },
        suffix = { if (stat.percentage) Text("%") },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = toneColors.container,
            unfocusedContainerColor = toneColors.container.copy(alpha = 0.58f),
            focusedBorderColor = toneColors.accent,
            unfocusedBorderColor = toneColors.accent.copy(alpha = 0.62f),
            focusedTextColor = toneColors.content,
            unfocusedTextColor = toneColors.content,
            focusedLabelColor = toneColors.accent,
            unfocusedLabelColor = toneColors.content.copy(alpha = 0.76f),
            focusedSuffixColor = toneColors.content,
            unfocusedSuffixColor = toneColors.content,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = {
            commitThreshold()
            focusManager.clearFocus()
        }),
        singleLine = true,
    )
}

@Composable
private fun <T> SelectionBox(
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    enabled: Boolean,
    tone: ConfigTone? = null,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val toneColors = tone?.let { configToneColors(it) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = toneColors?.container ?: Color.Transparent,
                contentColor = toneColors?.content ?: MaterialTheme.colorScheme.primary,
            ),
            border = BorderStroke(
                1.dp,
                toneColors?.accent ?: MaterialTheme.colorScheme.outline,
            ),
        ) {
            Text(selectedLabel, modifier = Modifier.weight(1f))
            Text("▼")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    accessibilityAllowed: Boolean,
    gameInstalled: Boolean,
    running: Boolean,
    taskActive: Boolean,
    paused: Boolean,
) {
    val taskStatus = when {
        paused -> "已暂停（下次重新识别）"
        !taskActive -> "未运行"
        else -> "执行中"
    }
    val rows = remember(accessibilityAllowed, gameInstalled, running, taskStatus) {
        listOf(
            "自动操作" to accessibilityAllowed,
            "CraveSaga 已安装" to gameInstalled,
            "服务运行中" to running,
        )
    }
    GlassCard {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            rows.forEachIndexed { index, (label, ok) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (ok) "●" else "○", color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
                if (index != rows.lastIndex) Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("当前任务状态：$taskStatus", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
