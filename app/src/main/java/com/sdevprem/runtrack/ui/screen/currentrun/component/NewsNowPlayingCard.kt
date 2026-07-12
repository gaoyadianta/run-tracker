package com.sdevprem.runtrack.ui.screen.currentrun.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sdevprem.runtrack.ai.news.NewsTextUtils
import com.sdevprem.runtrack.ai.news.model.NewsPlaybackState
import com.sdevprem.runtrack.ai.news.model.NewsPlaybackStatus

@Composable
fun NewsNowPlayingCard(
    state: NewsPlaybackState,
    onPrimaryActionClick: () -> Unit,
    onSkipClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val statusLabel = state.status.toStatusLabel()
    val statusColor = state.status.toStatusColor()
    val progressLabel = if (state.totalSentences > 0) {
        "第${state.currentSentenceIndex.coerceAtMost(state.totalSentences)}/${state.totalSentences}句"
    } else {
        "等待播报"
    }
    val primaryActionText = when (state.status) {
        NewsPlaybackStatus.RUNNING,
        NewsPlaybackStatus.FETCHING -> "暂停"
        NewsPlaybackStatus.PAUSED,
        NewsPlaybackStatus.INTERRUPTED -> "继续"
        NewsPlaybackStatus.IDLE,
        NewsPlaybackStatus.NO_CONTENT,
        NewsPlaybackStatus.STOPPED,
        NewsPlaybackStatus.ERROR -> "开始"
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(174.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "新闻播报",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.currentTitle ?: "尚未开始播报，支持语音说“开始播报xx新闻”",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildMeta(state),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = state.message ?: progressLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onPrimaryActionClick
                ) {
                    Text(primaryActionText)
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onSkipClick,
                    enabled = state.status != NewsPlaybackStatus.IDLE && state.status != NewsPlaybackStatus.STOPPED,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("下一条")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onStopClick,
                    enabled = state.status != NewsPlaybackStatus.IDLE && state.status != NewsPlaybackStatus.STOPPED,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("停止")
                }
            }
        }
    }
}

private fun NewsPlaybackStatus.toStatusLabel(): String = when (this) {
    NewsPlaybackStatus.IDLE -> "未开始"
    NewsPlaybackStatus.FETCHING -> "获取内容中"
    NewsPlaybackStatus.RUNNING -> "播报中"
    NewsPlaybackStatus.PAUSED -> "已暂停"
    NewsPlaybackStatus.INTERRUPTED -> "陪跑插播中"
    NewsPlaybackStatus.NO_CONTENT -> "无可播报内容"
    NewsPlaybackStatus.STOPPED -> "已停止"
    NewsPlaybackStatus.ERROR -> "错误"
}

private fun NewsPlaybackStatus.toStatusColor(): Color = when (this) {
    NewsPlaybackStatus.RUNNING -> Color(0xFF2E7D32)
    NewsPlaybackStatus.FETCHING -> Color(0xFF1976D2)
    NewsPlaybackStatus.PAUSED -> Color(0xFFE65100)
    NewsPlaybackStatus.INTERRUPTED -> Color(0xFF6A1B9A)
    NewsPlaybackStatus.ERROR -> Color(0xFFC62828)
    else -> Color(0xFF546E7A)
}

private fun buildMeta(state: NewsPlaybackState): String {
    val source = state.currentSource ?: "未知来源"
    val publishedAt = NewsTextUtils.formatPublishedTime(state.currentPublishedAtEpochMs)
    return "$source · $publishedAt"
}
