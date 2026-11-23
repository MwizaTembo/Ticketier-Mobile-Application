package com.example.ticketier.data

import com.example.ticketier.TicketierTestRule
import com.example.ticketier.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests for the small pure functions and models in Event.kt (no Android needed). */
class EventModelTest {

    @get:Rule val rule = TicketierTestRule()

    @Test fun U_EVT_01_formatMwk_addsThousandsSeparator() {
        assertEquals("MWK 15,000", formatMwk(15000))
    }

    @Test fun U_EVT_02_formatMwk_zero() {
        assertEquals("MWK 0", formatMwk(0))
    }

    @Test fun U_EVT_03_formatMwk_millions() {
        assertEquals("MWK 1,250,000", formatMwk(1_250_000))
    }

    @Test fun U_EVT_04_tiersOf_returnsThreeTiersInOrder() {
        val tiers = tiersOf(1, 2, 3)
        assertEquals(listOf("Early Bird", "General Access", "VIP"), tiers.map { it.name })
        assertEquals(listOf(1, 2, 3), tiers.map { it.price })
    }

    @Test fun U_EVT_05_tiersOf_everyTierHasPerkText() {
        assertTrue(tiersOf(1, 2, 3).all { it.perk.isNotBlank() })
    }

    @Test fun U_EVT_06_eventPrice_isLowestTierPrice() {
        // Prices deliberately NOT in ascending order
        val event = testEvent().copy(tiers = tiersOf(30000, 20000, 40000))
        assertEquals("MWK 20,000", event.price)
    }

    @Test fun U_EVT_07_categories_areUniqueNotBlankAndExcludeAll() {
        assertEquals(eventCategories.size, eventCategories.toSet().size)
        assertTrue(eventCategories.none { it.isBlank() })
        // "All" is added by the home screen; it must not be a real category
        assertFalse(eventCategories.contains("All"))
    }
}