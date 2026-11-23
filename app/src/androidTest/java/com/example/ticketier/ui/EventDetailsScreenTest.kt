package com.example.ticketier.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ticketier.TicketierTestRule
import com.example.ticketier.data.Event
import com.example.ticketier.data.TicketTier
import com.example.ticketier.showScreen
import com.example.ticketier.testEvent
import com.example.ticketier.ui.theme.screens.EventDetailsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * Uses testEvent(): tiers Early Bird 10,000 / General Access 20,000 / VIP 35,000.
 * "\u00D7" is the multiplication sign used in the summary text ("2 × General Access"),
 * "\u2212" is the minus sign on the quantity button.
 */
class EventDetailsScreenTest {
    @get:Rule(order = 0) val repos = TicketierTestRule()
    @get:Rule(order = 1) val rule = createComposeRule()

    private var backClicks = 0
    private var purchasedTier: TicketTier? = null
    private var purchasedQuantity = 0

    private fun show(event: Event = testEvent()) = rule.showScreen {
        EventDetailsScreen(
            event = event,
            onBackClick = { backClicks++ },
            onPurchaseConfirmed = { tier, quantity ->
                purchasedTier = tier
                purchasedQuantity = quantity
            }
        )
    }

    private fun plus(times: Int = 1) {
        repeat(times) { rule.onNodeWithText("+").performScrollTo().performClick() }
    }

    @Test fun A_EVD_01_showsEventInformation() {
        show()
        rule.onNodeWithText("Test Band").assertExists()
        rule.onNodeWithText("14 NOV").assertExists()
        rule.onNodeWithText("7:00 PM").assertExists()
        rule.onNodeWithText("Test Hall").assertExists()
        rule.onNodeWithText("Zomba").assertExists()
        rule.onNodeWithText("About the event").assertExists()
        rule.onNodeWithText("A test event.").assertExists()
    }

    @Test fun A_EVD_02_showsAllThreeTicketTiersWithPrices() {
        show()
        listOf("Early Bird", "General Access", "VIP").forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithText("MWK 10,000").assertExists()
        rule.onNodeWithText("MWK 35,000").assertExists()
        // General Access is selected by default, so 20,000 shows twice: tier card + bottom total
        rule.onAllNodesWithText("MWK 20,000").assertCountEquals(2)
    }

    @Test fun A_EVD_03_defaultsToGeneralAccessQuantityOne() {
        show()
        rule.onNodeWithText("1 \u00D7 General Access").assertExists()
    }

    @Test fun A_EVD_04_selectingVip_updatesSummary() {
        show()
        rule.onNodeWithText("VIP").performScrollTo().performClick()
        rule.onNodeWithText("1 \u00D7 VIP").assertExists()
        rule.onNodeWithText("1 \u00D7 General Access").assertDoesNotExist()
    }

    @Test fun A_EVD_05_increasingQuantity_updatesTotal() {
        show()
        plus(2)
        rule.onNodeWithText("3 \u00D7 General Access").assertExists()
        rule.onNodeWithText("MWK 60,000").assertExists()      // 3 x 20,000
    }

    @Test fun A_EVD_06_total_usesTheSelectedTier() {
        show()
        rule.onNodeWithText("VIP").performScrollTo().performClick()
        plus(1)
        rule.onNodeWithText("2 \u00D7 VIP").assertExists()
        rule.onNodeWithText("MWK 70,000").assertExists()      // 2 x 35,000
    }

    @Test fun A_EVD_07_minusButton_isDisabledAtOne() {
        show()
        rule.onNodeWithText("\u2212").performScrollTo().assertIsNotEnabled()
        plus(1)
        rule.onNodeWithText("\u2212").assertIsEnabled()
    }

    @Test fun A_EVD_08_plusButton_isDisabledAtTen() {
        show()
        plus(9)
        rule.onNodeWithText("10 \u00D7 General Access").assertExists()
        rule.onNodeWithText("MWK 200,000").assertExists()     // 10 x 20,000
        rule.onNodeWithText("+").assertIsNotEnabled()
    }

    @Test fun A_EVD_09_confirmPurchase_opensDialogWithSummary() {
        show()
        rule.onNodeWithText("Confirm Purchase").performClick()
        rule.onNodeWithText("Confirm purchase").assertExists()
        rule.onNode(hasText("Total: MWK 20,000", substring = true)).assertExists()
        rule.onNodeWithText("Cancel").assertExists()
    }

    @Test fun A_EVD_10_confirmingDialog_invokesCallbackWithTierAndQuantity() {
        show()
        plus(1)                                               // quantity 2
        rule.onNodeWithText("Confirm Purchase").performClick()
        rule.onNodeWithText("Confirm").performClick()
        assertNotNull(purchasedTier)
        assertEquals("General Access", purchasedTier!!.name)
        assertEquals(20000, purchasedTier!!.price)
        assertEquals(2, purchasedQuantity)
    }

    @Test fun A_EVD_11_cancellingDialog_doesNotInvokeCallback() {
        show()
        rule.onNodeWithText("Confirm Purchase").performClick()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Confirm purchase").assertDoesNotExist()
        assertFalse(purchasedQuantity > 0)
    }

    @Test fun A_EVD_12_emptyDescription_showsFallbackText() {
        show(testEvent(description = ""))
        rule.onNodeWithText("More details coming soon.").assertExists()
    }

    @Test fun A_EVD_13_performers_areListed() {
        show()
        rule.onNodeWithText("Also performing").assertExists()
        rule.onNodeWithText("Opener One").assertExists()
        rule.onNodeWithText("Opener Two").assertExists()
    }

    @Test fun A_EVD_14_backButton_invokesCallback() {
        show()
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backClicks)
    }

    @Test fun A_EVD_15_eventWithoutImage_showsInitialLetterPlaceholder() {
        show()   // testEvent() has imageRes = null
        rule.onNodeWithText("T").assertExists()
    }
}