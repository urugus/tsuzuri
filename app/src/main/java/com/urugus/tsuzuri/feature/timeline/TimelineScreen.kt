package com.urugus.tsuzuri.feature.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate

@Composable
fun TimelineScreen(
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // 他タブで保存後に戻ったとき最新化。
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("ふり返り", style = MaterialTheme.typography.headlineSmall)

        when {
            !state.vaultConfigured ->
                CenteredHint("「設定」タブでVaultフォルダを選ぶと、日記の一覧がここに表示されます。")
            state.days.isEmpty() && !state.loading ->
                CenteredHint("まだ記録がありません。「会話」タブで今日の出来事を記録しましょう。")
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.days) { day ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenDay(day) },
                        ) {
                            Text(
                                text = day.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}
