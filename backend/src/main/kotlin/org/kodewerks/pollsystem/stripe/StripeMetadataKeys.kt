package org.kodewerks.pollsystem.stripe

/**
 * Checkout Session metadata keys shared between the outbound session creation
 * ([StripePaymentProvider.createGuestCheckoutSession]) and the inbound webhook
 * ([StripeWebhookService]). Pay-first registration collects phone + zipcode
 * before payment; Stripe has no field for them, so they ride in the session
 * metadata and are read back to provision a complete account.
 */
object StripeMetadataKeys {
    const val PHONE = "app_phone"
    const val ZIPCODE = "app_zipcode"
}
