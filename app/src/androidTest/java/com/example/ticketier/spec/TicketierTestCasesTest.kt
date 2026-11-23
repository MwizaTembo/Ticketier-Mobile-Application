package com.example.ticketier.spec

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ticketier.TicketierTestRule
import com.example.ticketier.data.Event
import com.example.ticketier.data.EventRepository
import com.example.ticketier.data.ScanResult
import com.example.ticketier.data.Ticket
import com.example.ticketier.data.TicketRepository
import com.example.ticketier.fillPostEventForm
import com.example.ticketier.nav.AppNavigation
import com.example.ticketier.tapPostEvent
import com.example.ticketier.ui.theme.TicketierTheme
import com.example.ticketier.util.generateQrBitmap
import com.example.ticketier.waitForText
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Automated version of the 16 test cases in "Test Cases for Ticketier" (tickets.pdf).
 * Every test is named after the document's Test Case ID, so a line in the test report
 * maps straight to a test case in the document.
 *
 * They drive the REAL app (splash -> login -> ...) like a user would.
 *
 * Where the document and the app disagree on wording or data, the constants in `Spec`
 * below hold what the APP does today, with the document's wording in a comment.
 * Either fix the document, or change the app and the constant (one line each).
 */
class TicketierTestCasesTest {

    @get:Rule(order = 0) val repos = TicketierTestRule()
    @get:Rule(order = 1) val rule = createComposeRule()

    private object Spec {
        const val CUSTOMER_EMAIL = "user@test.com"        // document says user@example.com
        const val CUSTOMER_PASSWORD = "user123"
        const val WRONG_PASSWORD = "WrongPass1"
        const val LOGIN_ERROR = "Incorrect email or password."   // document: Invalid email or password
        const val ADMIN_EMAIL = "admin@test.com"
        const val ADMIN_PASSWORD = "1234"
        const val ADMIN_DENIED = "Access denied: admins only"
        const val GENRE_CHIP = "IndiePop"                 // document says Indiepop
        const val ADMIN_BUTTON = "Log in as admin"        // document says Login as admin
    }

    // ------------------------------------------------------------------ helpers

    private fun launchApp(owner: ActivityResultRegistryOwner? = null) {
        rule.setContent {
            TicketierTheme(darkTheme = false) {
                if (owner == null) {
                    AppNavigation()
                } else {
                    CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                        AppNavigation()
                    }
                }
            }
        }
        rule.waitForText("Login")       // the splash screen lasts about 2 seconds
    }

    private fun logInAsCustomer(email: String = Spec.CUSTOMER_EMAIL, password: String = Spec.CUSTOMER_PASSWORD) {
        rule.onNodeWithText("Email").performTextInput(email)
        rule.onNodeWithText("Password").performTextInput(password)
        rule.onNodeWithText("Login").performClick()
    }

    private fun launchAndLogIn(owner: ActivityResultRegistryOwner? = null) {
        launchApp(owner)
        logInAsCustomer()
        rule.waitForText("Upcoming Events")
    }

    private fun openAdminSignIn() {
        rule.onNodeWithText(Spec.ADMIN_BUTTON).performClick()
        rule.waitForText("Admin sign in")
    }

    private fun adminSignIn(email: String, password: String) {
        rule.onNodeWithText("Admin email").performTextInput(email)
        rule.onNodeWithText("Password").performTextInput(password)
        rule.onNodeWithText("Sign in as admin").performClick()
    }

    private fun launchAsAdmin() {
        launchApp()
        openAdminSignIn()
        adminSignIn(Spec.ADMIN_EMAIL, Spec.ADMIN_PASSWORD)
        rule.waitForText("Admin dashboard")
    }

    private fun scrollChipsTo(chip: String) {
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .performScrollToNode(hasText(chip))
    }

    private fun scrollHomeListTo(text: String) {
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(text))
    }

    private fun openEventFromHome(name: String) {
        scrollHomeListTo(name)
        rule.onNodeWithText(name).performClick()
        rule.waitForText("About the event")
    }

    private fun searchBar() = rule.onNode(hasSetTextAction())

    /** Event page: pick a tier, set the quantity, confirm the purchase. Ends on My tickets. */
    private fun buyFromEventPage(tier: String, quantity: Int) {
        rule.onNodeWithText(tier).performScrollTo().performClick()
        repeat(quantity - 1) { rule.onNodeWithText("+").performScrollTo().performClick() }
        rule.onNodeWithText("Confirm Purchase").performClick()
        rule.onNodeWithText("Confirm").performClick()
        rule.waitForText("My tickets")
    }

    private fun tapTicketsTab() {
        rule.onNodeWithText("Tickets").performClick()
        rule.waitForText("My tickets")
    }

    private fun event(artist: String): Event = EventRepository.events.first { it.artist == artist }

    /**
     * NEW: buys one ticket and returns the newly created Ticket.
     * purchase() is suspend and returns an error String (or null), so we run it in a
     * coroutine and read the created ticket back from TicketRepository.tickets.
     */
    private fun purchaseOne(event: Event, tierIndex: Int = 0): Ticket = runBlocking {
        val before = TicketRepository.tickets.map { it.serial }.toSet()
        val err = TicketRepository.purchase(event, event.tiers[tierIndex], 1)
        check(err == null) { "purchase failed: $err" }
        TicketRepository.tickets.first { it.serial !in before }
    }

    private fun decodeQr(bitmap: Bitmap): String {
        val pad = 80    // white border = the "quiet zone" the app shows around the code
        val padded = Bitmap.createBitmap(bitmap.width + 2 * pad, bitmap.height + 2 * pad, Bitmap.Config.ARGB_8888)
        Canvas(padded).apply { drawColor(Color.WHITE); drawBitmap(bitmap, pad.toFloat(), pad.toFloat(), null) }
        val pixels = IntArray(padded.width * padded.height)
        padded.getPixels(pixels, 0, padded.width, 0, 0, padded.width, padded.height)
        val source = RGBLuminanceSource(padded.width, padded.height, pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    // ------------------------------------------------------------------ TC 1 - 4: authentication

    /** TEST CASE 1: Successful Customer Login */
    @Test fun AUTH_LOGIN_001_successfulCustomerLogin() {
        launchApp()
        logInAsCustomer()
        rule.waitForText("Upcoming Events")

        rule.onNodeWithText("Upcoming Events").assertExists()               // upcoming events
        searchBar().assertExists()                                          // the search bar
        rule.onNodeWithText("All").assertExists()                           // the categories bar
        listOf("Home", "Tickets", "Saved", "Profile")                       // bottom navigation
            .forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithText(Spec.ADMIN_BUTTON).assertDoesNotExist()         // we have left the login screen
    }

    /** TEST CASE 2: Login with Incorrect Password */
    @Test fun AUTH_LOGIN_002_loginWithIncorrectPassword() {
        launchApp()
        logInAsCustomer(password = Spec.WRONG_PASSWORD)

        rule.onNodeWithText(Spec.LOGIN_ERROR).assertExists()
        rule.onNodeWithText("Login").assertExists()                         // still on the login screen
        rule.onNodeWithText("Upcoming Events").assertDoesNotExist()
    }

    /** TEST CASE 3: Successful Administrator Login */
    @Test fun AUTH_ADMIN_001_successfulAdministratorLogin() {
        launchApp()
        openAdminSignIn()
        adminSignIn(Spec.ADMIN_EMAIL, Spec.ADMIN_PASSWORD)
        rule.waitForText("Admin dashboard")

        rule.onNodeWithText("Admin dashboard").assertExists()
        rule.onNodeWithText("Post event").assertExists()                    // where events can be created and posted
    }

    /** TEST CASE 4: Customer Credentials Rejected on Admin Login */
    @Test fun AUTH_ADMIN_002_customerCredentialsRejectedOnAdminLogin() {
        launchApp()
        openAdminSignIn()
        adminSignIn(Spec.CUSTOMER_EMAIL, Spec.CUSTOMER_PASSWORD)

        rule.onNodeWithText(Spec.ADMIN_DENIED).assertExists()
        rule.onNodeWithText("Admin sign in").assertExists()                 // user stays on the sign-in screen
        rule.onNodeWithText("Admin dashboard").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ TC 5 - 7: search

    /** TEST CASE 5: Search Events by Genre (document ID EVT_SEARCH_001) */
    @Test fun EVT_SEARCH_001_searchEventsByGenre() {
        launchAndLogIn()
        scrollChipsTo(Spec.GENRE_CHIP)
        rule.onNodeWithText(Spec.GENRE_CHIP).performClick()

        rule.onNodeWithText("Lana del rey").assertExists()                  // the IndiePop event
        rule.onAllNodesWithText("Get Tickets").assertCountEquals(1)         // ONLY IndiePop events
        rule.onNodeWithText("The Neighbourhood").assertDoesNotExist()
        rule.onNodeWithText(Spec.GENRE_CHIP).assertIsSelected()             // filter stays active ...

        scrollChipsTo("All")                                                // ... until it is cleared
        rule.onNodeWithText("All").performClick()
        rule.onNodeWithText("The Neighbourhood").assertExists()
    }

    /** TEST CASE 6: Search Events by Location (the document reuses ID EVT_SEARCH_001, should be 002) */
    @Test fun EVT_SEARCH_002_searchEventsByLocation() {
        launchAndLogIn()
        searchBar().performTextInput("Blantyre")

        rule.onNodeWithText("Lana del rey").assertExists()                  // the only event in Blantyre
        rule.onAllNodesWithText("Get Tickets").assertCountEquals(1)
        rule.onNodeWithText("The Neighbourhood").assertDoesNotExist()       // a Lilongwe event

        searchBar().performTextClearance()                                  // search stays until cleared
        rule.onNodeWithText("The Neighbourhood").assertExists()
    }

    /** TEST CASE 7: Search with No Matching Events */
    @Test fun EVT_SEARCH_003_searchWithNoMatchingEvents() {
        launchAndLogIn()
        searchBar().performTextInput("wwwwwwww")

        rule.onNode(hasText("No events found", substring = true)).assertExists()
        rule.onAllNodesWithText("Get Tickets").assertCountEquals(0)         // no event cards
    }

    // ------------------------------------------------------------------ TC 8 - 9: event page and purchase

    /** TEST CASE 8: View Event Details */
    @Test fun EVT_DETAIL_001_viewEventDetails() {
        launchAndLogIn()
        openEventFromHome("Coachella Festival")

        rule.onNodeWithText("Coachella Festival").assertExists()            // name
        rule.onNodeWithText("12 DEC").assertExists()                        // date
        rule.onNodeWithText("12:00 PM").assertExists()                      // time
        listOf("Sabrina Carpenter", "Justin Beiber", "The Strokes", "and many more")
            .forEach { rule.onNodeWithText(it).assertExists() }             // performing artists
        rule.onNodeWithText("Lakeshore Grounds").assertExists()             // venue
        rule.onNodeWithContentDescription("Coachella Festival").assertExists()   // event image
        rule.onNodeWithText(
            "A full day of music by the lake with multiple artists, food stalls and activities for the whole family."
        ).assertExists()                                                    // description
        listOf("Early Bird", "General Access", "VIP").forEach { rule.onNodeWithText(it).assertExists() }

        rule.onNodeWithContentDescription("Back").performClick()            // can navigate back home
        rule.waitForText("Upcoming Events")
    }

    /**
     * TEST CASE 9: Total Cost Calculated for Selected Quantity
     * The document expects MWK 45,000 (3 x 15,000), but Coachella's Early Bird price in the
     * app is MWK 30,000, so 3 tickets = MWK 90,000. (15,000 is The Neighbourhood's price.)
     */
    @Test fun PUR_QTY_001_totalCostForSelectedQuantity() {
        launchAndLogIn()
        openEventFromHome("Coachella Festival")

        rule.onNodeWithText("Early Bird").performScrollTo().performClick()
        repeat(2) { rule.onNodeWithText("+").performScrollTo().performClick() }

        rule.onNodeWithText("3 \u00D7 Early Bird").assertExists()
        // MWK 90,000 shows twice: the VIP tier price and the total in the purchase bar
        rule.onAllNodesWithText("MWK 90,000").assertCountEquals(2)

        // post-condition: quantity and total are kept for the purchase
        rule.onNodeWithText("Confirm Purchase").performClick()
        rule.onNode(hasText("Total: MWK 90,000", substring = true)).assertExists()
        rule.onNodeWithText("Confirm").performClick()
        rule.waitForText("My tickets")
        assertEquals(3, TicketRepository.tickets.size)
        assertTrue(TicketRepository.tickets.all { it.tierName == "Early Bird" && it.price == 30000 })
    }

    // ------------------------------------------------------------------ TC 10 - 13: tickets

    /** TEST CASE 10: Digital Ticket Generated After Purchase */
    @Test fun TKT_GEN_001_digitalTicketGeneratedAfterPurchase() {
        launchAndLogIn()
        openEventFromHome("Coachella Festival")
        buyFromEventPage(tier = "Early Bird", quantity = 1)

        val ticket = TicketRepository.tickets.single()
        rule.onNodeWithText(ticket.serial).performClick()                   // open it from My tickets
        rule.waitForText("Your ticket")

        rule.onNodeWithText("Coachella Festival").assertExists()            // event name
        rule.onNodeWithText("12 DEC \u00B7 12:00 PM").assertExists()        // date and time
        rule.onNodeWithText("Early Bird").assertExists()                    // ticket tier
        rule.onNodeWithContentDescription("QR code for ticket ${ticket.serial}").assertExists()
        rule.onNodeWithText(ticket.serial).assertExists()                   // serial number

        // the serial is unique and well formed ...
        assertTrue(Regex("^TKT-\\d{3}-[A-HJKMNP-Z2-9]{8}$").matches(ticket.serial))
        assertEquals(1, TicketRepository.tickets.count { it.serial == ticket.serial })
        // ... and the QR code really contains it (decoded back with a QR reader)
        assertEquals(ticket.serial, decodeQr(generateQrBitmap(ticket.serial)))
        // post-condition: saved with status Valid
        rule.onNodeWithText("VALID").assertExists()
        assertFalse(ticket.used)
    }

    /** TEST CASE 11: Scan a Valid Ticket for the First Time */
    @Test fun TKT_QR_001_scanValidTicketForTheFirstTime() {
        val coachella = event("Coachella Festival")
        val ticket = purchaseOne(coachella, tierIndex = 0)
        launchAndLogIn()
        tapTicketsTab()
        rule.onNodeWithText(ticket.serial).performClick()
        rule.waitForText("Your ticket")
        rule.onNodeWithText("VALID").assertExists()

        // The app has no real scanner yet. The demo button does what the scanner does.
        rule.onNodeWithText("Demo: simulate a gate scan").performScrollTo().performClick()

        rule.onAllNodesWithText("USED").assertCountEquals(2)                // status chip and stamp over the QR
        assertTrue(TicketRepository.find(ticket.serial)!!.used)             // post-condition: Used
    }

    /** TEST CASE 12: Scan the Same Ticket Twice */
    @Test fun TKT_QR_002_scanTheSameTicketTwice() {
        val coachella = event("Coachella Festival")
        val ticket = purchaseOne(coachella, tierIndex = 0)
        // prerequisite: already scanned
        assertEquals(ScanResult.ACCEPTED, runBlocking { TicketRepository.scan(ticket.serial) })

        launchAndLogIn()
        tapTicketsTab()
        rule.onNodeWithText(ticket.serial).performClick()
        rule.waitForText("Your ticket")
        rule.onAllNodesWithText("USED").assertCountEquals(2)
        rule.onNodeWithText("Demo: simulate a gate scan").assertDoesNotExist()

        // scanning the QR again is rejected, and the status stays Used
        assertEquals(ScanResult.ALREADY_USED, runBlocking { TicketRepository.scan(ticket.serial) })
        assertTrue(TicketRepository.find(ticket.serial)!!.used)
    }

    /** TEST CASE 13: Viewing My Tickets */
    @Test fun TKT_VIEW_001_viewingMyTickets() {
        val coachella = event("Coachella Festival")
        val neighbourhood = event("The Neighbourhood")
        val first = purchaseOne(coachella, tierIndex = 0)
        val second = purchaseOne(neighbourhood, tierIndex = 1)

        launchAndLogIn()
        tapTicketsTab()
        rule.onNodeWithText(first.serial).assertExists()                    // the list shows both tickets
        rule.onNodeWithText(second.serial).assertExists()

        rule.onNodeWithText(second.serial).performClick()                   // select one ticket
        rule.waitForText("Your ticket")
        rule.onNodeWithText("The Neighbourhood").assertExists()             // event details
        rule.onNodeWithContentDescription("QR code for ticket ${second.serial}").assertExists()
        rule.onNodeWithText(second.serial).assertExists()                   // serial number

        rule.onNodeWithContentDescription("Back").performClick()           // back to the list
        rule.waitForText("My tickets")
        rule.onNodeWithContentDescription("Back").performClick()           // back to the home screen
        rule.waitForText("Upcoming Events")
    }

    // ------------------------------------------------------------------ TC 14 - 15: admin

    /**
     * TEST CASE 14: Administrator Creates a New Event
     * Differences from the document: the date is entered as Day 15 + month DEC (not 15/12/26),
     * and step 3 (add an event image) is not possible yet: the app shows "Image upload coming soon".
     */
    @Test fun ADM_CREATE_001_administratorCreatesANewEvent()  {
        runBlocking {
        launchAsAdmin()
        rule.onNodeWithText("Post event").performClick()
        rule.waitForText("Post new event")

        rule.onNodeWithText("Bands").performClick()                         // genre
        rule.fillPostEventForm(
            artist = "Gorillaz", venue = "Blantyre Sports Club", location = "Blantyre",
            day = "15", month = "DEC", time = "18:00",
            earlyBird = "50000", general = "65000", vip = "100000", performers = "n/a"
        )
        rule.onNodeWithText("Image upload coming soon").performScrollTo().assertExists()   // known gap
        rule.tapPostEvent()
        rule.waitForText("Admin dashboard")

        rule.onNodeWithText("Gorillaz").assertExists()                      // shown on the admin home screen
        val posted = EventRepository.events.first()                         // ... at the top of the list
        assertEquals("Gorillaz", posted.artist)
        assertEquals("Bands", posted.category)
        assertEquals("Blantyre Sports Club", posted.venue)
        assertEquals("Blantyre", posted.location)
        assertEquals("15", posted.day)
        assertEquals("DEC", posted.month)
        assertEquals("18:00", posted.time)
        assertEquals(listOf("n/a"), posted.performers)
        assertEquals(listOf(50000, 65000, 100000), posted.tiers.map { it.price })

        // post-condition: the event is visible to customers
        rule.onNodeWithText("Log out").performClick()
        rule.waitForText("Login")
        logInAsCustomer()
        rule.waitForText("Upcoming Events")
        searchBar().performTextInput("Gorillaz")
        rule.onNodeWithText("Gorillaz").assertExists()
    } }

    /** TEST CASE 15: Create an Event with Missing Required Fields */
    @Test fun ADM_CREATE_002_createEventWithMissingRequiredFields() {
        launchAsAdmin()
        val eventsBefore = EventRepository.events.size
        rule.onNodeWithText("Post event").performClick()
        rule.waitForText("Post new event")

        rule.tapPostEvent()                                                 // everything left empty

        // "Required" under name, venue, location, time and the three prices
        rule.onAllNodesWithText("Required").assertCountEquals(7)
        // the date fields show their own messages instead of "Required"
        rule.onNodeWithText("1 to 31").assertExists()
        rule.onNodeWithText("Select a month").assertExists()
        assertEquals(eventsBefore, EventRepository.events.size)             // no event is created
    }

    // ------------------------------------------------------------------ TC 16: download

    /**
     * TEST CASE 16: Download a Ticket
     * The system "Save as" screen cannot be driven by a Compose test, so this test replaces it
     * with a fake that "chooses" a file in the app's cache folder. Everything else is real:
     * the button, the PDF writer, the saved file and the "Ticket saved" message.
     */
    @Test fun TKT_DL_001_downloadATicket() {
        val target = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "TKT_DL_001.pdf")
        target.delete()

        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                @Suppress("UNCHECKED_CAST")
                dispatchResult(requestCode, Uri.fromFile(target) as O)
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }

        launchAndLogIn(owner)
        openEventFromHome("Coachella Festival")                             // 1. purchase a ticket
        buyFromEventPage(tier = "Early Bird", quantity = 1)
        rule.onNodeWithContentDescription("Back").performClick()            // 2. Tickets tab on the bottom bar
        rule.waitForText("Upcoming Events")
        tapTicketsTab()
        val ticket = TicketRepository.tickets.single()
        rule.onNodeWithText(ticket.serial).performClick()                   // 3. open the valid ticket
        rule.waitForText("Your ticket")

        rule.onNodeWithText("Download ticket (PDF)").performScrollTo().performClick()   // 4-6. download, location, save
        rule.waitForText("Ticket saved as PDF")                             // the pop-up at the bottom

        assertTrue("The PDF was not written", target.exists() && target.length() > 0)  // post-condition
        assertEquals("%PDF", String(target.readBytes(), 0, 4))
    }}
