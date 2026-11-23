package com.example.ticketier

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.ticketier.data.Event
import com.example.ticketier.data.EventRepository
import com.example.ticketier.data.Ticket
import com.example.ticketier.data.TicketRepository
import com.example.ticketier.data.TicketTier
import com.example.ticketier.ui.theme.TicketierTheme
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.Locale

/**
 * Resets the shared repositories around every test (see the unit-test copy of this
 * file for the full explanation). Use it BEFORE the compose rule:
 *
 *   @get:Rule(order = 0) val repos = TicketierTestRule()
 *   @get:Rule(order = 1) val rule = createComposeRule()
 */
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

        // Seed sample events so seed-based tests have data.
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

/**
 * A predictable event. Tiers: Early Bird 10,000 / General 20,000 / VIP 35,000.
 * Tier ids are non-zero (1L/2L/3L) so TicketRepository.purchase() accepts them.
 */
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

/**
 * Buys `count` tickets and returns the newly created ones.
 * purchase() is suspend; we run it and read the new tickets back from the list.
 */
fun buyTickets(count: Int = 1, event: Event = testEvent(), tierIndex: Int = 1): List<Ticket> =
    runBlocking {
        val before = TicketRepository.tickets.map { it.serial }.toSet()
        val err = TicketRepository.purchase(event, event.tiers[tierIndex], count)
        check(err == null) { "purchase failed: $err" }
        TicketRepository.tickets.filter { it.serial !in before }
    }

// ---------- Compose helpers ----------

/** Shows a screen inside the app theme (light mode, so results don't depend on the phone). */
fun ComposeContentTestRule.showScreen(content: @Composable () -> Unit) {
    setContent { TicketierTheme(darkTheme = false) { content() } }
}

/** Waits (up to 6 s) until some node shows `text`, then lets animations finish. */
fun ComposeContentTestRule.waitForText(text: String, timeoutMillis: Long = 6_000) {
    waitUntil(timeoutMillis = timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    waitForIdle()
}

/** Types into a field found by its label, scrolling it into view first. */
fun ComposeContentTestRule.typeInto(label: String, text: String) {
    onNodeWithText(label).performScrollTo().performTextInput(text)
}

/**
 * Picks a month chip. The chips are a horizontal LazyRow: chips that are off screen do not
 * exist until the row is scrolled. So we scroll the form down to the row, then scroll the
 * row sideways until the wanted month exists, then tap it.
 */
fun ComposeContentTestRule.selectMonth(month: String) {
    onNodeWithText("Description").performScrollTo()
    onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and
                hasAnyDescendant(hasText("JAN"))
    ).performScrollToNode(hasText(month))
    onNodeWithText(month).performClick()
}

/**
 * Fills the "Post new event" form. Month defaults to MAR. Pass month = null to leave it empty.
 * Pass day = "" to leave the day empty.
 */
fun ComposeContentTestRule.fillPostEventForm(
    artist: String = "Test Band",
    venue: String = "Test Hall",
    location: String = "Zomba",
    day: String = "5",
    month: String? = "MAR",
    time: String = "7:00 PM",
    earlyBird: String = "10000",
    general: String = "20000",
    vip: String = "35000",
    performers: String = ""
) {
    typeInto("Artist / event name", artist)
    if (performers.isNotEmpty()) typeInto("Other performers (separate with commas)", performers)
    typeInto("Venue", venue)
    typeInto("Location", location)
    if (day.isNotEmpty()) typeInto("Day", day)
    typeInto("Time (e.g. 7:00 PM)", time)
    month?.let { selectMonth(it) }
    typeInto("Early Bird price", earlyBird)
    typeInto("General Access price", general)
    typeInto("VIP price", vip)
}

fun ComposeContentTestRule.tapPostEvent() {
    onNodeWithText("Post event").performScrollTo().performClick()
}

/** Logs in on the login screen with the demo customer account (user@test.com / user123). */
fun ComposeContentTestRule.logInAsDemoCustomer() {
    onNodeWithText("Email").performTextInput("user@test.com")
    onNodeWithText("Password").performTextInput("user123")
    onNodeWithText("Login").performClick()
}