package com.example.ticketier.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ticketier.TicketierTestRule
import com.example.ticketier.data.Event
import com.example.ticketier.data.EventRepository
import com.example.ticketier.showScreen
import com.example.ticketier.testEvent
import com.example.ticketier.ui.theme.screens.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule(order = 0) val repos = TicketierTestRule()
    @get:Rule(order = 1) val rule = createComposeRule()

    private var clickedEvent: Event? = null
    private var ticketsTabClicks = 0

    private fun show() = rule.showScreen {
        HomeScreen(
            onEventClick = { clickedEvent = it },
            onTicketsClick = { ticketsTabClicks++ }
        )
    }

    private fun search(text: String) {
        // The search bar is the only text field on this screen
        rule.onNode(hasSetTextAction()).performTextInput(text)
    }

    private val emptyMessage = "No events found. Try another search or category."

    @Test fun A_HOM_01_showsHeadingAndCategoryChips() {
        show()
        rule.onNodeWithText("Upcoming Events").assertExists()
        // Only the first chips are checked: the row is a LazyRow, so off-screen chips don't exist yet
        listOf("All", "Concerts", "Bands").forEach { rule.onNodeWithText(it).assertExists() }
    }

    @Test fun A_HOM_02_showsFirstEventWithLowestPrice() {
        show()
        rule.onNodeWithText("The Neighbourhood").assertExists()
        rule.onNodeWithText("MWK 15,000").assertExists()
    }

    @Test fun A_HOM_03_categoryChip_filtersEvents() {
        show()
        rule.onNodeWithText("Concerts").performClick()
        rule.onNodeWithText("Adela").assertExists()                 // a Concerts event
        rule.onNodeWithText("The Neighbourhood").assertDoesNotExist() // a Bands event
    }

    @Test fun A_HOM_04_allChip_restoresFullList() {
        show()
        rule.onNodeWithText("Concerts").performClick()
        rule.onNodeWithText("All").performClick()
        rule.onNodeWithText("The Neighbourhood").assertExists()
    }

    @Test fun A_HOM_05_search_byArtist() {
        show()
        search("doja")
        rule.onNodeWithText("Doja Cat").assertExists()
        rule.onNodeWithText("The Neighbourhood").assertDoesNotExist()
    }

    @Test fun A_HOM_06_search_ignoresCase() {
        show()
        search("DOJA")
        rule.onNodeWithText("Doja Cat").assertExists()
    }

    @Test fun A_HOM_07_search_byLocation() {
        show()
        search("zomba")
        rule.onNodeWithText("Doja Cat").assertExists()
        rule.onNodeWithText("The Neighbourhood").assertDoesNotExist()
    }

    @Test fun A_HOM_08_search_byVenue() {
        show()
        search("unity hall")
        rule.onNodeWithText("Doja Cat").assertExists()
    }

    @Test fun A_HOM_09_search_withNoMatch_showsEmptyMessage() {
        show()
        search("zzzz")
        rule.onNodeWithText(emptyMessage).assertExists()
    }

    @Test fun A_HOM_10_searchAndCategory_combine() {
        show()
        rule.onNodeWithText("Bands").performClick()
        search("doja")          // Doja Cat is Pop, not Bands
        rule.onNodeWithText(emptyMessage).assertExists()
    }

    @Test fun A_HOM_11_getTickets_passesThatEventToCallback() {
        show()
        rule.onAllNodesWithText("Get Tickets")[0].performClick()
        assertNotNull(clickedEvent)
        assertEquals(EventRepository.events.first().id, clickedEvent!!.id)
    }

    @Test fun A_HOM_12_ticketsTab_invokesTicketsCallback() {
        show()
        rule.onNodeWithText("Tickets").performClick()
        assertEquals(1, ticketsTabClicks)
    }



    @Test fun A_HOM_13_adminPostedEvent_appearsOnHome() = runBlocking {
        EventRepository.add(testEvent(id = EventRepository.nextId(), artist = "Zed Test Band"))
        show()
        rule.onNodeWithText("Zed Test Band").assertExists()
        assertTrue(EventRepository.events.first().artist == "Zed Test Band")
    }
}