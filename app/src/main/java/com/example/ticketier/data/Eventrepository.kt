package com.example.ticketier.data

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ticketier.R
import com.example.ticketier.data.remote.EventRow
import com.example.ticketier.data.remote.NewEventRow
import com.example.ticketier.data.remote.NewTierRow
import com.example.ticketier.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException

// Photos are not stored in the database yet (that comes with Supabase Storage).
// Until then the 6 original events keep their local pictures, matched by artist name.
private val seedImages = mapOf(
    "The Neighbourhood" to R.drawable.nbhd,
    "Adela" to R.drawable.adela,
    "Doja Cat" to R.drawable.doja,
    "Lana del rey" to R.drawable.ldr,
    "Coachella Festival" to R.drawable.coach,
    "Beyonce" to R.drawable.beyonce
)

// Turns a database row into the Event class the screens already use.
private fun EventRow.toEvent() = Event(
    id = id.toInt(),
    artist = artist,
    category = category,
    day = day,
    month = month,
    time = time,
    venue = venue,
    location = location,
    description = description,
    performers = performers,
    tiers = tiers.sortedBy { it.sortOrder }.map { TicketTier(it.name, it.perk, it.price, it.id) },
    imageRes = seedImages[artist],
    imageUrl = imageUrl
)

/**
 * CONCEPT: the events now live in Supabase, so they survive closing the app and every
 * user sees the same events. This object keeps a copy in memory (`_events`) for the
 * screens. Compose watches that list, so screens redraw when it changes.
 *
 * Reading from the internet takes time, so loading and saving are `suspend` functions:
 * call them from a coroutine (LaunchedEffect or scope.launch).
 *
 * TESTING: set `testMode = true` to make every operation work on the in-memory
 * `_events` list only. Unit tests use this so they run offline and deterministically.
 * Production code never flips the flag.
 */
object EventRepository {
    private const val TAG = "EventRepository"
    private val client get() = SupabaseProvider.client

    private val _events = mutableStateListOf<Event>()

    // Read-only view: only this object can change the list.
    val events: List<Event> get() = _events

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** TEST-ONLY: when true, all operations use the in-memory `_events` list and skip Supabase. */
    @VisibleForTesting
    var testMode: Boolean = false

    /** Highest existing id + 1. Only meaningful in test mode; Supabase assigns real ids. */
    @VisibleForTesting
    fun nextId(): Int = (_events.maxOfOrNull { it.id } ?: 0) + 1

    /**
     * TEST-ONLY: seeds the six original sample events into `_events` so seed-based tests
     * (U_EVR_01..03) have data without hitting Supabase. Does nothing when `testMode` is false.
     */
    @VisibleForTesting
    fun eventsSeedForTests() {
        if (!testMode) return
        val seed = listOf(
            Triple("The Neighbourhood", "IndiePop", 15000),
            Triple("Adela", "Pop", 12000),
            Triple("Doja Cat", "Concerts", 25000),
            Triple("Lana del rey", "Bands", 18000),
            Triple("Coachella Festival", "Festivals", 30000),
            Triple("Beyonce", "Concerts", 28000)
        )
        _events.clear()
        seed.forEachIndexed { i, (artist, cat, base) ->
            _events.add(
                Event(
                    id = i + 1,
                    artist = artist,
                    category = cat,
                    day = "1${i + 1}",
                    month = "DEC",
                    time = "8:00 PM",
                    venue = "Venue $artist",
                    location = "Lilongwe",
                    description = "Sample event",
                    performers = listOf(artist),
                    tiers = listOf(
                        TicketTier("Early Bird", "Limited discounted tickets", base, id = 1L),
                        TicketTier("General Access", "Standard entry", base * 2, id = 2L),
                        TicketTier("VIP", "Front section + fast-lane entry", base * 3, id = 3L)
                    ),
                    imageRes = seedImages[artist]
                )
            )
        }
    }

    /** Downloads all events (with their ticket tiers), newest first. */
    suspend fun refresh() {
        if (testMode) {
            error = null
            return
        }
        loading = true
        try {
            val rows = client.from("events")
                .select(Columns.raw("*, ticket_tiers(*)")) {
                    order("id", Order.DESCENDING)
                }
                .decodeList<EventRow>()
            _events.clear()
            // An event with no tiers can't be bought, so it is skipped.
            _events.addAll(rows.filter { it.tiers.isNotEmpty() }.map { it.toEvent() })
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Loading events failed", e)
            error = "Couldn't load events. Check your internet connection."
        } finally {
            loading = false
        }
    }

    /** Admin: saves a new event and its 3 tiers. Returns an error message, or null on success. */
    suspend fun add(
        artist: String,
        category: String,
        day: String,
        month: String,
        time: String,
        venue: String,
        location: String,
        description: String,
        performers: List<String>,
        earlyBird: Int,
        general: Int,
        vip: Int,
        imageUrl: String? = null
    ): String? {
        if (testMode) {
            val ev = Event(
                id = nextId(),
                artist = artist, category = category, day = day, month = month,
                time = time, venue = venue, location = location,
                description = description, performers = performers,
                tiers = tiersOf(earlyBird, general, vip).mapIndexed { i, t ->
                    t.copy(id = (i + 1).toLong())
                },
                imageRes = null, imageUrl = imageUrl
            )
            _events.add(0, ev)
            return null
        }
        return try {
            val created = client.from("events")
                .insert(
                    NewEventRow(
                        artist = artist, category = category, day = day, month = month,
                        time = time, venue = venue, location = location,
                        description = description, performers = performers,
                        imageUrl = imageUrl
                    )
                ) { select() }
                .decodeSingle<EventRow>()

            try {
                val tiers = tiersOf(earlyBird, general, vip)
                client.from("ticket_tiers").insert(
                    tiers.mapIndexed { index, t ->
                        NewTierRow(created.id, t.name, t.perk, t.price, index + 1)
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Don't leave an event without tiers behind.
                try {
                    client.from("events").delete { filter { eq("id", created.id) } }
                } catch (ignored: Exception) {
                }
                throw e
            }

            refresh()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Posting event failed", e)
            "Could not post the event. Check your internet and that you are signed in as an admin."
        }
    }

    /** Convenience overload used by tests and any caller that already has an Event object. */
    suspend fun add(event: Event): String? = add(
        artist = event.artist, category = event.category, day = event.day,
        month = event.month, time = event.time, venue = event.venue,
        location = event.location, description = event.description,
        performers = event.performers,
        earlyBird = event.tiers.getOrNull(0)?.price ?: 0,
        general = event.tiers.getOrNull(1)?.price ?: 0,
        vip = event.tiers.getOrNull(2)?.price ?: 0,
        imageUrl = event.imageUrl
    )

    /** Admin: deletes an event (its tiers go with it). */
    suspend fun remove(id: Int) {
        if (testMode) {
            _events.removeAll { it.id == id }
            error = null
            return
        }
        try {
            client.from("events").delete { filter { eq("id", id) } }
            _events.removeAll { it.id == id }
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Deleting event failed", e)
            error = "Could not delete the event."
        }
    }
}