package com.example.ticketier.ui.theme.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ticketier.data.Event
import com.example.ticketier.data.EventRepository
import com.example.ticketier.data.TicketTier
import com.example.ticketier.data.formatMwk
import com.example.ticketier.ui.theme.TicketierTheme

@Composable
fun EventDetailsScreen(
    event: Event,
    onBackClick: () -> Unit,
    onPurchaseConfirmed: (TicketTier, Int) -> Unit = {_, _ ->}
) {
    var selectedTier by remember { mutableIntStateOf(1) } // starts on General Access
    var quantity by remember { mutableIntStateOf(1) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val tier = event.tiers[selectedTier]
    val total = tier.price * quantity

    // No containerColor: Scaffold already paints the theme's background role.
    Scaffold(
        bottomBar = {
            PurchaseBar(
                summary = "$quantity × ${tier.name}",
                total = formatMwk(total),
                onConfirmClick = { showConfirmDialog = true }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                // only bottom padding so the photo can reach the top of the screen
                .padding(bottom = innerPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
        ) {

            // ----- Photo + back button -----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                if (event.imageUrl != null) {
                    AsyncImage(
                        model = event.imageUrl,
                        contentDescription = event.artist,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (event.imageRes != null) {
                    Image(
                        painter = painterResource(id = event.imageRes),
                        contentDescription = event.artist,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder for admin-posted events (no photo yet)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = event.artist.first().toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontFamily = GrandHotel,
                            fontSize = 96.sp
                        )
                    }
                }

                // BEFORE: a Box + clip + background + clickable (about 8 lines).
                // AFTER: one M3 component. It brings the ripple, 48dp touch target and colors.
                FilledTonalIconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }

            // ----- Details -----
            Column(modifier = Modifier.padding(16.dp)) {

                Text(
                    text = event.category.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.artist,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                InfoRow(
                    icon = Icons.Filled.DateRange,
                    title = "${event.day} ${event.month}",
                    subtitle = event.time
                )
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow(
                    icon = Icons.Filled.LocationOn,
                    title = event.venue,
                    subtitle = event.location
                )

                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle("About the event")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    // Some events (and admin posts) have an empty description.
                    text = event.description.ifBlank { "More details coming soon." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle("Also performing")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    event.performers.forEach { name ->
                        // A read-only label: Surface with the secondaryContainer pair of colors.
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle("Select ticket type")
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    event.tiers.forEachIndexed { index, item ->
                        TierCard(
                            tier = item,
                            selected = index == selectedTier,
                            onClick = { selectedTier = index }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Number of tickets")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QuantityButton(
                            symbol = "−",
                            enabled = quantity > 1,
                            onClick = { quantity-- }
                        )
                        Text(
                            text = quantity.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        QuantityButton(
                            symbol = "+",
                            enabled = quantity < 10,
                            onClick = { quantity++ }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showConfirmDialog) {
        // AlertDialog already uses M3 colors (surfaceContainerHigh) and text styles,
        // so the containerColor / color overrides you had are no longer needed.
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm purchase") },
            text = {
                Text("$quantity × ${tier.name} for ${event.artist}\nTotal: ${formatMwk(total)}")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onPurchaseConfirmed(tier, quantity)
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ---------- Pieces ----------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // primaryContainer + onPrimaryContainer = the M3 "soft purple tile with readable icon" pair.
        Surface(
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TierCard(tier: TicketTier, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            // `selectable` makes the WHOLE card one radio option for the clicks AND for
            // screen readers (Role.RadioButton). The RadioButton inside gets onClick = null
            // so the tap isn't handled twice.
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tier.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    tier.perk,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatMwk(tier.price),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun QuantityButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    // FilledIconButton handles the enabled/disabled colors for you (that was your
    // `if (enabled) TicketierPurple else Color.LightGray` logic).
    FilledIconButton(onClick = onClick, enabled = enabled) {
        Text(symbol, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun PurchaseBar(summary: String, total: String, onConfirmClick: () -> Unit) {
    // tonalElevation = M3's way of showing "raised": the surface gets a faint tint of primary.
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = total,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Button(
                onClick = onConfirmClick,
                modifier = Modifier.height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Confirm Purchase", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun EventDetailsScreenPreview() {
    TicketierTheme(darkTheme = false) {
        EventDetailsScreen(event = EventRepository.events[0], onBackClick = {})
    }
}