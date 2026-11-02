package com.ndicamera.professional

import com.ndicamera.professional.billing.Feature
import com.ndicamera.professional.billing.PlanCatalog
import com.ndicamera.professional.billing.PlanLimits
import com.ndicamera.professional.billing.Tier
import com.ndicamera.professional.domain.model.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlansTest {
    private val hd = Resolution(1280, 720)
    private val fhd = Resolution(1920, 1080)
    private val uhd = Resolution(3840, 2160)

    @Test fun freeIsLimitedTo720p30() {
        assertTrue(PlanLimits.modeAllowed(Tier.FREE, hd, 30))
        assertFalse(PlanLimits.modeAllowed(Tier.FREE, hd, 60))
        assertFalse(PlanLimits.modeAllowed(Tier.FREE, fhd, 30))
    }

    @Test fun basicAllows1080p30Only() {
        assertTrue(PlanLimits.modeAllowed(Tier.BASIC, fhd, 30))
        assertFalse(PlanLimits.modeAllowed(Tier.BASIC, fhd, 60))
        assertFalse(PlanLimits.modeAllowed(Tier.BASIC, uhd, 30))
    }

    @Test fun proAllows1080p60NotUhd() {
        assertTrue(PlanLimits.modeAllowed(Tier.PRO, fhd, 60))
        assertFalse(PlanLimits.modeAllowed(Tier.PRO, uhd, 30))
    }

    @Test fun ultraAndMaxAllowUhd() {
        assertTrue(PlanLimits.modeAllowed(Tier.ULTRA, uhd, 60))
        assertTrue(PlanLimits.modeAllowed(Tier.MAX, uhd, 60))
    }

    @Test fun freeHasSessionLimitWatermarkAndMono() {
        assertEquals(15L * 60_000L, PlanLimits.sessionLimitMs(Tier.FREE))
        assertTrue(PlanLimits.watermark(Tier.FREE))
        assertEquals(1, PlanLimits.maxAudioChannels(Tier.FREE))
        assertNull(PlanLimits.sessionLimitMs(Tier.BASIC))
        assertFalse(PlanLimits.watermark(Tier.BASIC))
        assertEquals(2, PlanLimits.maxAudioChannels(Tier.BASIC))
    }

    @Test fun maxUnlocksEveryFeature() {
        Feature.entries.forEach { assertTrue(it.name, it.availableIn(Tier.MAX)) }
        Feature.entries.forEach { assertFalse(it.name, it.availableIn(Tier.FREE)) }
    }

    @Test fun ownedProductsMapToHighestTier() {
        assertEquals(Tier.FREE, PlanCatalog.tierFromOwnedProducts(emptyList()))
        assertEquals(Tier.FREE, PlanCatalog.tierFromOwnedProducts(listOf("unknown")))
        assertEquals(Tier.PRO, PlanCatalog.tierFromOwnedProducts(listOf("ndicam_basic", "ndicam_pro")))
        assertEquals(Tier.MAX, PlanCatalog.tierFromOwnedProducts(listOf("ndicam_max_lifetime", "ndicam_ultra")))
    }

    @Test fun everyPaidTierHasExactlyOneProduct() {
        Tier.entries.filter { it != Tier.FREE }.forEach { t ->
            assertEquals(t.name, 1, PlanCatalog.products.count { it.tier == t })
        }
        assertFalse(PlanCatalog.productFor(Tier.MAX)!!.subscription)
    }
}
