package com.mibandnfc.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.mibandnfc.data.db.entity.SwitchRuleEntity
import com.mibandnfc.model.CardType
import com.mibandnfc.model.NfcCard
import com.mibandnfc.ui.common.AdConfig
import com.mibandnfc.ui.home.cardTypeIcon

private val DAY_LABELS = listOf("日", "一", "二", "三", "四", "五", "六")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val bandMac by viewModel.bandMac.collectAsState()
    val authKey by viewModel.authKey.collectAsState()
    val autoSwitchEnabled by viewModel.autoSwitchEnabled.collectAsState()
    val isSupporter by viewModel.isSupporter.collectAsState()
    val switchRules by viewModel.switchRules.collectAsState()
    val cards by viewModel.cards.collectAsState()
    val showAddRuleDialog by viewModel.showAddRuleDialog.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("設定") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // -- Band Connection Section --
            SectionHeader(title = "手環連線", icon = Icons.Filled.Bluetooth)
            Spacer(modifier = Modifier.height(12.dp))
            MacAddressField(
                value = bandMac,
                onSave = { viewModel.saveBandMac(it) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            AuthKeyField(
                value = authKey,
                onSave = { viewModel.saveAuthKey(it) },
            )

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            // -- Auto Switch Section --
            SectionHeader(title = "自動切換", icon = Icons.Filled.Schedule)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("啟用自動切換", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "根據時間規則自動切換預設卡片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoSwitchEnabled,
                    onCheckedChange = { viewModel.toggleAutoSwitch(it) },
                )
            }

            if (autoSwitchEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                switchRules.forEach { rule ->
                    RuleItem(
                        rule = rule,
                        onDelete = { viewModel.deleteRule(rule.id) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { viewModel.showAddRule() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("新增規則")
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            // -- Support Section --
            SupportSection(
                isSupporter = isSupporter,
                onToggleSupporter = { viewModel.setSupporter(it) },
                onOpenUrl = { url ->
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    )
                },
            )

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            // -- About Section --
            SectionHeader(title = "關於", icon = Icons.Filled.Info)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Mi Band NFC Manager",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "版本 1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "基於小米手環 8 NFC 協議逆向工程",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showAddRuleDialog) {
        AddRuleDialog(
            cards = cards,
            onDismiss = { viewModel.hideAddRule() },
            onConfirm = { aid, cardType, hour, minute, daysOfWeek, label ->
                viewModel.addRule(aid, cardType, hour, minute, daysOfWeek, label)
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun MacAddressField(value: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    var edited by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.uppercase().filter { c -> c in "0123456789ABCDEF:" }
            edited = text != value
        },
        label = { Text("手環 MAC 地址") },
        placeholder = { Text("XX:XX:XX:XX:XX:XX") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        leadingIcon = {
            Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            if (edited) {
                TextButton(onClick = {
                    onSave(text)
                    edited = false
                }) {
                    Text("儲存")
                }
            }
        },
        supportingText = {
            Text("格式：XX:XX:XX:XX:XX:XX")
        },
    )
}

@Composable
private fun AuthKeyField(value: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    var edited by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.lowercase().filter { c -> c in "0123456789abcdef" }
            edited = text != value
        },
        label = { Text("認證金鑰 (Auth Key)") },
        placeholder = { Text("32 位十六進位字元") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        leadingIcon = {
            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            if (edited) {
                TextButton(onClick = {
                    onSave(text)
                    edited = false
                }) {
                    Text("儲存")
                }
            }
        },
        supportingText = {
            Text("${text.length}/32 hex 字元")
        },
    )
}

@Composable
private fun RuleItem(rule: SwitchRuleEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.AccessTime,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.label.ifBlank { "${CardType.fromProto(rule.cardType).label} 規則" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row {
                    Text(
                        text = "%02d:%02d".format(rule.hour, rule.minute),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDaysOfWeek(rule.daysOfWeek),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "刪除規則",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddRuleDialog(
    cards: List<NfcCard>,
    onDismiss: () -> Unit,
    onConfirm: (String, CardType, Int, Int, Int, String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var selectedCardIndex by remember { mutableIntStateOf(0) }
    var showCardMenu by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(initialHour = 8, initialMinute = 0)
    var daysOfWeek by remember { mutableIntStateOf(0b1111110) } // Mon-Sat

    val selectedCard = cards.getOrNull(selectedCardIndex)
    val isValid = selectedCard != null

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("選擇時間") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增切換規則") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("規則名稱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例：上班切門禁") },
                )

                // Card selector
                Box {
                    OutlinedTextField(
                        value = selectedCard?.name ?: "（請選擇卡片）",
                        onValueChange = {},
                        label = { Text("目標卡片") },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { showCardMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = null)
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = showCardMenu,
                        onDismissRequest = { showCardMenu = false },
                    ) {
                        cards.forEachIndexed { index, card ->
                            DropdownMenuItem(
                                text = { Text(card.name) },
                                onClick = {
                                    selectedCardIndex = index
                                    showCardMenu = false
                                },
                                leadingIcon = {
                                    Icon(cardTypeIcon(card.type), contentDescription = null)
                                },
                            )
                        }
                        if (cards.isEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "尚無卡片",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = { showCardMenu = false },
                            )
                        }
                    }
                }

                // Time picker trigger
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("%02d:%02d".format(timePickerState.hour, timePickerState.minute))
                }

                // Days of week chips
                Text(
                    "重複日期",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DAY_LABELS.forEachIndexed { index, dayLabel ->
                        val bit = 1 shl index
                        FilterChip(
                            selected = daysOfWeek and bit != 0,
                            onClick = { daysOfWeek = daysOfWeek xor bit },
                            label = { Text(dayLabel) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedCard?.let { card ->
                        onConfirm(
                            card.aid,
                            card.type,
                            timePickerState.hour,
                            timePickerState.minute,
                            daysOfWeek,
                            label,
                        )
                    }
                },
                enabled = isValid,
            ) {
                Text("新增")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SupportSection(
    isSupporter: Boolean,
    onToggleSupporter: (Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    SectionHeader(title = "支持開發", icon = Icons.Filled.Favorite)
    Spacer(modifier = Modifier.height(12.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSupporter) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isSupporter) {
                Text(
                    "感謝您的支持！廣告已移除",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    "此 App 免費開源，由社群贊助維護",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "贊助後可移除廣告，感謝您的支持！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = { onOpenUrl(AdConfig.KOFI_URL) },
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                Icons.Filled.LocalCafe,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Ko-fi", maxLines = 1)
        }
        FilledTonalButton(
            onClick = { onOpenUrl(AdConfig.GITHUB_SPONSORS_URL) },
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                Icons.Filled.VolunteerActivism,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Sponsor", maxLines = 1)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = { onOpenUrl(AdConfig.GITHUB_REPO_URL) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("GitHub 原始碼")
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Block,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("移除廣告", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "贊助後開啟此選項",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = isSupporter,
            onCheckedChange = onToggleSupporter,
        )
    }
}

private fun formatDaysOfWeek(mask: Int): String {
    if (mask == 0b1111111) return "每天"
    if (mask == 0b0111110) return "平日"
    if (mask == 0b1000001) return "週末"
    return DAY_LABELS.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
        .joinToString("、") { "週$it" }
}
