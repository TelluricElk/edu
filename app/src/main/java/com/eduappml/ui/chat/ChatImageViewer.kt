package com.eduappml.ui.chat

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream

/**
 * Полноэкранный просмотр картинки из чата: щипок для зума, перетаскивание,
 * двойного тапа нет намеренно (в связке с MarkdownText внутри LazyColumn
 * лишние жесты легко перехватывают прокрутку — этот класс багов в проекте
 * уже ловили с pointerInput/detectTapGestures).
 *
 * [saveBytes] задан только для картинок, сгенерированных моделью: миниатюру
 * собственного вложения сохранять некуда и незачем, оригинал и так у
 * пользователя в галерее.
 */
@Composable
internal fun ChatImageViewer(
    bitmap: Bitmap,
    saveBytes: ByteArray?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            // На единичном масштабе картинка всегда возвращается в центр —
            // иначе после отпускания пальцев она остаётся смещённой и кажется,
            // что экран «поехал».
            offsetX = 0f
            offsetY = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            // Клик по фону закрывает — но только когда не зумили: иначе выход
            // происходит случайно в середине разглядывания.
            .clickable(enabled = scale <= 1.01f, onClick = onClose)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Изображение во весь экран",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 64.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .transformable(state = transformState)
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (saveBytes != null) {
                ViewerAction(icon = Icons.Filled.Download, description = "Сохранить") {
                    val message = saveImageToGallery(context, saveBytes)
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
            ViewerAction(icon = Icons.Filled.Close, description = "Закрыть", onClick = onClose)
        }

        if (scale <= 1.01f) {
            Text(
                text = "Щипок — увеличить, тап по фону — закрыть",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, GlassStroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = Color.White)
    }
}

/**
 * Сохраняет картинку в общую галерею.
 *
 * На Android 10+ (API 29) это делается через MediaStore с RELATIVE_PATH и
 * НЕ требует ни одного разрешения — поэтому в манифест ничего добавлять не
 * нужно. На более старых версиях пришлось бы просить WRITE_EXTERNAL_STORAGE
 * и обрабатывать отказ; ради одной кнопки это лишний разрешенческий диалог
 * при первом запуске, поэтому там честно говорим, что сохранение недоступно.
 *
 * Возвращает уже готовый к показу текст.
 */
private fun saveImageToGallery(context: Context, bytes: ByteArray): String {
    val fileName = "EduAI_${System.currentTimeMillis()}.jpg"
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + File.separator + "EduAppML"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return "Не удалось создать файл в галерее."

            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: return "Не удалось записать файл."

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Сохранено в Картинки/EduAppML"
        } else {
            // Запасной путь: кэш приложения. Файл виден только приложению, но
            // сообщение честное — так пользователь хотя бы понимает, что
            // произошло, вместо молчаливого отказа кнопки.
            val dir = File(context.cacheDir, "images").apply { mkdirs() }
            FileOutputStream(File(dir, fileName)).use { it.write(bytes) }
            "Сохранение в галерею доступно на Android 10 и новее."
        }
    } catch (t: Throwable) {
        "Не удалось сохранить изображение."
    }
}
