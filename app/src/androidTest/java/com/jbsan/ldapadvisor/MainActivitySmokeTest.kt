package com.jbsan.ldapadvisor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesAndShowsAppOrDashboardText() {
        // App name appears in top bar; dashboard empty/title text when profiles absent or present.
        val appName = composeRule.activity.getString(R.string.app_name)
        val dashboard = composeRule.activity.getString(R.string.dashboard_title)
        val emptyTitle = composeRule.activity.getString(R.string.dashboard_empty_title)
        val matched = runCatching {
            composeRule.onNodeWithText(appName, substring = true).assertIsDisplayed()
        }.isSuccess || runCatching {
            composeRule.onNodeWithText(dashboard, substring = true).assertIsDisplayed()
        }.isSuccess || runCatching {
            composeRule.onNodeWithText(emptyTitle, substring = true).assertIsDisplayed()
        }.isSuccess
        assert(matched) { "Expected app name or dashboard text on launch" }
    }
}
