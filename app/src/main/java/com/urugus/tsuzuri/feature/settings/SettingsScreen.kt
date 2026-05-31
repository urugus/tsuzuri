package com.urugus.tsuzuri.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.urugus.tsuzuri.core.llm.LlmProviderMode

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::onFolderSelected) }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::onModelSelected) }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeNotice()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // --- Vault フォルダ ---
            Text("Vault フォルダ", style = MaterialTheme.typography.headlineSmall)
            Text(
                "日記を保存するフォルダ（Markdown）を選びます。GitHub/クラウド同期フォルダを指定すれば外部同期も可能です。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("選択中のフォルダ", style = MaterialTheme.typography.labelMedium)
                    Text(state.folderName ?: "未選択", style = MaterialTheme.typography.bodyLarge)
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else if (state.folderName != null) {
                        Text("日記ファイル: ${state.dayCount} 件", style = MaterialTheme.typography.bodyMedium)
                        if (state.recentDates.isNotEmpty()) {
                            Text("最近: " + state.recentDates.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(onClick = { folderPicker.launch(null) }) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Text(
                    text = if (state.folderName == null) "フォルダを選択" else "フォルダを変更",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // --- AIプロバイダ ---
            Text("AIプロバイダ", style = MaterialTheme.typography.headlineSmall)
            Text(
                "簡易モードは定型応答、端末内AIは読み込んだ .task モデルを使います。クラウドAIは次の実装ステップで追加します。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (state.modelAvailable) "モデル: 読込済み" else "モデル: 未読込",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProviderModeButton(
                            text = "簡易",
                            selected = state.providerMode == LlmProviderMode.STUB,
                            onClick = { viewModel.onProviderModeSelected(LlmProviderMode.STUB) },
                            modifier = Modifier.weight(1f),
                        )
                        ProviderModeButton(
                            text = "端末内",
                            selected = state.providerMode == LlmProviderMode.ON_DEVICE,
                            onClick = { viewModel.onProviderModeSelected(LlmProviderMode.ON_DEVICE) },
                            modifier = Modifier.weight(1f),
                        )
                        ProviderModeButton(
                            text = "クラウド",
                            selected = state.providerMode == LlmProviderMode.CLOUD,
                            onClick = { viewModel.onProviderModeSelected(LlmProviderMode.CLOUD) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (state.importingModel) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }
            OutlinedButton(
                onClick = { modelPicker.launch(arrayOf("*/*")) },
                enabled = !state.importingModel,
            ) {
                Text("モデルファイル(.task)を読み込む")
            }
        }
    }
}

@Composable
private fun ProviderModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    }
}
