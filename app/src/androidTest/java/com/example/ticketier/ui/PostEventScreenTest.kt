package com.example.ticketier.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ticketier.TicketierTestRule
import com.example.ticketier.data.EventRepository
import com.example.ticketier.fillPostEventForm
import com.example.ticketier.showScreen
import com.example.ticketier.tapPostEvent
import com.example.ticketier.typeInto
import com.example.ticketier.ui.theme.screens.PostEventScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PostEventScreenTest {
    @get:Rule(order = 0) val repos = TicketierTestRule()
    @get:Rule(order = 1) val rule = createComposeRule()

    private var posted = false
    private var backClicks = 0

    private fun show() = rule.showScreen {
        PostEventScreen(onBack = { backClicks++ }, onPosted = { posted = true })
    }

    @Test fun A_PEV_01_emptyForm_showsErrorsAndPostsNothing() {
        show()
        val before = EventRepository.events.size
        rule.tapPostEvent()
        // artist, venue, location, time, and the 3 prices
        rule.onAllNodesWithText("Required").assertCountEquals(7)
        rule.onNodeWithText("1 to 31").assertExists()
        rule.onNodeWithText("Select a month").assertExists()
        assertFalse(posted)
        assertEquals(before, EventRepository.events.size)
    }

    @Test fun A_PEV_02_validForm_postsEventAndNavigates() {
        show()
        val before = EventRepository.events.size
        rule.fillPostEventForm()
        rule.tapPostEvent()
        assertTrue(posted)
        assertEquals(before + 1, EventRepository.events.size)
    }

    @Test fun A_PEV_03_postedEvent_hasTheEnteredDetails() {
        show()
        rule.fillPostEventForm(artist = "Test Band", venue = "Test Hall", location = "Zomba", time = "7:00 PM")
        rule.tapPostEvent()
        val event = EventRepository.events.first()
        assertEquals("Test Band", event.artist)
        assertEquals("Test Hall", event.venue)
        assertEquals("Zomba", event.location)
        assertEquals("7:00 PM", event.time)
        assertEquals("Concerts", event.category)   // the default category
        assertEquals("", event.description)
    }

    @Test fun A_PEV_04_postedEvent_hasThreeTiersWithTheEnteredPrices() {
        show()
        rule.fillPostEventForm(earlyBird = "10000", general = "20000", vip = "35000")
        rule.tapPostEvent()
        val tiers = EventRepository.events.first().tiers
        assertEquals(listOf("Early Bird", "General Access", "VIP"), tiers.map { it.name })
        assertEquals(listOf(10000, 20000, 35000), tiers.map { it.price })
    }

    @Test fun A_PEV_05_singleDigitDay_isPaddedToTwoDigits() {
        show()
        rule.fillPostEventForm(day = "5", month = "MAR")
        rule.tapPostEvent()
        val event = EventRepository.events.first()
        assertEquals("05", event.day)
        assertEquals("MAR", event.month)
    }

    @Test fun A_PEV_06_performers_areSplitAndTrimmed() {
        show()
        rule.fillPostEventForm(performers = "  Opener One ,Opener Two,, ")
        rule.tapPostEvent()
        assertEquals(listOf("Opener One", "Opener Two"), EventRepository.events.first().performers)
    }

    @Test fun A_PEV_07_blankPerformers_defaultToToBeAnnounced() {
        show()
        rule.fillPostEventForm(performers = "")
        rule.tapPostEvent()
        assertEquals(listOf("To be announced"), EventRepository.events.first().performers)
    }

    @Test fun A_PEV_08_dayAbove31_isRejected() {
        show()
        val before = EventRepository.events.size
        rule.fillPostEventForm(day = "32")
        rule.tapPostEvent()
        rule.onNodeWithText("1 to 31").assertExists()
        assertFalse(posted)
        assertEquals(before, EventRepository.events.size)
    }

    @Test fun A_PEV_09_dayZero_isRejected() {
        show()
        val before = EventRepository.events.size
        rule.fillPostEventForm(day = "0")
        rule.tapPostEvent()
        rule.onNodeWithText("1 to 31").assertExists()
        assertFalse(posted)
        assertEquals(before, EventRepository.events.size)
    }

    @Test fun A_PEV_10_missingMonth_isRejected() {
        show()
        val before = EventRepository.events.size
        rule.fillPostEventForm(month = null)
        rule.tapPostEvent()
        rule.onNodeWithText("Select a month").assertExists()
        assertFalse(posted)
        assertEquals(before, EventRepository.events.size)
    }

    @Test fun A_PEV_11_priceField_ignoresLetters() {
        show()
        rule.typeInto("Early Bird price", "12ab34")
        rule.onNodeWithText("Early Bird price").assertTextContains("1234")
    }

    @Test fun A_PEV_12_selectedCategoryChip_isUsedForTheEvent() {
        show()
        rule.onNodeWithText("Bands").performClick()   // near the top, visible without scrolling
        rule.fillPostEventForm()
        rule.tapPostEvent()
        assertEquals("Bands", EventRepository.events.first().category)
    }

    @Test fun A_PEV_13_backButton_invokesCallback() {
        show()
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backClicks)
    }
}