package com.example.ticketier.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText      // ← ADDED
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ticketier.TicketierTestRule
import com.example.ticketier.showScreen
import com.example.ticketier.data.AuthResult
import com.example.ticketier.ui.theme.screens.AdminLoginScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AdminLoginScreenTest {
    @get:Rule(order = 0) val repos = TicketierTestRule()
    @get:Rule(order = 1) val rule = createComposeRule()

    private var loggedIn = 0
    private var backClicks = 0
    private val error = "Enter your admin email and password."
    private val denied = "Access denied: admins only"
    private val incorrect = "Incorrect email or password."

    private val fakeSignIn: suspend (String, String) -> AuthResult = { email, password ->
        val e = email.trim().lowercase()
        when {
            e == "admin@test.com" && password == "1234" -> AuthResult.Success
            e == "user@test.com" && password == "user123" -> AuthResult.Error(denied)
            else -> AuthResult.Error(incorrect)
        }
    }

    private fun show() = rule.showScreen {
        AdminLoginScreen(
            signIn = fakeSignIn,
            onBack = { backClicks++ },
            onLoggedIn = { loggedIn++ }
        )
    }

    @Test fun A_ADL_01_emptyFields_showError() {
        show()
        rule.onNodeWithText("Sign in as admin").performClick()
        rule.onNodeWithText(error).assertExists()
        assertEquals(0, loggedIn)
    }

    @Test fun A_ADL_02_onlyEmail_showsError() {
        show()
        rule.onNodeWithText("Admin email").performTextInput("admin@test.com")
        rule.onNodeWithText("Sign in as admin").performClick()
        rule.onNodeWithText(error).assertExists()
        assertEquals(0, loggedIn)
    }

    @Test fun A_ADL_03_adminCredentials_invokeCallback() {
        show()
        rule.onNodeWithText("Admin email").performTextInput("admin@test.com")
        rule.onNodeWithText("Password").performTextInput("1234")
        rule.onNodeWithText("Sign in as admin").performClick()
        rule.waitUntil(2_000) { loggedIn == 1 }
        assertEquals(1, loggedIn)
    }

    @Test fun A_ADL_04_passwordToggle_switchesBetweenShowAndHide() {
        show()
        rule.onNodeWithText("Show").performClick()
        rule.onNodeWithText("Hide").assertExists()
        rule.onNodeWithText("Hide").performClick()
        rule.onNodeWithText("Show").assertExists()
    }

    @Test fun A_ADL_05_backButton_invokesCallback() {
        show()
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backClicks)
    }

    @Test fun A_ADL_06_errorDisappearsWhenTyping() {
        show()
        rule.onNodeWithText("Sign in as admin").performClick()
        rule.onNodeWithText(error).assertExists()
        rule.onNodeWithText("Admin email").performTextInput("a")
        rule.onNodeWithText(error).assertDoesNotExist()
    }

    @Test fun A_ADL_07_customerCredentials_areDeniedAccess() {
        show()
        rule.onNodeWithText("Admin email").performTextInput("user@test.com")
        rule.onNodeWithText("Password").performTextInput("user123")
        rule.onNodeWithText("Sign in as admin").performClick()
        rule.waitUntil(2_000) {
            rule.onAllNodesWithText(denied).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(denied).assertExists()
        assertEquals(0, loggedIn)
    }

    @Test fun A_ADL_08_unknownCredentials_showIncorrectMessage() {
        show()
        rule.onNodeWithText("Admin email").performTextInput("admin@test.com")
        rule.onNodeWithText("Password").performTextInput("wrong")
        rule.onNodeWithText("Sign in as admin").performClick()
        rule.waitUntil(2_000) {
            rule.onAllNodesWithText(incorrect).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(incorrect).assertExists()
        assertEquals(0, loggedIn)
    }
}