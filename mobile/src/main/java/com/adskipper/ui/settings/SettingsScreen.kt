package com.adskipper.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adskipper.AdSkipperApp
import com.adskipper.core.data.AppSettings
import com.adskipper.core.detect.SystemSurfaceGuard
import kotlinx.coroutines.launch

/**
 * Settings tab.
 *
 * The whole page is one LazyColumn: the whitelist can hold a few hundred apps,
 * and the previous implementation put every row inside a vertically scrolling
 * Column, so entering the tab composed and laid out all of them at once — the
 * stutter reported on device. A LazyColumn also means the app list can be a
 * plain `items(...)` list instead of a nested scrollable, which Compose does
 * not allow anyway.
 *
 * The app list itself now comes from [InstalledAppCache] (queried once per
 * process), so tab switching no longer re-runs queryIntentActivities/loadLabel.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = AdSkipperApp.get(context)
    val settings by app.settingsRepo.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()

    val cachedApps by InstalledAppCache.apps.collectAsState()
    var search by remember { mutableStateOf("") }

    // Returns instantly after the first call in this process.
    LaunchedEffect(Unit) { InstalledAppCache.load(context) }

    val query = search.trim()
    val filtered = remember(cachedApps, query) {
        val all = cachedApps ?: emptyList()
        if (query.isEmpty()) {
            all
        } else {
            all.filter {
                it.second.contains(query, ignoreCase = true) ||
                    it.first.contains(query, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text("设置", style = MaterialTheme.typography.headlineMedium)
        }

        item(key = "layers") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("检测层级", style = MaterialTheme.typography.titleMedium)
                    SettingSwitch("L1 界面节点匹配（<10ms）", settings.layer1Enabled) {
                        scope.launch { app.settingsRepo.setLayer1Enabled(it) }
                    }
                    SettingSwitch("L2 本地 OCR（~100ms）", settings.layer2Enabled) {
                        scope.launch { app.settingsRepo.setLayer2Enabled(it) }
                    }
                    SettingSwitch("L3 本地视觉检测（内置检测器 + 可选下载大模型）", settings.layer3Enabled) {
                        scope.launch { app.settingsRepo.setLayer3Enabled(it) }
                    }
                }
            }
        }

        item(key = "keywords") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("关键词", style = MaterialTheme.typography.titleMedium)
                    Text(
                        settings.keywords.joinToString("、"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    var input by remember { mutableStateOf("") }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("添加关键词") },
                            keyboardActions = KeyboardActions(onDone = { }),
                        )
                        Button(onClick = {
                            val kw = input.trim()
                            if (kw.isNotEmpty()) {
                                scope.launch {
                                    app.settingsRepo.setKeywords(settings.keywords + kw)
                                }
                                input = ""
                            }
                        }) { Text("添加") }
                    }
                    if (settings.keywords.isNotEmpty()) {
                        Button(onClick = {
                            scope.launch {
                                app.settingsRepo.setKeywords(settings.keywords.toList().dropLast(1).toSet())
                            }
                        }) { Text("移除最后一个") }
                    }
                }
            }
        }

        item(key = "other") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("其他", style = MaterialTheme.typography.titleMedium)
                    SettingSwitch("调试悬浮窗（命中时显示层级与坐标）", settings.debugOverlay) {
                        scope.launch { app.settingsRepo.setDebugOverlay(it) }
                    }
                    SettingSwitch("自测模式（对本 App 生效，用于模拟广告测试）", settings.selfTest) {
                        scope.launch { app.settingsRepo.setSelfTest(it) }
                    }
                }
            }
        }

        item(key = "system-surfaces") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("系统界面保护", style = MaterialTheme.typography.titleMedium)
                    SettingSwitch(
                        "桌面 / 分享面板 / 系统弹窗：不识别、不点击",
                        settings.protectSystemSurfaces,
                    ) {
                        scope.launch { app.settingsRepo.setProtectSystemSurfaces(it) }
                    }
                    Text(
                        "始终排除：" + SystemSurfaceGuard.BUILTIN.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "另外自动排除没有桌面图标的界面（各厂商的分享面板、权限弹窗往往不属于" +
                            "任何 App，白名单管不到它们）。探测不出结果时不会误排除，只会放行。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item(key = "whitelist-header") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("白名单（不跳过的应用）", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("搜索应用名或包名") },
                    )
                    Text(
                        if (cachedApps == null) "加载应用列表…"
                        else "共 ${cachedApps?.size ?: 0} 个应用，已勾选 ${settings.whitelist.size} 个" +
                            if (query.isNotEmpty()) "，匹配 ${filtered.size} 个" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        items(filtered, key = { it.first }) { (pkg, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = pkg in settings.whitelist,
                    onCheckedChange = { checked ->
                        scope.launch {
                            app.settingsRepo.setWhitelist(
                                if (checked) settings.whitelist + pkg else settings.whitelist - pkg
                            )
                        }
                    },
                )
                Text("$label  ($pkg)", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
