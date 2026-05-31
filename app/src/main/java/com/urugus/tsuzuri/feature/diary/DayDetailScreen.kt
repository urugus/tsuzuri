package com.urugus.tsuzuri.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.urugus.tsuzuri.core.model.Event
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DayDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(viewModel.date.toString()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = viewModel::reconstruct, enabled = !state.busy) {
                    Text("この日を再構成")
                }
                OutlinedButton(onClick = viewModel::toggleRaw) {
                    Text(if (state.showRaw) "出来事を表示" else "Markdownを表示")
                }
            }

            state.reconstructed?.let { prose ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("再構成された日記", style = MaterialTheme.typography.labelMedium)
                        Text(
                            prose,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            if (state.showRaw) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.rawMarkdown.ifBlank { "(空)" },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                if (state.events.isEmpty()) {
                    Text("この日の出来事はまだありません。", style = MaterialTheme.typography.bodyMedium)
                } else {
                    state.events.forEach { EventRow(it) }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: Event) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val time = event.time?.format(DateTimeFormatter.ofPattern("HH:mm"))
            Text(
                text = listOfNotNull(time, event.title).joinToString("　"),
                style = MaterialTheme.typography.titleSmall,
            )
            if (event.body.isNotBlank() && event.body != event.title) {
                Text(
                    event.body,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            event.category?.let {
                Text(
                    "#$it",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
