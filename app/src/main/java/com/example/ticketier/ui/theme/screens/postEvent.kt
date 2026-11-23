package com.example.ticketier.ui.theme.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ticketier.data.Event
import com.example.ticketier.data.EventRepository
import com.example.ticketier.data.eventCategories
import com.example.ticketier.data.tiersOf
import coil3.compose.AsyncImage
import com.example.ticketier.util.ImageUploader
import kotlinx.coroutines.launch

private val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN",
    "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEventScreen(onBack: () -> Unit, onPosted: () -> Unit) {
    var artist by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(eventCategories.first()) }
    var performers by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var earlyBird by remember { mutableStateOf("") }
    var general by remember { mutableStateOf("") }
    var vip by remember { mutableStateOf("") }
    // Errors only show AFTER the first "Post" tap, so the form doesn't start out red.
    var submitted by remember { mutableStateOf(false) }
    // Saving to Supabase takes a moment, so we track "posting" and any error.
    var posting by remember { mutableStateOf(false) }
    var postError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // The picture the admin chose (null = none). The photo picker needs no permission.
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) imageUri = uri }

    // Small helper: "Required" if we've tried to submit and the field is empty.
    fun required(value: String): String? = if (submitted && value.isBlank()) "Required" else null
    val dayNumber = day.toIntOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post new event") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Event details")
            FormField("Artist / event name", artist, { artist = it }, required(artist))

            Text("Category", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(eventCategories) { c ->
                    FilterChip(selected = c == category, onClick = { category = c }, label = { Text(c) })
                }
            }

            FormField("Other performers (separate with commas)", performers, { performers = it }, null)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField("Venue", venue, { venue = it }, required(venue), Modifier.weight(1f))
                FormField("Location", location, { location = it }, required(location), Modifier.weight(1f))
            }

            SectionTitle("Date and time")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField(
                    "Day", day, { day = it.filter(Char::isDigit).take(2) },
                    if (submitted && (dayNumber ?: 0) !in 1..31) "1 to 31" else null,
                    Modifier.weight(1f), keyboardType = KeyboardType.Number
                )
                FormField("Time (e.g. 7:00 PM)", time, { time = it }, required(time), Modifier.weight(2f))
            }
            Text("Month", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(months) { m ->
                    FilterChip(selected = m == month, onClick = { month = m }, label = { Text(m) })
                }
            }
            if (submitted && month.isBlank()) {
                Text("Select a month", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }

            FormField("Description", description, { description = it }, null, minLines = 3)

            SectionTitle("Ticket tiers (MWK)")
            // `filter(Char::isDigit)` keeps digits only; `take(7)` keeps the number safely small.
            FormField("Early Bird price", earlyBird, { earlyBird = it.filter(Char::isDigit).take(7) },
                required(earlyBird), keyboardType = KeyboardType.Number)
            FormField("General Access price", general, { general = it.filter(Char::isDigit).take(7) },
                required(general), keyboardType = KeyboardType.Number)
            FormField("VIP price", vip, { vip = it.filter(Char::isDigit).take(7) },
                required(vip), keyboardType = KeyboardType.Number)

            SectionTitle("Event image")
            OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    val chosen = imageUri
                    if (chosen != null) {
                        AsyncImage(
                            model = chosen,
                            contentDescription = "Chosen event picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            "No picture chosen (a purple placeholder is used)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !posting
                ) { Text(if (imageUri == null) "Choose picture" else "Change picture") }
                if (imageUri != null) {
                    TextButton(onClick = { imageUri = null }, enabled = !posting) { Text("Remove") }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    submitted = true
                    val allFilled = listOf(artist, venue, location, time, earlyBird, general, vip)
                        .all { it.isNotBlank() }
                    if (allFilled && month.isNotBlank() && (dayNumber ?: 0) in 1..31) {
                        val performerList = performers.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .ifEmpty { listOf("To be announced") }
                        // Save to Supabase (runs in the background, the button shows "Posting...")
                        scope.launch {
                            posting = true
                            postError = null

                            // 1) Upload the picture first (if one was chosen) to get its web address.
                            var imageUrl: String? = null
                            val picked = imageUri
                            if (picked != null) {
                                imageUrl = ImageUploader.uploadEventImage(context, picked)
                                if (imageUrl == null) {
                                    posting = false
                                    postError = "Could not upload the picture. Try again, or remove it."
                                    return@launch
                                }
                            }

                            // 2) Save the event with that address.
                            val problem = EventRepository.add(
                                artist = artist.trim(),
                                category = category,
                                day = day.padStart(2, '0'),   // "5" -> "05", like your sample data
                                month = month,
                                time = time.trim(),
                                venue = venue.trim(),
                                location = location.trim(),
                                description = description.trim(),
                                performers = performerList,
                                earlyBird = earlyBird.toInt(),
                                general = general.toInt(),
                                vip = vip.toInt(),
                                imageUrl = imageUrl
                            )
                            posting = false
                            if (problem == null) onPosted() else postError = problem
                        }
                    }
                },
                enabled = !posting,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(55.dp)
            ) {
                Text(
                    if (posting) "Posting..." else "Post event",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            postError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

// CONCEPT: one reusable composable instead of a dozen copies of the same TextField setup.
// `error` is the message to show (null = no error).
@Composable
private fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        singleLine = minLines == 1,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    )
}