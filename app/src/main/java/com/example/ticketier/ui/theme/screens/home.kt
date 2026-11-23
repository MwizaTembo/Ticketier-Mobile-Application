package com.example.ticketier.ui.theme.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.ticketier.data.AuthRepository
import com.example.ticketier.data.Event
import com.example.ticketier.data.EventRepository
import com.example.ticketier.data.eventCategories
import com.example.ticketier.ui.theme.TicketierTheme
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onEventClick: (Event) -> Unit = {},
    onTicketsClick: () -> Unit = {},
    onSavedClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedTab by remember { mutableStateOf(0) }

    val categories = listOf("All") + eventCategories

    // Saved (favourite) event ids for the signed-in user, loaded from Supabase.
    var favourites by remember { mutableStateOf(setOf<Int>()) }
    val scope = rememberCoroutineScope()

    // Runs when the screen opens: download the events and this user's favourites.
    LaunchedEffect(Unit) {
        EventRepository.refresh()
        favourites = AuthRepository.getFavoriteEventIds()
    }

    val filteredEvents = EventRepository.events.filter { event ->
        (selectedCategory == "All" || event.category == selectedCategory) &&
                (event.artist.contains(query, ignoreCase = true) ||
                        event.venue.contains(query, ignoreCase = true) ||
                        event.location.contains(query, ignoreCase = true))
    }

    Scaffold(
        bottomBar = {
            TicketierBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { index ->
                    when (index) {
                        0 -> selectedTab = 0
                        1 -> onTicketsClick()
                        2 -> onSavedClick()
                        3 -> onProfileClick()
                    }
                }
            )
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ticketier",
                        style = MaterialTheme.typography.displaySmall.copy(fontFamily = GrandHotel),
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { /* TODO: notifications */ }) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search artists, venues, locations") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    singleLine = true,
                    shape = CircleShape
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { category ->
                        FilterChip(
                            selected = category == selectedCategory,
                            onClick = { selectedCategory = category },
                            label = { Text(category) }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Upcoming Events",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            items(filteredEvents, key = { it.id }) { event ->
                EventCard(
                    event = event,
                    onClick = { onEventClick(event) },
                    isFavourite = event.id in favourites,
                    onToggleFavourite = {
                        scope.launch {
                            val nowSaved = AuthRepository.toggleFavorite(event.id)
                            favourites = if (nowSaved) favourites + event.id else favourites - event.id
                        }
                    }
                )
            }

            if (filteredEvents.isEmpty()) {
                item {
                    val loadError = EventRepository.error
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when {
                            EventRepository.loading -> CircularProgressIndicator()
                            loadError != null -> {
                                Text(
                                    text = loadError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                TextButton(onClick = { scope.launch { EventRepository.refresh() } }) {
                                    Text("Try again")
                                }
                            }
                            else -> Text(
                                text = "No events found. Try another search or category.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- Pieces ----------

@Composable
private fun EventCard(
    event: Event,
    onClick: () -> Unit,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.large
    ) {
        Column {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                if (event.imageUrl != null) {
                    // Picture uploaded by an admin: downloaded from Supabase Storage
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
                            fontSize = 72.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        event.day,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        event.month,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                // Heart button: save / unsave this event (shows on the Saved tab)
                IconButton(
                    onClick = onToggleFavourite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = if (isFavourite) "Remove from saved" else "Save event",
                            tint = if (isFavourite) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = event.category.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = event.artist,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = event.venue,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "From",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = event.price,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(onClick = onClick, shape = MaterialTheme.shapes.medium) {
                        Text("Get Tickets")
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketierBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf(
        "Home" to Icons.Filled.Home,
        "Tickets" to Icons.Filled.DateRange,
        "Saved" to Icons.Filled.Favorite,
        "Profile" to Icons.Filled.Person
    )

    NavigationBar {
        tabs.forEachIndexed { index, (label, icon) ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(imageVector = icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    TicketierTheme(darkTheme = false) {
        HomeScreen()
    }
}