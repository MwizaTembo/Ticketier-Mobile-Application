package com.example.ticketier

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.ticketier.nav.AppNavigation
import com.example.ticketier.reminder.ReminderScheduler
import com.example.ticketier.ui.theme.TicketierTheme


class MainActivity : ComponentActivity() {

    // Android 13+ shows a pop-up asking the user to allow notifications.
    // We don't need to do anything with the answer: the reminder worker checks it again itself.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Notifications must belong to a channel, so create it as soon as the app starts.
        ReminderScheduler.createChannel(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {

            TicketierTheme() {

                AppNavigation()
            }
        }
    }
}