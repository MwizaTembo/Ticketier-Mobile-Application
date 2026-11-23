package com.example.ticketier.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ticketier.data.AuthRepository
import com.example.ticketier.data.Event
import com.example.ticketier.data.EventRepository
import com.example.ticketier.data.TicketRepository
import com.example.ticketier.data.TicketTier
import com.example.ticketier.data.payment.FakePaymentGateway
import com.example.ticketier.data.payment.PaymentGateway
import com.example.ticketier.reminder.ReminderScheduler
import com.example.ticketier.ui.theme.screens.AddAdminScreen
import com.example.ticketier.ui.theme.screens.AdminDashboardScreen
import com.example.ticketier.ui.theme.screens.AdminLoginScreen
import com.example.ticketier.ui.theme.screens.DigitalTicketScreen
import com.example.ticketier.ui.theme.screens.EventDetailsScreen
import com.example.ticketier.ui.theme.screens.ForgotPasswordScreen
import com.example.ticketier.ui.theme.screens.HomeScreen
import com.example.ticketier.ui.theme.screens.LoginScreen
import com.example.ticketier.ui.theme.screens.MyTicketsScreen
import com.example.ticketier.ui.theme.screens.PaymentScreen
import com.example.ticketier.ui.theme.screens.PostEventScreen
import com.example.ticketier.ui.theme.screens.ProfileScreen
import com.example.ticketier.ui.theme.screens.SavedScreen
import com.example.ticketier.ui.theme.screens.SignUpScreen
import com.example.ticketier.ui.theme.screens.SplashScreen
import kotlinx.coroutines.launch

/** What the user chose on the event page, waiting for payment to succeed. */
private data class PendingPurchase(
    val event: Event,
    val tier: TicketTier,
    val quantity: Int
)

@Composable
fun AppNavigation() {

    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // The purchase waiting for payment. Kept here so the Payment screen can read it.
    var pendingPurchase by remember { mutableStateOf<PendingPurchase?>(null) }

    // Which payment provider the app uses. FakePaymentGateway needs no internet.
    // Later, swap for PayChanguGateway(<your shared Supabase client>) - nothing else changes.
    val paymentGateway: PaymentGateway = remember { FakePaymentGateway() }

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {

        composable("splash") {
            SplashScreen(
                onFinished = {
                    // Stay logged in: if Supabase saved a session, skip the login screen.
                    scope.launch {
                        val destination = AuthRepository.startDestination()
                        navController.navigate(destination) {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                }
            )
        }

        composable("login") {
            LoginScreen(
                signIn = { email, password -> AuthRepository.signInCustomer(email, password) },
                onLoginClick = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onAdminLoginClick = { navController.navigate("adminLogin") },
                onNavigateToSignUp = { navController.navigate("signup") },
                onForgotPassword = { navController.navigate("forgotPassword") }
            )
        }

        composable("forgotPassword") {
            ForgotPasswordScreen(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable("signup") {
            SignUpScreen(
                onSignedUp = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("home") {
            HomeScreen(
                onEventClick = { event ->
                    navController.navigate("ticketDetails/${event.id}")
                },
                onTicketsClick = { navController.navigate("myTickets") },
                onSavedClick = { navController.navigate("saved") },
                onProfileClick = { navController.navigate("profile") }
            )
        }

        composable("saved") {
            SavedScreen(
                onOpenEvent = { id -> navController.navigate("ticketDetails/$id") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("profile") {
            ProfileScreen(
                onSignedOut = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
                onMyTickets = { navController.navigate("myTickets") }
            )
        }

        composable(
            route = "ticketDetails/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.IntType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getInt("eventId")
            val event = EventRepository.events.find { it.id == eventId }

            if (event != null) {
                EventDetailsScreen(
                    event = event,
                    onBackClick = { navController.popBackStack() },
                    // CHANGED: no tickets are created here any more.
                    // We remember the choice and open the Payment screen first.
                    onPurchaseConfirmed = { tier, quantity ->
                        pendingPurchase = PendingPurchase(event, tier, quantity)
                        navController.navigate("payment")
                    }
                )
            }
        }

        // NEW: payment step. Tickets are created only after the payment succeeds.
        composable("payment") {
            val pending = pendingPurchase

            val context = LocalContext.current
            var email by remember { mutableStateOf("") }
            var fullName by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                email = AuthRepository.currentEmail() ?: ""
                fullName = AuthRepository.loadProfile()?.fullName ?: ""
            }

            if (pending == null) {
                // Nothing to pay for (for example the app was restarted): go back.
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                PaymentScreen(
                    amount = pending.tier.price * pending.quantity,
                    summary = "${pending.quantity} x ${pending.tier.name} - ${pending.event.artist}",
                    email = email,
                    fullName = fullName,
                    gateway = paymentGateway,
                    // Payment succeeded: create the tickets in Supabase. If that fails,
                    // the error message is shown on the Payment screen.
                    onPaid = { _ ->
                        val problem = TicketRepository.purchase(
                            pending.event, pending.tier, pending.quantity
                        )
                        if (problem == null) {
                            // WorkManager: remind the user before the event
                            ReminderScheduler.scheduleForPurchase(context, pending.event)
                            navController.navigate("myTickets") {
                                popUpTo("home")
                            }
                        }
                        problem
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
        }

        composable("myTickets") {
            MyTicketsScreen(
                onBack = { navController.popBackStack() },
                onTicketClick = { serial -> navController.navigate("ticket/$serial") },
                onBrowseEvents = { navController.popBackStack("home", inclusive = false) }
            )
        }

        composable(
            route = "ticket/{serial}",
            arguments = listOf(navArgument("serial") { type = NavType.StringType })
        ) { backStackEntry ->
            val serial = backStackEntry.arguments?.getString("serial").orEmpty()
            DigitalTicketScreen(
                serial = serial,
                onBack = { navController.popBackStack() }
            )
        }

        // ---------- Admin flow ----------

        composable("adminLogin") {
            AdminLoginScreen(
                signIn = { email, password -> AuthRepository.signInAdmin(email, password) },
                onBack = { navController.popBackStack() },
                onLoggedIn = {
                    navController.navigate("adminHome") {
                        popUpTo("login")
                    }
                }
            )
        }

        composable("adminHome") {
            AdminDashboardScreen(
                onPostEvent = { navController.navigate("adminPost") },
                onAddAdmin = { navController.navigate("adminAdd") },
                onLogout = {
                    scope.launch { AuthRepository.signOut() }
                    navController.popBackStack("login", inclusive = false)
                }
            )
        }

        composable("adminAdd") {
            AddAdminScreen(onBack = { navController.popBackStack() })
        }

        composable("adminPost") {
            PostEventScreen(
                onBack = { navController.popBackStack() },
                onPosted = { navController.popBackStack() }
            )
        }
    }
}