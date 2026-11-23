package com.example.ticketier.data

import com.example.ticketier.TicketierTestRule
import com.example.ticketier.buyTickets
import com.example.ticketier.testEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TicketRepositoryTest {

    @get:Rule val rule = TicketierTestRule()

    @Test fun U_TKT_01_purchase_createsOneTicketPerQuantity() {
        val created = buyTickets(count = 3)
        assertEquals(3, created.size)
        assertEquals(3, TicketRepository.tickets.size)
    }

    @Test fun U_TKT_02_purchase_everyTicketHasDifferentSerial() {
        val serials = buyTickets(count = 5).map { it.serial }
        assertEquals(5, serials.toSet().size)
    }

    @Test fun U_TKT_03_serial_hasExpectedFormat() {
        val format = Regex("^TKT-\\d{3}-[A-HJKMNP-Z2-9]{8}$")
        buyTickets(count = 20).forEach {
            assertTrue("Bad serial: ${it.serial}", format.matches(it.serial))
        }
    }

    @Test fun U_TKT_04_serial_containsEventIdPaddedToThreeDigits() {
        assertTrue(buyTickets(1, testEvent(id = 1)).single().serial.startsWith("TKT-001-"))
        assertTrue(buyTickets(1, testEvent(id = 12)).single().serial.startsWith("TKT-012-"))
        assertTrue(buyTickets(1, testEvent(id = 123)).single().serial.startsWith("TKT-123-"))
    }

    @Test fun U_TKT_05_serial_neverUsesConfusingCharacters() {
        val confusing = setOf('0', 'O', '1', 'I', 'L')
        buyTickets(count = 200).forEach {
            val code = it.serial.substringAfterLast('-')
            assertTrue("Confusing character in $code", code.none { c -> c in confusing })
        }
    }

    @Test fun U_TKT_06_serials_areUniqueAcrossLargePurchase() {
        val serials = buyTickets(count = 1000).map { it.serial }
        assertEquals(1000, serials.toSet().size)
    }

    @Test fun U_TKT_07_ticket_copiesEventAndTierDetails() {
        val event = testEvent()
        val tier = event.tiers[2]
        val ticket = buyTickets(1, event, tierIndex = 2).single()

        assertEquals(event.id, ticket.eventId)
        assertEquals(event.artist, ticket.artist)
        assertEquals(event.day, ticket.day)
        assertEquals(event.month, ticket.month)
        assertEquals(event.time, ticket.time)
        assertEquals(event.venue, ticket.venue)
        assertEquals(event.location, ticket.location)
        assertEquals("VIP", ticket.tierName)
        assertEquals(35000, ticket.price)
    }

    @Test fun U_TKT_08_newTicket_isNotUsed() {
        assertFalse(buyTickets(1).single().used)
    }

    @Test fun U_TKT_09_purchase_insertsNewestTicketsFirst() {
        val older = buyTickets(count = 1)
        val newer = buyTickets(count = 2)
        val list = TicketRepository.tickets
        assertEquals(newer.map { it.serial }, list.take(2).map { it.serial })
        assertEquals(older.single().serial, list[2].serial)
    }

    @Test fun U_TKT_10_purchase_zeroQuantityCreatesNothing() {
        assertTrue(buyTickets(count = 0).isEmpty())
        assertTrue(TicketRepository.tickets.isEmpty())
    }

    @Test fun U_TKT_11_find_returnsTicketBySerial() {
        val ticket = buyTickets(count = 3)[1]
        assertEquals(ticket.serial, TicketRepository.find(ticket.serial)?.serial)
    }

    @Test fun U_TKT_12_find_unknownSerialReturnsNull() {
        buyTickets(count = 2)
        assertNull(TicketRepository.find("TKT-000-NOTREAL1"))
    }

    @Test fun U_TKT_13_markUsed_marksOnlyThatTicket() {
        val tickets = buyTickets(count = 3)
        TicketRepository.markUsed(tickets[1].serial)

        assertTrue(TicketRepository.find(tickets[1].serial)!!.used)
        assertFalse(TicketRepository.find(tickets[0].serial)!!.used)
        assertFalse(TicketRepository.find(tickets[2].serial)!!.used)
    }

    @Test fun U_TKT_14_markUsed_calledTwiceStaysUsed() {
        val ticket = buyTickets(1).single()
        TicketRepository.markUsed(ticket.serial)
        TicketRepository.markUsed(ticket.serial)
        assertTrue(TicketRepository.find(ticket.serial)!!.used)
        assertEquals(1, TicketRepository.tickets.size)
    }

    @Test fun U_TKT_15_markUsed_unknownSerialDoesNothing() {
        val tickets = buyTickets(count = 2)
        TicketRepository.markUsed("TKT-000-NOTREAL1")
        assertTrue(tickets.all { !TicketRepository.find(it.serial)!!.used })
        assertEquals(2, TicketRepository.tickets.size)
    }

    @Test fun U_TKT_16_ticket_survivesEventRemoval() = runBlocking {
        val event = testEvent(id = EventRepository.nextId(), artist = "Vanishing Band")
        EventRepository.add(event)
        val ticket = buyTickets(1, event, tierIndex = 0).single()

        EventRepository.remove(event.id)

        val stored = TicketRepository.find(ticket.serial)
        assertNotNull(stored)
        assertEquals("Vanishing Band", stored!!.artist)
    }

    @Test fun U_TKT_17_scan_firstScanOfValidTicketIsAccepted() = runBlocking {
        val ticket = buyTickets(1).single()
        assertEquals(ScanResult.ACCEPTED, TicketRepository.scan(ticket.serial))
        assertTrue(TicketRepository.find(ticket.serial)!!.used)
    }

    @Test fun U_TKT_18_scan_secondScanOfSameTicketIsRejected() = runBlocking {
        val ticket = buyTickets(1).single()
        TicketRepository.scan(ticket.serial)
        assertEquals(ScanResult.ALREADY_USED, TicketRepository.scan(ticket.serial))
        assertTrue(TicketRepository.find(ticket.serial)!!.used)
    }

    @Test fun U_TKT_19_scan_unknownSerialIsNotFound() = runBlocking {
        buyTickets(2)
        assertEquals(ScanResult.NOT_FOUND, TicketRepository.scan("TKT-000-NOTREAL1"))
    }

    @Test fun U_TKT_20_scan_onlyAffectsTheScannedTicket() = runBlocking {
        val tickets = buyTickets(3)
        TicketRepository.scan(tickets[0].serial)
        assertFalse(TicketRepository.find(tickets[1].serial)!!.used)
        assertEquals(ScanResult.ACCEPTED, TicketRepository.scan(tickets[1].serial))
    }
}