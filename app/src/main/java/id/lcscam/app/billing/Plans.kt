package id.lcscam.app.billing

import id.lcscam.app.domain.model.Resolution
import id.lcscam.app.i18n.tr

/**
 * Plans in ascending order. PRO is a subscription with every production feature up to 1080p60;
 * MAX is a one-time (lifetime) purchase that adds 4K on top of everything in PRO.
 */
enum class Tier(val label: String) {
    FREE("Free"), PRO("Pro"), MAX("Max");

    fun atLeast(other: Tier): Boolean = ordinal >= other.ordinal
}

/** Gated features and the lowest plan that unlocks each one. */
enum class Feature(val minTier: Tier, private val en: String, private val id: String) {
    UNLIMITED_SESSION(Tier.PRO, "Unlimited stream duration", "Durasi stream tanpa batas"),
    NO_WATERMARK(Tier.PRO, "No watermark on the NDI output", "Tanpa watermark di output NDI"),
    RES_1080P(Tier.PRO, "1080p Full HD output", "Output 1080p Full HD"),
    MANUAL_EXPOSURE(Tier.PRO, "Manual ISO, shutter, EV and AE lock", "ISO, shutter, EV dan kunci AE manual"),
    STEREO_AUDIO(Tier.PRO, "Stereo audio", "Audio stereo"),
    CUSTOM_SOURCE_NAME(Tier.PRO, "Custom NDI source name", "Nama source NDI kustom"),
    BACKGROUND_STREAMING(Tier.PRO, "Streaming with the screen off / in background", "Streaming saat layar mati / di latar belakang"),
    HIGH_FPS(Tier.PRO, "50/60 fps", "50/60 fps"),
    MANUAL_FOCUS(Tier.PRO, "Manual focus and focus lock", "Fokus manual dan kunci fokus"),
    MANUAL_WB(Tier.PRO, "Manual white balance (Kelvin, tint, AWB lock)", "White balance manual (Kelvin, tint, kunci AWB)"),
    PRO_OVERLAYS(Tier.PRO, "Zebra, focus peaking, histogram, safe areas", "Zebra, focus peaking, histogram, area aman"),
    EXTERNAL_AUDIO(Tier.PRO, "USB microphones and audio interfaces", "Mikrofon USB dan audio interface"),
    ALL_LENSES(Tier.PRO, "Ultra-wide, tele and all physical lenses", "Ultra-wide, tele dan semua lensa fisik"),
    RES_4K(Tier.MAX, "4K UHD output (when the camera supports it)", "Output 4K UHD (jika kamera mendukung)"),
    NDI_GROUPS(Tier.PRO, "NDI groups and discovery tools", "Grup NDI dan alat discovery"),
    AV_SYNC_OFFSET(Tier.PRO, "Audio/video sync offset", "Offset sinkron audio/video"),
    TALLY(Tier.PRO, "Tally (preview / program) indicator", "Indikator tally (preview / program)"),
    ;

    val title: String get() = tr(en, id)

    fun availableIn(tier: Tier): Boolean = tier.atLeast(minTier)
}

/**
 * Google Play product configuration. The product IDs below must be created in Play Console
 * exactly as written (see docs/GOOGLE_PLAY.md). Displayed prices always come from Google Play;
 * [suggestedPrice] is only the recommended price to enter in Play Console.
 */
data class PlanProduct(
    val tier: Tier,
    val productId: String,
    val subscription: Boolean,
    val suggestedPrice: String,
)

object PlanCatalog {
    val products = listOf(
        PlanProduct(Tier.PRO, "ndicam_pro", subscription = true, suggestedPrice = "Rp 69.000 / bulan · Rp 599.000 / tahun (US$4.49 / US$39.99)"),
        PlanProduct(Tier.MAX, "ndicam_max_lifetime", subscription = false, suggestedPrice = "Rp 1.499.000 sekali bayar (US$99.99)"),
    )

    fun productFor(tier: Tier): PlanProduct? = products.firstOrNull { it.tier == tier }
    /**
     * Product IDs of the earlier five-plan line-up. They were never sold, but if a tester owns
     * one it still unlocks the closest current plan instead of silently falling back to Free.
     */
    private val legacyProducts = mapOf("ndicam_basic" to Tier.PRO, "ndicam_ultra" to Tier.PRO)

    fun tierForProduct(productId: String): Tier? =
        products.firstOrNull { it.productId == productId }?.tier ?: legacyProducts[productId]

    /** Highest tier among owned products. */
    fun tierFromOwnedProducts(productIds: Collection<String>): Tier =
        productIds.mapNotNull { tierForProduct(it) }.maxByOrNull { it.ordinal } ?: Tier.FREE
}

/** Pure limit rules derived from a tier (unit-tested). */
object PlanLimits {
    /** Free sessions stop automatically after this long. */
    const val FREE_SESSION_MS = 15L * 60L * 1000L
    const val WATERMARK_TEXT = "LCS CAM - FREE"

    fun maxPixels(tier: Tier): Long = when {
        Feature.RES_4K.availableIn(tier) -> Long.MAX_VALUE
        Feature.RES_1080P.availableIn(tier) -> 1920L * 1080L
        else -> 1280L * 720L
    }

    fun maxFps(tier: Tier): Int = if (Feature.HIGH_FPS.availableIn(tier)) 60 else 30

    fun resolutionAllowed(tier: Tier, r: Resolution): Boolean = r.pixels <= maxPixels(tier)
    fun modeAllowed(tier: Tier, r: Resolution, fps: Int): Boolean = resolutionAllowed(tier, r) && fps <= maxFps(tier)

    fun sessionLimitMs(tier: Tier): Long? = if (Feature.UNLIMITED_SESSION.availableIn(tier)) null else FREE_SESSION_MS
    fun watermark(tier: Tier): Boolean = !Feature.NO_WATERMARK.availableIn(tier)
    fun maxAudioChannels(tier: Tier): Int = if (Feature.STEREO_AUDIO.availableIn(tier)) 2 else 1
}
