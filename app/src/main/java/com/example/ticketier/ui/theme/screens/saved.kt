package com.example.ticketier.ui.theme.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ticketier.data.AuthRepository
import com.example.ticketier.data.EventRepository
import kotlinx.coroutines.launch

@Composable
fun SavedScreen(
    onOpenEvent: (Int) -> Unit,
    onBack: () -> Unit
) {
    var ids by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        ids = AuthRepository.getFavoriteEventIds()
    }

    LaunchedEffect(Unit) {
        refresh()
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Saved events", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        if (loading) {
            CircularProgressIndicator()
        } else {
            val events = EventRepository.events.filter { it.id in ids }
            if (events.isEmpty()) {
                Text("No saved events yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(events, key = { it.id }) { ev ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onOpenEvent(ev.id) }
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(ev.artist, style = MaterialTheme.typography.titleMedium)
                                Text("${ev.venue} • ${ev.location}")
                                Text("From ${ev.price}")
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = {
                                    scope.launch {
                                        AuthRepository.toggleFavorite(ev.id)
                                        refresh()
                                    }
                                }) { Text("Unsave") }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onBack) { Text("Back") }
    }
}