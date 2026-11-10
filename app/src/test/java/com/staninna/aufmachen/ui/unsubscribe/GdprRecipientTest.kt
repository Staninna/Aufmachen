package com.staninna.aufmachen.ui.unsubscribe

import org.junit.Assert.assertEquals
import org.junit.Test

class GdprRecipientTest {
    @Test fun usesSavedContactWhenItIsAnAddress() {
        assertEquals("dpo@shop.example" to false, gdprRecipient("shop.example", " dpo@shop.example "))
    }

    @Test fun guessesPrivacyAddressOtherwise() {
        assertEquals("privacy@shop.example" to true, gdprRecipient("shop.example", null))
        assertEquals("privacy@shop.example" to true, gdprRecipient("shop.example", "https://shop.example/privacy"))
    }
}
