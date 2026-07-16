package com.sdevprem.runtrack.ai.news

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sdevprem.runtrack.ai.news.model.NewsPlaybackState
import com.sdevprem.runtrack.ai.news.model.NewsPlaybackStatus
import com.sdevprem.runtrack.ui.screen.currentrun.component.NewsNowPlayingCard
import com.sdevprem.runtrack.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test

class NewsNowPlayingCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun configurationErrorOffersSettingsAction() {
        composeRule.setContent {
            AppTheme {
                NewsNowPlayingCard(
                    state = NewsPlaybackState(
                        status = NewsPlaybackStatus.CONFIGURATION_ERROR,
                        message = "新闻服务未配置"
                    ),
                    onPrimaryActionClick = {},
                    onOpenSettingsClick = {},
                    onSkipClick = {},
                    onStopClick = {}
                )
            }
        }

        composeRule.onNodeWithText("需要配置").assertIsDisplayed()
        composeRule.onNodeWithText("新闻服务未配置").assertIsDisplayed()
        composeRule.onNodeWithText("检查配置").assertIsDisplayed()
    }

    @Test
    fun companionInterruptionIsVisible() {
        composeRule.setContent {
            AppTheme {
                NewsNowPlayingCard(
                    state = NewsPlaybackState(
                        status = NewsPlaybackStatus.INTERRUPTED,
                        message = "陪跑插播中，结束后继续当前句"
                    ),
                    onPrimaryActionClick = {},
                    onOpenSettingsClick = {},
                    onSkipClick = {},
                    onStopClick = {}
                )
            }
        }

        composeRule.onNodeWithText("陪跑插播中").assertIsDisplayed()
        composeRule.onNodeWithText("继续").assertIsDisplayed()
    }
}
