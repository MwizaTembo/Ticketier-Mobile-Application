package com.example.ticketier.ui.theme.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ticketier.data.AuthRepository
import kotlinx.coroutines.launch

/**
 * One screen, three stages:
 *  1) enter your email  ->  we email a code
 *  2) enter the code and a new password
 *  3) done: go back to login
 */
@Composable
fun ForgotPasswordScreen(
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    var stage by remember { mutableStateOf(1) }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Reset password",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))

            when (stage) {
                1 -> {
                    Text(
                        "Enter your account email and we'll send you a code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !loading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = authFieldColors()
                    )
                }

                2 -> {
                    Text(
                        "We sent a code to ${email.trim()}. Enter it below with your new password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(8); error = null },
                        label = { Text("Code from the email") },
                        singleLine = true,
                        enabled = !loading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = authFieldColors()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; error = null },
                        label = { Text("New password (min 6 chars)") },
                        singleLine = true,
                        enabled = !loading,
                        visualTransformation = if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) "Hide" else "Show")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = authFieldColors()
                    )
                }

                else -> {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Your password has been changed. Please log in with your new password.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            info?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))

            // ----- Main button -----
            Button(
                onClick = {
                    error = null
                    info = null
                    when (stage) {
                        1 -> {
                            if (!email.contains("@")) {
                                error = "Enter a valid email address."
                                return@Button
                            }
                            scope.launch {
                                loading = true
                                val problem = AuthRepository.sendPasswordResetCode(email)
                                loading = false
                                if (problem == null) stage = 2 else error = problem
                            }
                        }

                        2 -> {
                            if (code.length < 6) {
                                error = "Enter the code from the email."
                                return@Button
                            }
                            if (newPassword.length < 6) {
                                error = "Password must be at least 6 characters."
                                return@Button
                            }
                            scope.launch {
                                loading = true
                                val problem =
                                    AuthRepository.resetPasswordWithCode(email, code, newPassword)
                                loading = false
                                if (problem == null) stage = 3 else error = problem
                            }
                        }

                        else -> onDone()
                    }
                },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        when (stage) {
                            1 -> "Send code"
                            2 -> "Reset password"
                            else -> "Back to login"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // ----- Extra links -----
            if (stage == 2) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            val problem = AuthRepository.sendPasswordResetCode(email)
                            loading = false
                            if (problem == null) info = "A new code was sent." else error = problem
                        }
                    },
                    enabled = !loading
                ) { Text("Send the code again") }
            }

            if (stage != 3) {
                TextButton(onClick = onBack, enabled = !loading) { Text("Back to login") }
            }
        }
    }
}