package ai.hey.phone.feature.pair

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** Server pair code: 8 upper-case letters/digits without look-alikes; lenient here, the server is the judge. */
private val BARE_CODE = Regex("^[A-Za-z0-9]{4,16}$")
private val LINK_CODE = Regex("""(?:hey\.lastmile\.ltd/p/|heyparri://p/|heyparri:///p/)([A-Za-z0-9]{4,16})(?:[/?#].*)?$""")

/**
 * The pair code in text that was scanned from a QR or typed by hand: the car QR holds `https://hey.lastmile.ltd/p/{code}`,
 * a hand-typed value is the bare code. Anything else (`/p/done`, other sites, free text) is not a code.
 */
fun parsePairCode(text: String?): String? {
    val t = text?.trim().orEmpty()
    val code = when {
        t.isEmpty() -> null
        BARE_CODE.matches(t) -> t
        else -> LINK_CODE.find(t)?.groupValues?.get(1)
    }
    return code?.takeIf { !it.equals("done", ignoreCase = true) }?.uppercase()
}

private val reader = ThreadLocal.withInitial {
    MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }
}

/** Decodes a QR from an 8-bit luminance plane ([rowStride] bytes per row, [width]x[height] used); null when none is found. */
fun decodeQr(luma: ByteArray, rowStride: Int, width: Int, height: Int): String? {
    val source = PlanarYUVLuminanceSource(luma, rowStride, height, 0, 0, width, height, false)
    val r = reader.get()!!
    return try {
        r.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: NotFoundException) {
        null
    } finally {
        r.reset()
    }
}
