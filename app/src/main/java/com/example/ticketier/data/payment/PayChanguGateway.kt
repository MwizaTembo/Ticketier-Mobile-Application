package com.example.ticketier.data.payment

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.serialization.Serializable

// Requests/responses for our two Edge Functions. The PayChangu SECRET key never
// appears here: it lives only in Supabase (Edge Functions > Secrets).

@Serializable
internal data class CreatePaymentBody(
    val amount: Int,
    val email: String,
    val first_name: String,
    val last_name: String
)

@Serializable
internal data class CreatePaymentResponse(
    val checkout_url: String? = null,
    val tx_ref: String? = null,
    val error: String? = null
)

@Serializable
internal data class VerifyPaymentBody(
    val tx_ref: String,
    val amount: Int
)

@Serializable
internal data class VerifyPaymentResponse(
    val paid: Boolean = false,
    val status: String? = null,
    val error: String? = null
)

/**
 * Real gateway using PayChangu test mode (or live mode later, by changing the
 * secret key in Supabase - no app change needed).
 *
 * Pass in the app's shared Supabase client, so the user's sign-in token is
 * sent to the Edge Functions automatically.
 */
class PayChanguGateway(private val client: SupabaseClient) : PaymentGateway {

    override val usesHostedCheckout = true

    override suspend fun start(request: PaymentRequest): PaymentStart {
        return try {
            val parts = request.fullName.trim().split(" ").filter { it.isNotBlank() }
            val firstName = parts.firstOrNull() ?: "Customer"
            val lastName = parts.drop(1).joinToString(" ").ifBlank { "Ticketier" }

            val response = client.functions.invoke(
                function = "create-payment",
                body = CreatePaymentBody(
                    amount = request.amount,
                    email = request.email,
                    first_name = firstName,
                    last_name = lastName
                )
            ).body<CreatePaymentResponse>()

            val url = response.checkout_url
            val ref = response.tx_ref
            if (url != null && ref != null) {
                PaymentStart.NeedsCheckout(url, ref)
            } else {
                PaymentStart.Finished(
                    PaymentResult.Failed(response.error ?: "Could not start the payment.")
                )
            }
        } catch (e: Exception) {
            PaymentStart.Finished(
                PaymentResult.Failed("Could not reach the payment server. Check your internet and try again.")
            )
        }
    }

    override suspend fun verify(reference: String, amount: Int): PaymentResult {
        return try {
            val response = client.functions.invoke(
                function = "verify-payment",
                body = VerifyPaymentBody(tx_ref = reference, amount = amount)
            ).body<VerifyPaymentResponse>()

            if (response.paid) {
                PaymentResult.Success(reference)
            } else {
                PaymentResult.Failed(
                    response.error
                        ?: "Payment not confirmed yet. If you have paid, wait a few seconds and tap the button again."
                )
            }
        } catch (e: Exception) {
            PaymentResult.Failed("Could not verify the payment. Check your internet and try again.")
        }
    }
}