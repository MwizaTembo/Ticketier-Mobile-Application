package com.example.ticketier.ui.theme.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ticketier.data.Event
import com.example.ticketier.data.EventRepository
import com.example.ticketier.data.formatMwk
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onPostEvent: () -> Unit,
    onLogout: () -> Unit,
    onAddAdmin: () -> Unit = {}
) {
    // Reading the shared list here means this screen redraws when events are added/removed.
    val events = EventRepository.events
    val scope = rememberCoroutineScope()

    // Download the latest events from Supabase when the dashboard opens.
    LaunchedEffect(Unit) { EventRepository.refresh() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Admin dashboard") },
                actions = {
                    TextButton(onClick = onAddAdmin) { Text("Add admin") }
                    TextButton(onClick = onLogout) { Text("Log out") }
                }
            )
        },
        floatingActionButton = {
            // The FAB = M3's "primary action of this screen".
            ExtendedFloatingActionButton(
                onClick = onPostEvent,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Post event") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            // extra bottom space so the last card isn't hidden behind the FAB
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    if (events.isEmpty()) "No events yet. Tap “Post event” to add one."
                    else "${events.size} event(s) posted",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EventRepository.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
            items(events, key = { it.id }) { event ->
                AdminEventCard(event, onDelete = { scope.launch { EventRepository.remove(event.id) } })
            }
        }
    }
}

@Composable
private fun AdminEventCard(event: Event, onDelete: () -> Unit) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                // A small non-clickable label in the ACCENT (tertiary) container colors.
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        event.category,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(event.artist, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${event.day} ${event.month} · ${event.time}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${event.venue}, ${event.location}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    event.tiers.joinToString("  ·  ") { "${it.name} ${formatMwk(it.price)}" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete event")
            }
        }
    }
}