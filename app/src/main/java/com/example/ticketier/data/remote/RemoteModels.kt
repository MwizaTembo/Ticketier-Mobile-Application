package com.example.ticketier.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// These classes match the columns of your Supabase tables, so the app can read and
// write rows. `@SerialName` links a Kotlin name (eventId) to the column name (event_id).

/** One row of `ticket_tiers`. */
@Serializable
data class TierRow(
    val id: Long,
    @SerialName("event_id") val eventId: Long,
    val name: String,
    val perk: String = "",
    val price: Int,
    @SerialName("sort_order") val sortOrder: Int = 0
)

/** One row of `events`, with its tiers attached (we ask Supabase for both at once). */
@Serializable
data class EventRow(
    val id: Long,
    val artist: String,
    val category: String,
    val day: String,
    val month: String,
    val time: String,
    val venue: String,
    val location: String,
    val description: String = "",
    val performers: List<String> = emptyList(),
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("ticket_tiers") val tiers: List<TierRow> = emptyList()
)

/** What we send when an admin posts a new event. */
@Serializable
data class NewEventRow(
    val artist: String,
    val category: String,
    val day: String,
    val month: String,
    val time: String,
    val venue: String,
    val location: String,
    val description: String,
    val performers: List<String>,
    @SerialName("image_url") val imageUrl: String? = null
)

/** What we send for each of the 3 ticket tiers of a new event. */
@Serializable
data class NewTierRow(
    @SerialName("event_id") val eventId: Long,
    val name: String,
    val perk: String,
    val price: Int,
    @SerialName("sort_order") val sortOrder: Int
)

/** One row of `tickets`. */
@Serializable
data class TicketRow(
    val serial: String,
    @SerialName("event_id") val eventId: Long? = null,
    val artist: String,
    val day: String,
    val month: String,
    val time: String,
    val venue: String,
    val location: String,
    @SerialName("tier_name") val tierName: String,
    val price: Int,
    val used: Boolean = false
)