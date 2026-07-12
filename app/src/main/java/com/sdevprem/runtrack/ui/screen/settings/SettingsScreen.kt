package com.sdevprem.runtrack.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sdevprem.runtrack.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uriHandler = LocalUriHandler.current
    val newsSettings by viewModel.newsSettings.collectAsStateWithLifecycle()
    var retryInput by remember(newsSettings.noContentRetryMinutes) {
        mutableStateOf(newsSettings.noContentRetryMinutes.toString())
    }
    LaunchedEffect(newsSettings.noContentRetryMinutes) {
        retryInput = newsSettings.noContentRetryMinutes.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Settings")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "App Information",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    SettingsItem(title = "App Version", value = BuildConfig.VERSION_NAME)
                    HorizontalDivider()
                    SettingsItem(title = "Build Number", value = BuildConfig.VERSION_CODE.toString())
                    HorizontalDivider()
                    SettingsItem(title = "Package Name", value = BuildConfig.APPLICATION_ID)
                }
            }

            Text(
                text = "About",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    ClickableText(
                        text = AnnotatedString("AI跑伴 (AI Running Mate)"),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = TextDecoration.Underline
                        ),
                        onClick = {
                            uriHandler.openUri("https://github.com/run-track")
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "An intelligent running companion that combines GPS-based fitness tracking with AI-powered voice interaction.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "AI Configuration",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Coze Platform Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Configure your Coze platform credentials in the app resources to enable AI companion features.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "跑前新闻播报设置",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NewsSettingSwitchItem(
                        title = "启用节目系统",
                        checked = newsSettings.enabled,
                        onCheckedChange = viewModel::setProgramEnabled
                    )
                    NewsSettingSwitchItem(
                        title = "允许语音开启新闻",
                        checked = newsSettings.allowVoiceStart,
                        onCheckedChange = viewModel::setAllowVoiceStart
                    )
                    NewsSettingSwitchItem(
                        title = "App 打开后自动启动",
                        checked = newsSettings.autoStartOnAppOpen,
                        onCheckedChange = viewModel::setAutoStartOnAppOpen
                    )
                    NewsSettingSwitchItem(
                        title = "已授权全文播报",
                        checked = newsSettings.fullTextAuthorized,
                        onCheckedChange = viewModel::setFulltextAuthorized
                    )
                    OutlinedTextField(
                        value = newsSettings.defaultKeyword,
                        onValueChange = viewModel::setDefaultKeyword,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("默认关键词") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newsSettings.defaultLanguage,
                        onValueChange = viewModel::setDefaultLanguage,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("默认语言（zh/en）") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = retryInput,
                        onValueChange = { raw ->
                            val filtered = raw.filter { it.isDigit() }
                            retryInput = filtered
                            filtered.toIntOrNull()?.let { value ->
                                if (value > 0) {
                                    viewModel.setNoContentRetryMinutes(value)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("无内容重试间隔（分钟）") },
                        singleLine = true
                    )
                }
            }

            Text(
                text = "新闻 Provider 配置",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.applyNewsApiPreset() }
                        ) {
                            Text("应用 NewsAPI 预设")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.applyLocalMockPreset() }
                        ) {
                            Text("应用内置测试文章")
                        }
                    }
                    OutlinedTextField(
                        value = newsSettings.feedUrlTemplate,
                        onValueChange = viewModel::setFeedUrlTemplate,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Feed Endpoint") }
                    )
                    OutlinedTextField(
                        value = newsSettings.contentUrlTemplate,
                        onValueChange = viewModel::setContentUrlTemplate,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Content Endpoint（可空）") }
                    )
                    OutlinedTextField(
                        value = newsSettings.apiKeyQueryName,
                        onValueChange = viewModel::setApiKeyQueryName,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key Query 名（如 apiKey）") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newsSettings.apiKeyHeaderName,
                        onValueChange = viewModel::setApiKeyHeaderName,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key Header 名（可空）") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newsSettings.apiKeyValue,
                        onValueChange = viewModel::setApiKeyValue,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key") },
                        singleLine = true
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsItem(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun NewsSettingSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
