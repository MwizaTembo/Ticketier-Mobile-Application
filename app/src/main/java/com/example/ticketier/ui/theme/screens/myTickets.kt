package com.example.ticketier.ui.theme.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ticketier.data.Ticket
import com.example.ticketier.data.TicketRepository
import com.example.ticketier.ui.theme.TicketierTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTicketsScreen(
    onBack: () -> Unit,
    onTicketClick: (String) -> Unit,   // gives the serial of the tapped ticket
    onBrowseEvents: () -> Unit
) {
    // Reading the shared list: this screen updates by itself when tickets are added or used.
    val tickets = TicketRepository.tickets

    // Download this user's tickets from Supabase when the screen opens.
    LaunchedEffect(Unit) { TicketRepository.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My tickets") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (tickets.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No tickets yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Buy a ticket and it will show up here with its QR code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBrowseEvents, shape = MaterialTheme.shapes.medium) {
                    Text("Browse events")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tickets, key = { it.serial }) { ticket ->
                    TicketRow(ticket, onClick = { onTicketClick(ticket.serial) })
                }
            }
        }
    }
}

@Composable
private fun TicketRow(ticket: Ticket, onClick: () -> Unit) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open ticket") { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(ticket.artist, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${ticket.day} ${ticket.month} · ${ticket.time}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${ticket.tierName} · ${ticket.venue}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    ticket.serial,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            TicketStatusChip(used = ticket.used)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MyTicketsScreenPreview() {
    TicketierTheme(darkTheme = false) {
        MyTicketsScreen(onBack = {}, onTicketClick = {}, onBrowseEvents = {})
    }
}