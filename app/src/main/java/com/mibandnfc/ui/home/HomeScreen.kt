package com.mibandnfc.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mibandnfc.data.prefs.AppPrefs
import com.mibandnfc.model.BandState
import com.mibandnfc.model.CardType
import com.mibandnfc.model.NfcCard
import com.mibandnfc.ui.common.AdBanner
import com.mibandnfc.ui.common.rememberBlePermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val bandState by viewModel.bandState.collectAsState()
    val cards by viewModel.cards.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val blePermission = rememberBlePermissionState(
        onGranted = { viewModel.connect() },
        onDenied = {
            scope.launch { snackbarHostState.showSnackbar("需要藍牙權限才能連接手環") }
        },
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text("Mi Band NFC") },
                actions = {
                    if (bandState is BandState.Connected) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "重新整理")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                ConnectionStatusCard(
                    state = bandState,
                    onConnect = { blePermission.launchPermissionRequest() },
                    onDisconnect = { viewModel.disconnect() },
                )

                Spacer(modifier = Modifier.height(24.dp))

                val defaultCard = cards.find { it.isDefault }
                AnimatedVisibility(
                    visible = defaultCard != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    defaultCard?.let {
                        DefaultCardSection(card = it)
                    }
                }

                if (cards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "快速切換",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    QuickSwitchGrid(
                        cards = cards,
                        onCardTap = { viewModel.setDefault(it) },
                    )
                }

                if (cards.isEmpty() && bandState is BandState.Connected) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = "尚未發現卡片\n請在「卡片」頁面新增",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                AdBanner(prefs = viewModel.appPrefs)
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    state: BandState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = when (state) {
            is BandState.Connected -> MaterialTheme.colorScheme.primaryContainer
            is BandState.Error -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "statusColor",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (state) {
                    is BandState.Connected -> Icons.Filled.BluetoothConnected
                    else -> Icons.Filled.BluetoothDisabled
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = when (state) {
                    is BandState.Connected -> MaterialTheme.colorScheme.primary
                    is BandState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                when (state) {
                    is BandState.Disconnected -> {
                        Text("未連接", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "點擊連接手環",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is BandState.Scanning -> {
                        Text("搜尋中…", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "正在掃描手環",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is BandState.Connecting -> {
                        Text("連接中…", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "正在建立 BLE 連線",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is BandState.Authenticating -> {
                        Text("驗證中…", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "正在進行金鑰認證",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is BandState.Connected -> {
                        Text(state.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.mac,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "韌體 ${state.firmware}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is BandState.Error -> {
                        Text("連線錯誤", style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            when (state) {
                is BandState.Disconnected, is BandState.Error -> {
                    FilledTonalButton(onClick = onConnect) {
                        Text("連接")
                    }
                }
                is BandState.Scanning, is BandState.Connecting, is BandState.Authenticating -> {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                }
                is BandState.Connected -> {
                    OutlinedButton(onClick = onDisconnect) {
                        Text("中斷")
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultCardSection(card: NfcCard) {
    Text(
        text = "目前預設卡片",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = cardTypeIcon(card.type),
                contentDescription = card.type.label,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = card.aid,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = card.type.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                Icons.Filled.Star,
                contentDescription = "預設",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun QuickSwitchGrid(
    cards: List<NfcCard>,
    onCardTap: (NfcCard) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(cards, key = { it.aid }) { card ->
            QuickSwitchCard(card = card, onClick = { onCardTap(card) })
        }
    }
}

@Composable
private fun QuickSwitchCard(card: NfcCard, onClick: () -> Unit) {
    val borderColor by animateColorAsState(
        targetValue = if (card.isDefault) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        label = "cardBorder",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = BorderStroke(if (card.isDefault) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (card.isDefault) 4.dp else 1.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = cardTypeIcon(card.type),
                contentDescription = card.type.label,
                modifier = Modifier.size(32.dp),
                tint = if (card.isDefault) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = card.aid.take(12) + if (card.aid.length > 12) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (card.isDefault) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "預設",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

fun cardTypeIcon(type: CardType): ImageVector = when (type) {
    CardType.DOOR -> Icons.Filled.DoorFront
    CardType.BUS -> Icons.Filled.DirectionsBus
    CardType.UNIONPAY -> Icons.Filled.Payment
    CardType.MASTERCARD -> Icons.Filled.CreditCard
    CardType.VISA -> Icons.Filled.CreditCard
    CardType.UNKNOWN -> Icons.Filled.Nfc
}
