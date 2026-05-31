package com.urugus.tsuzuri.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // ACTION_OPEN_DOCUMENT_TREE でフォルダを選択（Obsidian流のvault指定）。
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::onFolderSelected) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Vault フォルダ", style = MaterialTheme.typography.headlineSmall)
        Text(
            "日記を保存するフォルダ（Markdown）を選びます。GitHub/クラウド同期フォルダを指定すれば外部同期も可能です。",
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("選択中のフォルダ", style = MaterialTheme.typography.labelMedium)
                Text(
                    state.folderName ?: "未選択",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else if (state.folderName != null) {
                    Text(
                        "日記ファイル: ${state.dayCount} 件",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.recentDates.isNotEmpty()) {
                        Text(
                            "最近: " + state.recentDates.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Button(onClick = { picker.launch(null) }) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null)
            Text(
                text = if (state.folderName == null) "フォルダを選択" else "フォルダを変更",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
