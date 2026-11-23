package com.example.ticketier.ui

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText      // ← ADDED
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ticketier.TicketierTestRule
import com.example.ticketier.showScreen
import com.example.ticketier.data.AuthResult
import com.example.ticketier.ui.theme.screens.LoginScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule(order = 0) val repos = TicketierTestRule()
    @get:Rule(order = 1) val rule = createComposeRule()

    private var loginClicks = 0
    private var adminClicks = 0
    private val enterBoth = "Enter your email and password."
    private val incorrect = "Incorrect email or password."

    /** Fake auth matching the demo accounts. Admin uses a different code path, so deny here. */
    private val fakeSignIn: suspend (String, String) -> AuthResult = { email, password ->
        if (email.trim().equals("user@test.com", ignoreCase = true) && password == "user123") {
            AuthResult.Success
        } else {
            AuthResult.Error(incorrect)
        }
    }

    private fun show() = rule.showScreen {
        LoginScreen(
            signIn = fakeSignIn,
            onLoginClick = { loginClicks++ },
            onAdminLoginClick = { adminClicks++ }
        )
    }

    private fun signIn(email: String, password: String) {
        if (email.isNotEmpty()) rule.onNodeWithText("Email").performTextInput(email)
        if (password.isNotEmpty()) rule.onNodeWithText("Password").performTextInput(password)
        rule.onNodeWithText("Login").performClick()
    }

    // ... rest of the test methods are UNCHANGED — copy them from your current file ...
    @Test fun A_LGN_01_showsBrandingFieldsAndButtons() {
        show()
        rule.onNodeWithText("Ticketier").assertExists()
        rule.onNodeWithText("Right Where You'd Wanna Be!").assertExists()
        rule.onNodeWithText("Email").assertExists()
        rule.onNodeWithText("Password").assertExists()
        rule.onNodeWithText("Login").assertExists()
        rule.onNodeWithText("Log in as admin").assertExists()
    }

    @Test fun A_LGN_02_loginWithCorrectCredentials_invokesCallback() {
        show()
        signIn("user@test.com", "user123")
        // The button launches a coroutine; wait for the result to settle
        rule.waitUntil(2_000) { loginClicks == 1 }
        assertEquals(1, loginClicks)
        assertEquals(0, adminClicks)
    }

    @Test fun A_LGN_03_adminButton_invokesAdminCallbackWithoutCustomerCredentials() {
        show()
        rule.onNodeWithText("Log in as admin").performClick()
        assertEquals(1, adminClicks)
        assertEquals(0, loginClicks)
    }

    @Test fun A_LGN_04_emailField_acceptsTyping() {
        show()
        rule.onNodeWithText("Email").performTextInput("user@example.com")
        rule.onNodeWithText("Email").assertTextContains("user@example.com")
    }

    @Test fun A_LGN_05_passwordField_masksTypedText() {
        show()
        rule.onNodeWithText("Password").performTextInput("secret123")
        rule.onNodeWithText("secret123").assertDoesNotExist()
    }

    @Test fun A_LGN_06_wrongPassword_showsErrorAndDoesNotLogIn() {
        show()
        signIn("user@test.com", "wrong")
        rule.waitUntil(2_000) {
            rule.onAllNodesWithText(incorrect).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(incorrect).assertExists()
        assertEquals(0, loginClicks)
    }

    @Test fun A_LGN_07_wrongEmail_showsErrorAndDoesNotLogIn() {
        show()
        signIn("someone@test.com", "user123")
        rule.waitUntil(2_000) {
            rule.onAllNodesWithText(incorrect).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(incorrect).assertExists()
        assertEquals(0, loginClicks)
    }

    @Test fun A_LGN_08_emptyFields_showEnterBothMessage() {
        show()
        signIn("", "")
        rule.onNodeWithText(enterBoth).assertExists()
        assertEquals(0, loginClicks)
    }

    @Test fun A_LGN_09_onlyEmail_showsEnterBothMessage() {
        show()
        signIn("user@test.com", "")
        rule.onNodeWithText(enterBoth).assertExists()
        assertEquals(0, loginClicks)
    }

    @Test fun A_LGN_10_errorDisappearsWhenTyping() {
        show()
        signIn("user@test.com", "wrong")
        rule.waitUntil(2_000) {
            rule.onAllNodesWithText(incorrect).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Password").performTextInput("x")
        rule.onNodeWithText(incorrect).assertDoesNotExist()
    }

    @Test fun A_LGN_11_email_ignoresCaseAndSurroundingSpaces() {
        show()
        signIn("  USER@Test.com ", "user123")
        rule.waitUntil(2_000) { loginClicks == 1 }
        assertEquals(1, loginClicks)
    }

    @Test fun A_LGN_12_password_isCaseSensitive() {
        show()
        signIn("user@test.com", "USER123")
        rule.waitUntil(2_000) {
            rule.onAllNodesWithText(incorrect).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(incorrect).assertExists()
        assertEquals(0, loginClicks)
    }

    @Test fun A_LGN_13_showButton_revealsThePassword() {
        show()
        rule.onNodeWithText("Password").performTextInput("user123")
        rule.onNodeWithText("user123").assertDoesNotExist()
        rule.onNodeWithText("Show").performClick()
        rule.onNodeWithText("user123").assertExists()
        rule.onNodeWithText("Hide").assertExists()
    }

    @Test fun A_LGN_14_hideButton_masksThePasswordAgain() {
        show()
        rule.onNodeWithText("Password").performTextInput("user123")
        rule.onNodeWithText("Show").performClick()
        rule.onNodeWithText("Hide").performClick()
        rule.onNodeWithText("user123").assertDoesNotExist()
        rule.onNodeWithText("Show").assertExists()
    }
}