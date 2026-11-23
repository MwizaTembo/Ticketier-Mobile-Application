package com.example.ticketier.data

import androidx.annotation.DrawableRes
import java.util.Locale

// Moved out of home.kt. Models used by SEVERAL screens (home, ticket details, admin)
// belong in a shared "data" package, not inside one screen's file.

data class TicketTier(
    val name: String,
    val perk: String,
    val price: Int,
    val id: Long = 0L   // the tier's id in the Supabase ticket_tiers table (needed to buy it)
)

fun tiersOf(earlyBird: Int, general: Int, vip: Int) = listOf(
    TicketTier("Early Bird", "Limited discounted tickets", earlyBird),
    TicketTier("General Access", "Standard entry", general),
    TicketTier("VIP", "Front section + fast-lane entry", vip)
)

// Locale.US always uses commas (15,000), even if the phone's language would print 15.000
fun formatMwk(amount: Int): String = "MWK " + "%,d".format(Locale.US, amount)

// ONE list of categories, used by the home filter chips AND the admin "post event" form.
// (Your old data had "Concert" on one event but "Concerts" in the chip list, so that
// event never matched its filter. A single shared list prevents that kind of typo.)
val eventCategories = listOf("Concerts", "Bands", "Festivals", "IndiePop", "Pop", "Comedy")

data class Event(
    val id: Int,
    val artist: String,
    val category: String,
    val day: String,
    val month: String,
    val time: String,
    val venue: String,
    val location: String,
    val description: String,
    val performers: List<String>,
    val tiers: List<TicketTier>,
    @param:DrawableRes val imageRes: Int? = null, // local picture (the 6 original events)
    val imageUrl: String? = null                  // picture uploaded by an admin (Supabase Storage)
) {
    val price: String get() = formatMwk(tiers.minOf { it.price })
}