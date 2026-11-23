package com.example.ticketier.data

import com.example.ticketier.TicketierTestRule
import com.example.ticketier.testEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EventRepositoryTest {

    @get:Rule val rule = TicketierTestRule()

    @Test fun U_EVR_01_seed_isNotEmptyAndHasUniqueIds() {
        val ids = EventRepository.events.map { it.id }
        assertTrue(ids.isNotEmpty())
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun U_EVR_02_seed_categoriesAllExistInCategoryList() {
        EventRepository.events.forEach {
            assertTrue("'${it.category}' (${it.artist}) is not in eventCategories",
                it.category in eventCategories)
        }
    }

    @Test fun U_EVR_03_seed_everyEventHasThreeAscendingPositiveTiers() {
        EventRepository.events.forEach { event ->
            assertEquals("${event.artist} tiers", 3, event.tiers.size)
            val prices = event.tiers.map { it.price }
            assertTrue("${event.artist} has a non-positive price", prices.all { it > 0 })
            assertEquals("${event.artist} prices should ascend", prices.sorted(), prices)
        }
    }

    @Test fun U_EVR_04_nextId_isGreaterThanAllExistingIds() {
        assertTrue(EventRepository.nextId() > EventRepository.events.maxOf { it.id })
    }

    @Test fun U_EVR_05_add_putsNewEventFirstAndGrowsList() = runBlocking {
        val before = EventRepository.events.size
        val event = testEvent(id = EventRepository.nextId(), artist = "Newest Act")
        EventRepository.add(event)
        assertEquals(before + 1, EventRepository.events.size)
        assertEquals("Newest Act", EventRepository.events.first().artist)
    }

    @Test fun U_EVR_06_add_twiceGivesDifferentIds() = runBlocking {
        val first = testEvent(id = EventRepository.nextId())
        EventRepository.add(first)
        val second = testEvent(id = EventRepository.nextId())
        EventRepository.add(second)
        assertNotEquals(first.id, second.id)
        val ids = EventRepository.events.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun U_EVR_07_remove_deletesOnlyThatEvent() = runBlocking {
        val keep = testEvent(id = EventRepository.nextId(), artist = "Keep Me")
        EventRepository.add(keep)
        val drop = testEvent(id = EventRepository.nextId(), artist = "Drop Me")
        EventRepository.add(drop)

        EventRepository.remove(drop.id)

        assertTrue(EventRepository.events.none { it.id == drop.id })
        assertTrue(EventRepository.events.any { it.id == keep.id })
    }

    @Test fun U_EVR_08_remove_unknownIdChangesNothing() = runBlocking {
        val before = EventRepository.events.size
        EventRepository.remove(9999)
        assertEquals(before, EventRepository.events.size)
    }
}