package com.staninna.aufmachen.ui.detail

import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.model.WhyPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailLogicTest {
    @Test fun serviceLabelStripsTld() {
        assertEquals("example", serviceLabel("example.co.uk"))
        assertEquals("my-shop", serviceLabel("My-Shop.com"))
        assertEquals("service", serviceLabel("..."))
    }

    @Test fun ownAddress() {
        assertEquals("example@mail.example.org", ownAddressSuggestion("Example.com", "mail.example.org"))
    }

    @Test fun addressValidation() {
        assertTrue(looksLikeAddress(" privacy@example.com "))
        assertFalse(looksLikeAddress("privacy@example"))
        assertFalse(looksLikeAddress("no spaces@example.com"))
        assertFalse(looksLikeAddress(""))
    }

    @Test fun whyRowsFilterAndSort() {
        val rows = whyRows(listOf(WhyPart("a", 0.4), WhyPart("b", -12.0), WhyPart("c", 40.0), WhyPart("d", 3.0)))
        assertEquals(listOf("c", "b", "d"), rows.map { it.key })
        assertEquals("+40", signedPoints(40.0))
        assertEquals("-12", signedPoints(-12.0))
    }

    @Test fun stepsToggle() {
        assertEquals(listOf(0, 2), toggleStep(setOf(2), 0, true))
        assertEquals(listOf(2), toggleStep(setOf(0, 2), 0, false))
    }

    @Test fun sparkFillsGaps() {
        val bars = sparkBars(mapOf(2020 to 10, 2023 to 5))
        assertEquals(listOf(2020, 2021, 2022, 2023), bars.map { it.first })
        assertEquals(1f, bars[0].second, 0f)
        assertEquals(0f, bars[1].second, 0f)
        assertEquals(0.5f, bars[3].second, 0f)
    }

    @Test fun whySummaryTakesTopTwo() {
        val parts = listOf(WhyPart("billing", 10.0), WhyPart("Finance", 40.0), WhyPart("x", 3.0), WhyPart("none", 0.2))
        assertEquals("Finance +40, billing +10", whySummary(parts))
        assertEquals("", whySummary(emptyList()))
    }

    @Test fun nextStepFromState() {
        assertEquals(NextStep.ASK, nextStep(Status.TODO, false, false))
        assertEquals(NextStep.STEPS, nextStep(Status.TODO, false, true))
        assertEquals(NextStep.ADD_PROOF, nextStep(Status.CHANGED, false, true))
        assertEquals(NextStep.FAILED, nextStep(Status.CHANGED, true, false))
        assertEquals(NextStep.FAILED, nextStep(Status.VERIFIED, true, true))
        for (s in listOf(Status.VERIFIED, Status.DELETED, Status.DELETION_REQUESTED, Status.UNSUBSCRIBED, Status.IGNORED)) {
            assertEquals(NextStep.DONE, nextStep(s, false, true))
        }
    }
}
