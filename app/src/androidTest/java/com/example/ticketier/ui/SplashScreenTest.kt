package com.example.ticketier.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ticketier.TicketierTestRule
import com.example.ticketier.showScreen
import com.example.ticketier.ui.theme.screens.SplashScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SplashScreenTest {
    @get:Rule(order = 0) val repos = TicketierTestRule()
    @get:Rule(order = 1) val rule = createComposeRule()

    @Test fun A_SPL_01_showsWordmark() {
        rule.showScreen { SplashScreen(onFinished = {}) }
        rule.onNodeWithText("Ticketier").assertExists()
    }

    @Test fun A_SPL_02_callsOnFinishedAfterTheAnimation() {
        var finished = false
        rule.showScreen { SplashScreen(onFinished = { finished = true }) }
        rule.waitUntil(timeoutMillis = 6_000) { finished }
        assertTrue(finished)
    }
}