package com.example.ticketier.ui.theme.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ticketier.data.AuthRepository
import com.example.ticketier.data.TicketRepository
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onSignedOut: () -> Unit,
    onBack: () -> Unit,
    onMyTickets: () -> Unit = {}
) {
    var email by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        email = AuthRepository.currentEmail()
        val p = AuthRepository.loadProfile()
        name = p?.fullName ?: ""
        loading = false
        TicketRepository.refresh() // so the ticket count below is up to date
    }

    // Surface paints the theme background AND sets the default text color,
    // which fixes the white screen / invisible text.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                "Profile",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))

            Text(
                "Email",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                email ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))

            // Purchased tickets are stored in Supabase, so they are always here.
            Text(
                "Purchased tickets",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${TicketRepository.tickets.size} ticket(s)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = onMyTickets) { Text("View my tickets") }
            Spacer(Modifier.height(8.dp))

            if (loading) {
                CircularProgressIndicator()
            } else {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; message = null },
                    label = { Text("Full name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = authFieldColors()
                )
                Spacer(Modifier.height(8.dp))
                message?.let {
                    Text(
                        it,
                        color = if (messageIsError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val ok = AuthRepository.updateFullName(name.trim())
                            messageIsError = !ok
                            message = if (ok) "Saved." else "Could not save."
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text("Save", style = MaterialTheme.typography.titleMedium) }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { scope.launch { AuthRepository.signOut(); onSignedOut() } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text("Sign out", style = MaterialTheme.typography.titleMedium) }
            }

            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Back")
            }
        }
    }
}