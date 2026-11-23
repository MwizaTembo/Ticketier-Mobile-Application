package com.example.ticketier.ui.theme.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ticketier.data.payment.PaymentGateway
import com.example.ticketier.data.payment.PaymentMethod
import com.example.ticketier.data.payment.PaymentRequest
import com.example.ticketier.data.payment.PaymentResult
import com.example.ticketier.data.payment.PaymentStart
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    amount: Int,                       // total in MWK
    summary: String,                   // e.g. "3 x Early Bird - Coachella"
    email: String,
    fullName: String,
    gateway: PaymentGateway,
    // Runs after payment succeeded. Returns an error message, or null if everything worked.
    onPaid: suspend (reference: String) -> String?,
    onCancel: () -> Unit
) {
    var method by remember { mutableStateOf(PaymentMethod.AIRTEL_MONEY) }
    var phone by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Set when the user was sent to PayChangu's page and must come back to confirm
    var pendingReference by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val hosted = gateway.usesHostedCheckout
    val total = "MWK " + String.format(Locale.US, "%,d", amount)

    suspend fun handle(result: PaymentResult) {
        when (result) {
            is PaymentResult.Success -> error = onPaid(result.reference)
            is PaymentResult.Failed -> error = result.message
        }
    }

    fun inputProblem(): String? {
        if (hosted) return null // PayChangu collects the details on its own page
        return when (method) {
            PaymentMethod.CARD ->
                if (cardNumber.filter { it.isDigit() }.length < 12) "Enter a valid card number." else null

            else ->
                if (phone.trim().removePrefix("0").length != 9) "Enter your 9-digit mobile number (without the 0)." else null
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text("Payment", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                total,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(24.dp))
            Text("Pay with", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethod.values().forEach { m ->
                    FilterChip(
                        selected = method == m,
                        onClick = { method = m; error = null },
                        enabled = !processing && pendingReference == null,
                        label = { Text(m.label) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (hosted) {
                Text(
                    "You will finish the payment on PayChangu's secure page, then come back here and confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (method == PaymentMethod.CARD) {
                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { cardNumber = it; error = null },
                    label = { Text("Card number") },
                    singleLine = true,
                    enabled = !processing,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = authFieldColors()
                )
            } else {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; error = null },
                    label = { Text("Mobile number (without 0)") },
                    singleLine = true,
                    enabled = !processing,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = authFieldColors()
                )
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))

            if (pendingReference == null) {
                Button(
                    onClick = {
                        val problem = inputProblem()
                        if (problem != null) {
                            error = problem
                            return@Button
                        }
                        scope.launch {
                            processing = true
                            error = null
                            val started = gateway.start(
                                PaymentRequest(
                                    amount = amount,
                                    method = method,
                                    phone = phone,
                                    cardNumber = cardNumber,
                                    email = email,
                                    fullName = fullName
                                )
                            )
                            when (started) {
                                is PaymentStart.Finished -> handle(started.result)
                                is PaymentStart.NeedsCheckout -> {
                                    pendingReference = started.reference
                                    uriHandler.openUri(started.checkoutUrl)
                                }
                            }
                            processing = false
                        }
                    },
                    enabled = !processing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (processing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Pay $total", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                // The user went to PayChangu's page. Confirm after they come back.
                Button(
                    onClick = {
                        val ref = pendingReference ?: return@Button
                        scope.launch {
                            processing = true
                            error = null
                            handle(gateway.verify(ref, amount))
                            processing = false
                        }
                    },
                    enabled = !processing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (processing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("I've completed payment", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { pendingReference = null; error = null },
                    enabled = !processing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text("Start over", style = MaterialTheme.typography.titleMedium) }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onCancel,
                enabled = !processing,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("Cancel") }
        }
    }
}