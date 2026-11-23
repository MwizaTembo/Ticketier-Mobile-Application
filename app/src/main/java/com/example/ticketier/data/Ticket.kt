package com.example.ticketier.data

/**
 * One ADMIT-ONE ticket. Buying 3 tickets creates 3 Ticket objects, each with its OWN
 * serial number and QR code (the gate scans one code per person).
 *
 * The event details are COPIED into the ticket (like a printed receipt), so the ticket
 * still makes sense even if an admin later edits or deletes the event.
 */
data class Ticket(
    val serial: String,        // unique, e.g. TKT-001-7K3M9QXD, and it is what the QR code contains
    val eventId: Int,
    val artist: String,
    val day: String,
    val month: String,
    val time: String,
    val venue: String,
    val location: String,
    val tierName: String,
    val price: Int,
    val used: Boolean = false  // becomes true after the first scan
)