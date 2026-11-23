package com.example.ticketier.ui.theme.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ticketier.data.Ticket
import com.example.ticketier.data.TicketRepository
import com.example.ticketier.reminder.ReminderScheduler
import com.example.ticketier.ui.theme.TicketierTheme
import com.example.ticketier.util.generateQrBitmap
import com.example.ticketier.util.writeTicketPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The screen only receives the SERIAL, then looks the ticket up. Why not pass the whole
 * Ticket? Because the ticket can change (it becomes "used"), and looking it up from the
 * repository means this screen always shows the latest version.
 */
@Composable
fun DigitalTicketScreen(serial: String, onBack: () -> Unit) {
    val ticket = TicketRepository.find(serial)

    if (ticket == null) {
        TicketNotFound(onBack)
    } else {
        TicketContent(
            ticket = ticket,
            onBack = onBack,
            onSimulateScan = { TicketRepository.markUsed(serial) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TicketContent(ticket: Ticket, onBack: () -> Unit, onSimulateScan: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Generated once per serial (remember with a key), not on every redraw.
    val qrBitmap: Bitmap = remember(ticket.serial) { generateQrBitmap(ticket.serial) }
    val qrImage: ImageBitmap = remember(qrBitmap) { qrBitmap.asImageBitmap() }

    // CONCEPT: the system "Save as" screen. The user picks WHERE to save, and Android gives
    // us a Uri to write to. That means no storage permission is needed on any Android version.
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                // File writing happens off the main thread so the UI never freezes.
                val ok = withContext(Dispatchers.IO) { writeTicketPdf(context, uri, ticket, qrBitmap) }
                snackbarHostState.showSnackbar(
                    if (ok) "Ticket saved as PDF" else "Could not save the ticket. Try again."
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your ticket") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TicketCard(ticket, qrImage)

            Button(
                onClick = { saveLauncher.launch("Ticketier-${ticket.serial}.pdf") },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Download ticket (PDF)", style = MaterialTheme.typography.titleMedium)
            }

            // DEMO ONLY: schedules a WorkManager reminder 1 minute from now.
            TextButton(
                onClick = {
                    ReminderScheduler.scheduleTest(context, ticket)
                    scope.launch {
                        snackbarHostState.showSnackbar("Reminder set. It will appear in about 1 minute.")
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Demo: remind me in 1 minute")
            }

            // DEMO ONLY: lets you show the lecturer "the QR expires once scanned".
            // In a real app the gate scanner does this, and it must happen on a server.
            if (!ticket.used) {
                TextButton(
                    onClick = onSimulateScan,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Demo: simulate a gate scan")
                }
            }
        }
    }
}

@Composable
private fun TicketCard(ticket: Ticket, qr: ImageBitmap) {
    val dashColor = MaterialTheme.colorScheme.outlineVariant

    ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column {
            // ----- Header -----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)

            ) {
                Column {
                    Text(
                        " Ticketier",
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = GrandHotel),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        ticket.artist,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        "${ticket.day} ${ticket.month} · ${ticket.time}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        "${ticket.venue}, ${ticket.location}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }
            }

            // ----- Ticket type + status -----
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "TICKET TYPE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(ticket.tierName, style = MaterialTheme.typography.titleMedium)
                }
                TicketStatusChip(used = ticket.used)
            }

            // ----- Dashed "tear here" line -----
            Canvas(Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 16.dp)) {
                drawLine(
                    color = dashColor,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
                )
            }

            // ----- QR code + serial number -----
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Fixed white background + padding = the "quiet zone" scanners need.
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(Color.White)
                            .padding(12.dp)
                    ) {
                        Image(
                            bitmap = qr,
                            contentDescription = "QR code for ticket ${ticket.serial}",
                            filterQuality = FilterQuality.None, // keep the squares sharp
                            modifier = Modifier.fillMaxSize().alpha(if (ticket.used) 0.2f else 1f)
                        )
                    }
                    if (ticket.used) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "USED",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    ticket.serial,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    "Serial number",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (ticket.used) "This ticket has been scanned and can't be used again."
                    else "Show this QR code at the entrance. It can only be scanned once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// `internal` = visible to other files in the module. myTickets.kt reuses it.
@Composable
internal fun TicketStatusChip(used: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (used) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (used) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text(
            if (used) "USED" else "VALID",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TicketNotFound(onBack: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("We couldn't find this ticket.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Go back") }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun TicketContentPreview() {
    TicketierTheme(darkTheme = false) {
        TicketContent(
            ticket = Ticket("TKT-001-7K3M9QXD", 1, "The Neighbourhood", "14", "NOV", "7:00 PM",
                "BICC", "Lilongwe", "General Access", 20000),
            onBack = {},
            onSimulateScan = {}
        )
    }
}