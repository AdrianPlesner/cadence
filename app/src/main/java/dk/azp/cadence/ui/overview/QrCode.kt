package dk.azp.cadence.ui.overview

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) { renderQrCode(text, QR_PIXELS) }
    Image(bitmap.asImageBitmap(), contentDescription = "Invite QR code", modifier = modifier, filterQuality = FilterQuality.None)
}

private fun renderQrCode(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { index -> if (matrix[index % size, index / size]) BLACK else WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

private const val QR_PIXELS = 512
private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
