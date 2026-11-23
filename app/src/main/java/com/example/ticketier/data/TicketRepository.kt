package com.example.ticketier.data

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ticketier.data.remote.SupabaseProvider
import com.example.ticketier.data.remote.TicketRow
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import kotlin.random.Random

enum class ScanResult {
    ACCEPTED, ALREADY_USED, NOT_FOUND
}

private fun TicketRow.toTicket() = Ticket(
    serial = serial,
    eventId = eventId?.toInt() ?: 0,
    artist = artist,
    day = day,
    month = month,
    time = time,
    venue = venue,
    location = location,
    tierName = tierName,
    price = price,
    used = used
)

object TicketRepository {
    private const val TAG = "TicketRepository"
    private val client get() = SupabaseProvider.client

    private val _tickets = mutableStateListOf<Ticket>()
    val tickets: List<Ticket> get() = _tickets

    var loading by mutableStateOf(false)
        private set

    /** TEST-ONLY: same meaning as in EventRepository. */
    @VisibleForTesting
    var testMode: Boolean = false

    suspend fun refresh() {
        if (testMode) return
        val userId = client.auth.currentUserOrNull()?.id
        if (userId == null) {
            _tickets.clear()
            return
        }
        loading = true
        try {
            val rows = client.from("tickets")
                .select {
                    filter { eq("owner_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<TicketRow>()
            _tickets.clear()
            _tickets.addAll(rows.map { it.toTicket() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Loading tickets failed", e)
        } finally {
            loading = false
        }
    }

    suspend fun purchase(event: Event, tier: TicketTier, quantity: Int): String? {
        if (tier.id == 0L) return "This ticket type is not available online."
        if (testMode) {
            repeat(quantity.coerceAtLeast(0)) {
                _tickets.add(0, newFakeTicket(event, tier))
            }
            return null
        }
        return try {
            val rows = client.postgrest.rpc(
                function = "purchase_tickets",
                parameters = buildJsonObject {
                    put("p_event_id", event.id)
                    put("p_tier_id", tier.id)
                    put("p_quantity", quantity)
                }
            ).decodeList<TicketRow>()
            _tickets.addAll(0, rows.map { it.toTicket() })
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Purchase failed", e)
            "Your payment went through but we couldn't create your tickets. Check your internet and try again."
        }
    }

    fun find(serial: String): Ticket? = _tickets.find { it.serial == serial }

    fun clear() { _tickets.clear() }

    suspend fun scan(serial: String): ScanResult {
        if (testMode) {
            val i = _tickets.indexOfFirst { it.serial == serial }
            if (i < 0) return ScanResult.NOT_FOUND
            return if (_tickets[i].used) ScanResult.ALREADY_USED else {
                _tickets[i] = _tickets[i].copy(used = true)
                ScanResult.ACCEPTED
            }
        }
        return try {
            when (client.postgrest.rpc(
                function = "scan_ticket",
                parameters = buildJsonObject { put("p_serial", serial) }
            ).decodeAs<String>()) {
                "ACCEPTED" -> ScanResult.ACCEPTED
                "ALREADY_USED" -> ScanResult.ALREADY_USED
                else -> ScanResult.NOT_FOUND
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed", e)
            ScanResult.NOT_FOUND
        }
    }

    fun markUsed(serial: String) {
        val index = _tickets.indexOfFirst { it.serial == serial }
        if (index >= 0) _tickets[index] = _tickets[index].copy(used = true)
    }

    /** Test-only fake serial, mimics the server's TKT-<3 digits>-<8 chars, no I/L/O/0/1>. */
    @VisibleForTesting
    internal fun newFakeTicket(event: Event, tier: TicketTier): Ticket {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val tail = (1..8).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
        val serial = "TKT-" + String.format(Locale.US, "%03d", event.id) + "-" + tail
        return Ticket(
            serial = serial,
            eventId = event.id,
            artist = event.artist,
            day = event.day,
            month = event.month,
            time = event.time,
            venue = event.venue,
            location = event.location,
            tierName = tier.name,
            price = tier.price,
            used = false
        )
    }
}