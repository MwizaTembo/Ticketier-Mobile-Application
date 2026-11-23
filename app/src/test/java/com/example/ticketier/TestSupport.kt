package com.example.ticketier

import com.example.ticketier.data.Event
import com.example.ticketier.data.EventRepository
import com.example.ticketier.data.Ticket
import com.example.ticketier.data.TicketRepository
import com.example.ticketier.data.TicketTier
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.Locale


class TicketierTestRule : TestWatcher() {
    private var savedLocale: Locale = Locale.getDefault()
    private var savedEvents: List<Event> = emptyList()

    override fun starting(description: Description) {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)

        // Offline/test mode: repositories never touch Supabase while this flag is on.
        EventRepository.testMode = true
        TicketRepository.testMode = true

        savedEvents = EventRepository.events.toList()
        ticketList().clear()

        // Seed the six "sample" events so the seed-based tests have something to assert on.
        if (EventRepository.events.isEmpty()) {
            EventRepository.eventsSeedForTests()
        }
    }

    override fun finished(description: Description) {
        eventList().apply { clear(); addAll(savedEvents) }
        ticketList().clear()
        Locale.setDefault(savedLocale)
        EventRepository.testMode = false
        TicketRepository.testMode = false
    }

    @Suppress("UNCHECKED_CAST")
    private fun eventList(): MutableList<Event> =
        EventRepository::class.java.getDeclaredField("_events")
            .apply { isAccessible = true }.get(EventRepository) as MutableList<Event>

    @Suppress("UNCHECKED_CAST")
    private fun ticketList(): MutableList<Ticket> =
        TicketRepository::class.java.getDeclaredField("_tickets")
            .apply { isAccessible = true }.get(TicketRepository) as MutableList<Ticket>
}


fun testEvent(
    id: Int = 99,
    artist: String = "Test Band",
    description: String = "A test event."
) = Event(
    id = id,
    artist = artist,
    category = "Concerts",
    day = "14",
    month = "NOV",
    time = "7:00 PM",
    venue = "Test Hall",
    location = "Zomba",
    description = description,
    performers = listOf("Opener One", "Opener Two"),
    tiers = listOf(
        TicketTier("Early Bird", "Limited discounted tickets", 10000, id = 1L),
        TicketTier("General Access", "Standard entry", 20000, id = 2L),
        TicketTier("VIP", "Front section + fast-lane entry", 35000, id = 3L)
    ),
    imageRes = null
)


fun buyTickets(count: Int = 1, event: Event = testEvent(), tierIndex: Int = 1): List<Ticket> =
    runBlocking {
        val before = TicketRepository.tickets.map { it.serial }.toSet()
        val err = TicketRepository.purchase(event, event.tiers[tierIndex], count)
        check(err == null) { "purchase failed: $err" }
        TicketRepository.tickets.filter { it.serial !in before }
    }
