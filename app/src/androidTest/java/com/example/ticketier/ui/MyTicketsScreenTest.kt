package com.example.ticketier.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ticketier.TicketierTestRule
import com.example.ticketier.buyTickets
import com.example.ticketier.data.TicketRepository
import com.example.ticketier.showScreen
import com.example.ticketier.ui.theme.screens.MyTicketsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MyTicketsScreenTest {
    @get:Rule(order = 0) val repos = TicketierTestRule()
    @get:Rule(order = 1) val rule = createComposeRule()

    private var backClicks = 0
    private var browseClicks = 0
    private var openedSerial: String? = null

    // Tickets are created BEFORE this is called, so the screen starts with the right data.
    private fun show() = rule.showScreen {
        MyTicketsScreen(
            onBack = { backClicks++ },
            onTicketClick = { openedSerial = it },
            onBrowseEvents = { browseClicks++ }
        )
    }

    @Test fun A_MYT_01_noTickets_showsEmptyState() {
        show()
        rule.onNodeWithText("No tickets yet").assertExists()
        rule.onNodeWithText("Buy a ticket and it will show up here with its QR code.").assertExists()
        rule.onNodeWithText("Browse events").assertExists()
    }

    @Test fun A_MYT_02_browseEventsButton_invokesCallback() {
        show()
        rule.onNodeWithText("Browse events").performClick()
        assertEquals(1, browseClicks)
    }

    @Test fun A_MYT_03_listsEveryPurchasedTicket() {
        val tickets = buyTickets(count = 3)
        show()
        rule.onNodeWithText("My tickets").assertExists()
        tickets.forEach { rule.onNodeWithText(it.serial).assertExists() }
    }

    @Test fun A_MYT_04_newTickets_showValidStatus() {
        buyTickets(count = 2)
        show()
        rule.onAllNodesWithText("VALID").assertCountEquals(2)
    }

    @Test fun A_MYT_05_usedTicket_showsUsedStatus() {
        val tickets = buyTickets(count = 2)
        TicketRepository.markUsed(tickets[0].serial)
        show()
        rule.onAllNodesWithText("USED").assertCountEquals(1)
        rule.onAllNodesWithText("VALID").assertCountEquals(1)
    }

    @Test fun A_MYT_06_clickingTicket_passesItsSerialToCallback() {
        val ticket = buyTickets(count = 1).single()
        show()
        rule.onNodeWithText(ticket.serial).performClick()
        assertEquals(ticket.serial, openedSerial)
    }

    @Test fun A_MYT_07_backButton_invokesCallback() {
        show()
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backClicks)
    }
}