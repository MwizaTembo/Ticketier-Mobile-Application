
package com.example.ticketier.data.payment

import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// The "contract" every payment provider must follow. Screens only talk to this
// interface, so the fake gateway and the PayChangu gateway are swappable.
// ---------------------------------------------------------------------------

enum class PaymentMethod(val label: String) {
    AIRTEL_MONEY("Airtel Money"),
    TNM_MPAMBA("TNM Mpamba"),
    CARD("Card")
}

data class PaymentRequest(
    val amount: Int,                 // in MWK
    val method: PaymentMethod,
    val phone: String = "",          // mobile money number (fake gateway only)
    val cardNumber: String = "",     // card number (fake gateway only)
    val email: String = "",
    val fullName: String = ""
)

sealed interface PaymentResult {
    data class Success(val reference: String) : PaymentResult
    data class Failed(val message: String) : PaymentResult
}

// What happens after the user taps Pay:
//  - Finished: the gateway already knows the answer (fake gateway).
//  - NeedsCheckout: the user must finish on a hosted page, then we verify (PayChangu).
sealed interface PaymentStart {
    data class Finished(val result: PaymentResult) : PaymentStart
    data class NeedsCheckout(val checkoutUrl: String, val reference: String) : PaymentStart
}

interface PaymentGateway {
    /** true = the provider shows its own page, so we don't collect phone/card here. */
    val usesHostedCheckout: Boolean

    suspend fun start(request: PaymentRequest): PaymentStart

    /** Ask the provider whether [reference] was really paid. */
    suspend fun verify(reference: String, amount: Int): PaymentResult
}

// ---------------------------------------------------------------------------
// Fake gateway: no network. Follows PayChangu's published test numbers so the
// demo behaves the same as the real test mode.
// ---------------------------------------------------------------------------
class FakePaymentGateway : PaymentGateway {

    override val usesHostedCheckout = false

    override suspend fun start(request: PaymentRequest): PaymentStart {
        delay(2000) // pretend the gateway is thinking
        val reference = "FAKE-" + System.currentTimeMillis()

        val result: PaymentResult = when (request.method) {
            PaymentMethod.AIRTEL_MONEY -> mobileMoney(
                number = request.phone,
                success = "990000000",
                failed = "990000001",
                provider = "Airtel Money",
                reference = reference
            )

            PaymentMethod.TNM_MPAMBA -> mobileMoney(
                number = request.phone,
                success = "899817565",
                failed = "899817566",
                provider = "TNM Mpamba",
                reference = reference
            )

            PaymentMethod.CARD -> card(request.cardNumber, reference)
        }
        return PaymentStart.Finished(result)
    }

    override suspend fun verify(reference: String, amount: Int): PaymentResult =
        PaymentResult.Success(reference)

    private fun mobileMoney(
        number: String,
        success: String,
        failed: String,
        provider: String,
        reference: String
    ): PaymentResult {
        // PayChangu test numbers are entered without the leading 0
        return when (number.trim().removePrefix("0")) {
            success -> PaymentResult.Success(reference)
            failed -> PaymentResult.Failed("$provider payment failed. Please try again.")
            else -> PaymentResult.Failed(
                "Not a test number. Use $success (success) or $failed (failure)."
            )
        }
    }

    private fun card(cardNumber: String, reference: String): PaymentResult {
        return when (cardNumber.filter { it.isDigit() }) {
            "4242424242424242", "5555555555554444" -> PaymentResult.Success(reference)
            "4000000000000002" -> PaymentResult.Failed("Card declined.")
            "4000000000009995" -> PaymentResult.Failed("Insufficient funds.")
            "4000000000003220" -> PaymentResult.Failed("Payment timed out. Please try again.")
            "5200000000000008" -> PaymentResult.Failed("Card error. Please try another card.")
            else -> PaymentResult.Failed("Not a test card. Try 4242 4242 4242 4242.")
        }
    }
}