package com.example.ticketier.flow

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.ticketier.TicketierTestRule
import com.example.ticketier.data.TicketRepository
import com.example.ticketier.fillPostEventForm
import com.example.ticketier.logInAsDemoCustomer
import com.example.ticketier.nav.AppNavigation
import com.example.ticketier.showScreen
import com.example.ticketier.tapPostEvent
import com.example.ticketier.waitForText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end tests: they start the REAL navigation (splash -> login -> ...) and click
 * through it like a user. They are the slowest tests and the most valuable: they check
 * that the screens work TOGETHER.
 *
 * Tip: turn off the emulator animations (Developer options) to make these faster and
 * more reliable.
 */
class AppFlowTest {
    @get:Rule(order = 0) val repos = TicketierTestRule()
    @get:Rule(order = 1) val rule = createComposeRule()

    private fun startApp() {
        rule.showScreen { AppNavigation() }
        rule.waitForText("Login")           // the splash screen lasts about 2 seconds
    }

    private fun startAppAndLogIn() {
        startApp()
        rule.logInAsDemoCustomer()
        rule.waitForText("Upcoming Events")
    }

    /** From Home: opens the first event and buys `quantity` General Access tickets. */
    private fun buyFromFirstEvent(quantity: Int) {
        rule.onAllNodesWithText("Get Tickets")[0].performClick()
        rule.waitForText("About the event")
        repeat(quantity - 1) { rule.onNodeWithText("+").performScrollTo().performClick() }
        rule.onNodeWithText("Confirm Purchase").performClick()
        rule.onNodeWithText("Confirm").performClick()
        rule.waitForText("My tickets")
    }

    @Test fun A_E2E_01_buyingTickets_createsUniqueTicketsListedInMyTickets() {
        startAppAndLogIn()
        buyFromFirstEvent(quantity = 2)

        rule.onAllNodesWithText("VALID").assertCountEquals(2)
        rule.onAllNodesWithText("The Neighbourhood").assertCountEquals(2)
        assertEquals(2, TicketRepository.tickets.size)
        assertEquals(2, TicketRepository.tickets.map { it.serial }.toSet().size)
    }

    @Test fun A_E2E_02_scanningOneTicket_leavesTheOtherValid() {
        startAppAndLogIn()
        buyFromFirstEvent(quantity = 2)

        val serial = TicketRepository.tickets.first().serial
        rule.onNodeWithText(serial).performClick()
        rule.waitForText("Your ticket")
        rule.onNodeWithText("Demo: simulate a gate scan").performScrollTo().performClick()
        rule.onAllNodesWithText("USED").assertCountEquals(2)

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForText("My tickets")
        rule.onAllNodesWithText("USED").assertCountEquals(1)
        rule.onAllNodesWithText("VALID").assertCountEquals(1)
    }

    @Test fun A_E2E_03_backFromMyTicketsAfterPurchase_returnsHomeNotThePurchasePage() {
        startAppAndLogIn()
        buyFromFirstEvent(quantity = 1)
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForText("Upcoming Events")
        rule.onNodeWithText("About the event").assertDoesNotExist()
    }

    @Test fun A_E2E_04_adminPostsEvent_userFindsItOnHome() {
        startApp()
        rule.onNodeWithText("Log in as admin").performClick()
        rule.waitForText("Admin sign in")
        rule.onNodeWithText("Admin email").performTextInput("admin@test.com")
        rule.onNodeWithText("Password").performTextInput("1234")
        rule.onNodeWithText("Sign in as admin").performClick()
        rule.waitForText("Admin dashboard")

        rule.onNodeWithText("Post event").performClick()      // the floating button
        rule.waitForText("Post new event")
        rule.fillPostEventForm(artist = "E2E Band")
        rule.tapPostEvent()
        rule.waitForText("Admin dashboard")
        rule.onNodeWithText("E2E Band").assertExists()

        rule.onNodeWithText("Log out").performClick()
        rule.waitForText("Login")
        rule.logInAsDemoCustomer()
        rule.waitForText("Upcoming Events")
        rule.onNode(hasSetTextAction()).performTextInput("E2E")
        rule.onNodeWithText("E2E Band").assertExists()
    }

    @Test fun A_E2E_05_adminLogout_returnsToLoginScreen() {
        startApp()
        rule.onNodeWithText("Log in as admin").performClick()
        rule.waitForText("Admin sign in")
        rule.onNodeWithText("Admin email").performTextInput("admin@test.com")
        rule.onNodeWithText("Password").performTextInput("1234")
        rule.onNodeWithText("Sign in as admin").performClick()
        rule.waitForText("Admin dashboard")
        rule.onNodeWithText("Log out").performClick()
        rule.waitForText("Login")
        rule.onNodeWithText("Log in as admin").assertExists()
    }

    @Test fun A_E2E_06_backFromEventDetails_returnsHome() {
        startAppAndLogIn()
        rule.onAllNodesWithText("Get Tickets")[0].performClick()
        rule.waitForText("About the event")
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForText("Upcoming Events")
        assertTrue(TicketRepository.tickets.isEmpty())   // nothing was bought
    }

    @Test fun A_E2E_07_wrongPassword_staysOnLoginScreen() {
        startApp()
        rule.onNodeWithText("Email").performTextInput("user@test.com")
        rule.onNodeWithText("Password").performTextInput("wrong")
        rule.onNodeWithText("Login").performClick()
        rule.onNodeWithText("Incorrect email or password.").assertExists()
        rule.onNodeWithText("Upcoming Events").assertDoesNotExist()
    }
}