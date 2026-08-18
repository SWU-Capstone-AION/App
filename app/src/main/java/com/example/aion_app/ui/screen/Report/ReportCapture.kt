package com.example.aion_app.ui.screen.report

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Picture
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ============================================================
// Compose 화면 캡처 → Bitmap → 갤러리 저장 유틸
// 위치: ui/screen/report/ReportCapture.kt
//
// android.graphics.Picture 로 컴포저블의 그리기를 기록해 두었다가
// 필요할 때 Bitmap 으로 변환한다. (현재 Compose BOM 에서 안정적으로 동작하는 방식)
// ============================================================

// capturable() 로 감싼 영역을 Bitmap 으로 뽑아주는 컨트롤러
class CaptureController {
    // capturable Modifier 가 매 프레임 그리기를 기록해두는 Picture
    internal var picture: Picture? = null

    fun toBitmap(): Bitmap? {
        val pic = picture ?: return null
        if (pic.width <= 0 || pic.height <= 0) return null

        val bitmap = Bitmap.createBitmap(pic.width, pic.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE) // 투명 배경 방지 (흰 배경)
        canvas.drawPicture(pic)
        return bitmap
    }
}

@Composable
fun rememberCaptureController(): CaptureController = remember { CaptureController() }

// content 의 그리기를 Picture 에 기록해서 나중에 캡처할 수 있게 하는 Modifier.
// 스크롤 안쪽 자식에 붙이면 화면에 안 보이는 아래 영역까지 자연 높이로 기록된다.
fun Modifier.capturable(controller: CaptureController): Modifier = this.then(
    Modifier.drawWithCache {
        val width = size.width.toInt()
        val height = size.height.toInt()
        val picture = Picture()
        controller.picture = picture

        onDrawWithContent {
            val pictureCanvas = Canvas(picture.beginRecording(width, height))
            // 컴포저블 원본 내용을 Picture 캔버스에 그림
            draw(this, layoutDirection, pictureCanvas, size) {
                this@onDrawWithContent.drawContent()
            }
            picture.endRecording()
            // 실제 화면에도 그대로 그려줌
            drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPicture(picture) }
        }
    }
)

// Bitmap 을 갤러리(Pictures/AION)에 저장.
// Android 10(Q) 이상: 별도 권한 없이 저장 가능.
// Android 9 이하: AndroidManifest 에 WRITE_EXTERNAL_STORAGE 권한 + 런타임 권한 요청 필요.
suspend fun saveBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    displayName: String
): Boolean = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AION")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return@withContext false

    try {
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: return@withContext false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}
