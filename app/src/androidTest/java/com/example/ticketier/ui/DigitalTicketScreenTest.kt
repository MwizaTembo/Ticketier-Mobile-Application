package com.example.ticketier.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ticketier.TicketierTestRule
import com.example.ticketier.buyTickets
import com.example.ticketier.data.TicketRepository
import com.example.ticketier.showScreen
import com.example.ticketier.ui.theme.screens.DigitalTicketScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The "Download ticket (PDF)" button opens Android's system Save-as screen, which a
 * Compose test cannot drive. Saving the PDF is covered by manual test M-04 instead.
 */
class DigitalTicketScreenTest {
    @get:Rule(order = 0) val repos = TicketierTestRule()
    @get:Rule(order = 1) val rule = createComposeRule()

    private var backClicks = 0
    private val demoButton = "Demo: simulate a gate scan"
    private val scannedMessage = "This ticket has been scanned and can't be used again."

    private fun show(serial: String) = rule.showScreen {
        DigitalTicketScreen(serial = serial, onBack = { backClicks++ })
    }

    @Test fun A_DTK_01_showsTicketDetailsQrCodeAndDownloadButton() {
        val ticket = buyTickets(1).single()
        show(ticket.serial)
        rule.onNodeWithText("Test Band").assertExists()
        rule.onNodeWithText("14 NOV \u00B7 7:00 PM").assertExists()
        rule.onNodeWithText("Test Hall, Zomba").assertExists()
        rule.onNodeWithText("General Access").assertExists()
        rule.onNodeWithText(ticket.serial).assertExists()
        rule.onNodeWithText("Serial number").assertExists()
        rule.onNodeWithText("VALID").assertExists()
        rule.onNodeWithContentDescription("QR code for ticket ${ticket.serial}").assertExists()
        rule.onNodeWithText("Download ticket (PDF)").assertExists()
    }

    @Test fun A_DTK_02_simulateScan_marksTicketUsedInScreenAndRepository() {
        val ticket = buyTickets(1).single()
        show(ticket.serial)
        rule.onNodeWithText(demoButton).performScrollTo().performClick()
        // "USED" appears twice: the status chip and the stamp over the QR code
        rule.onAllNodesWithText("USED").assertCountEquals(2)
        assertTrue(TicketRepository.find(ticket.serial)!!.used)
    }

    @Test fun A_DTK_03_afterScan_demoButtonDisappearsAndMessageAppears() {
        val ticket = buyTickets(1).single()
        show(ticket.serial)
        rule.onNodeWithText(demoButton).performScrollTo().performClick()
        rule.onNodeWithText(demoButton).assertDoesNotExist()
        rule.onNodeWithText("VALID").assertDoesNotExist()
        rule.onNodeWithText(scannedMessage).assertExists()
    }

    @Test fun A_DTK_04_alreadyUsedTicket_opensInUsedState() {
        val ticket = buyTickets(1).single()
        TicketRepository.markUsed(ticket.serial)
        show(ticket.serial)
        rule.onAllNodesWithText("USED").assertCountEquals(2)
        rule.onNodeWithText(scannedMessage).assertExists()
        rule.onNodeWithText(demoButton).assertDoesNotExist()
    }

    @Test fun A_DTK_05_unknownSerial_showsNotFoundAndGoBack() {
        show("TKT-999-NOTREAL1")
        rule.onNodeWithText("We couldn't find this ticket.").assertExists()
        rule.onNodeWithText("Go back").performClick()
        assertEquals(1, backClicks)
    }

    @Test fun A_DTK_06_backButton_invokesCallback() {
        val ticket = buyTickets(1).single()
        show(ticket.serial)
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backClicks)
    }
}