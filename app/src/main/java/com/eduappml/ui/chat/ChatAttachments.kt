package com.eduappml.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.max

/**
 * Работа с файлами, которые пользователь прикрепляет к сообщению чата.
 *
 * Здесь НЕТ ни одной Compose-зависимости специально: чтение файла, поворот по
 * EXIF, сжатие и проверка лимитов — обычный I/O, который вызывается из
 * ViewModel на Dispatchers.IO. Экран (ChatScreen.kt) получает уже готовый
 * [PendingAttachment] с миниатюрой и понятной подписью.
 */

// ---------------------------------------------------------------------------
// Лимиты
// ---------------------------------------------------------------------------

object ChatFileLimits {
    /** Сколько файлов можно прикрепить к одному сообщению. */
    const val MaxAttachments = 5

    /**
     * Максимальная сторона картинки после сжатия. GigaChat всё равно ужимает
     * изображение до ~1792 токенов, поэтому 4000x3000 с камеры — это трафик
     * впустую: качество распознавания от него не растёт, а base64 растёт втрое.
     */
    const val MaxImageSide = 1600

    const val ImageJpegQuality = 85

    /** Предел на одну картинку ПОСЛЕ сжатия. */
    const val MaxImageBytes = 8L * 1024 * 1024

    /** Предел на один документ. У GigaChat — 40 МБ, но у нас base64 в JSON. */
    const val MaxDocumentBytes = 10L * 1024 * 1024

    /** Суммарный предел на все вложения одного сообщения. */
    const val MaxTotalBytes = 20L * 1024 * 1024
}

// ---------------------------------------------------------------------------
// Форматы
// ---------------------------------------------------------------------------

/** Картинки, которые GigaChat принимает на вход. */
private val ImageExtensions = setOf("jpg", "jpeg", "png", "tiff", "tif", "bmp")

/** Документы, которые GigaChat разбирает сам. */
private val DocumentExtensions = setOf("txt", "doc", "docx", "pdf", "epub", "ppt", "pptx", "xlsx")

/**
 * Форматы, которые GigaChat формально не принимает, но которые по сути —
 * обычный текст. Такие файлы отправляются как `<имя>.txt` с mime text/plain:
 * пользователю не нужно знать, что csv/json/kt «не поддерживаются», он просто
 * прикрепляет свой файл и получает ответ.
 */
private val TextLikeExtensions = setOf(
    "md", "markdown", "csv", "tsv", "json", "xml", "yml", "yaml", "ini", "cfg", "conf",
    "log", "sql", "html", "htm", "css", "tex", "sh", "bat", "properties", "gradle",
    "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "c", "h", "cpp", "hpp",
    "cs", "go", "rs", "rb", "php", "swift", "m", "r", "scala", "pl", "lua", "dart"
)

// ---------------------------------------------------------------------------
// Модель вложения
// ---------------------------------------------------------------------------

/**
 * Файл, выбранный пользователем и уже прочитанный в память.
 *
 * [bytes] хранится готовым к отправке (для картинок — после сжатия), чтобы
 * повторное чтение из ContentResolver не понадобилось: URI из системного
 * пикера живёт недолго и может протухнуть к моменту отправки.
 */
class PendingAttachment(
    val id: String = UUID.randomUUID().toString(),
    /** Имя, под которым файл уйдёт на сервер (может отличаться от исходного). */
    val name: String,
    /** Исходное имя — его и показываем пользователю. */
    val displayName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val isImage: Boolean,
    /** Миниатюра для полосы вложений и для пузыря сообщения. Только у картинок. */
    val thumbnail: Bitmap? = null
) {
    val sizeBytes: Long get() = bytes.size.toLong()
    val readableSize: String get() = formatFileSize(sizeBytes)
    val extension: String get() = displayName.substringAfterLast('.', "").uppercase(Locale.getDefault())
}

/** Результат попытки прикрепить файл. */
sealed class AttachmentResult {
    data class Ok(val attachment: PendingAttachment) : AttachmentResult()
    /** [message] уже написан по-русски и готов к показу пользователю. */
    data class Failure(val message: String) : AttachmentResult()
}

// ---------------------------------------------------------------------------
// Чтение и подготовка
// ---------------------------------------------------------------------------

/**
 * Читает файл по [uri], приводит его к виду, пригодному для GigaChat, и
 * проверяет лимиты. Вызывать только с фонового потока.
 *
 * [alreadyUsedBytes] — сколько весят уже прикреплённые файлы: суммарный лимит
 * проверяется здесь же, чтобы экран не занимался арифметикой.
 */
fun readAttachment(
    context: Context,
    uri: Uri,
    alreadyUsedBytes: Long = 0L
): AttachmentResult {
    val resolver = context.contentResolver

    val displayName = queryDisplayName(context, uri) ?: "файл"
    val declaredSize = queryDeclaredSize(context, uri)
    val mimeFromResolver = resolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
    val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)

    val isImage = mimeFromResolver.startsWith("image/") || extension in ImageExtensions

    // Отсекаем заведомо неподъёмные файлы ДО чтения в память: иначе большой
    // видеофайл из галереи уронит приложение по OutOfMemory раньше, чем мы
    // успеем показать вежливое сообщение.
    val hardCeiling = if (isImage) 60L * 1024 * 1024 else ChatFileLimits.MaxDocumentBytes
    if (declaredSize != null && declaredSize > hardCeiling) {
        return AttachmentResult.Failure(
            "«$displayName» весит ${formatFileSize(declaredSize)} — это больше допустимых " +
                formatFileSize(hardCeiling) + "."
        )
    }

    val raw = try {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (t: Throwable) {
        null
    } ?: return AttachmentResult.Failure("Не удалось прочитать «$displayName».")

    if (raw.isEmpty()) {
        return AttachmentResult.Failure("Файл «$displayName» пустой.")
    }

    return when {
        isImage -> prepareImage(raw, displayName, alreadyUsedBytes)
        extension in DocumentExtensions -> prepareDocument(
            bytes = raw,
            displayName = displayName,
            sendName = displayName,
            mimeType = documentMime(extension, mimeFromResolver),
            alreadyUsedBytes = alreadyUsedBytes
        )
        extension in TextLikeExtensions || mimeFromResolver.startsWith("text/") -> {
            // Отправляем как .txt — GigaChat принимает текстовые документы, но
            // не знает про расширения вроде .kt или .csv.
            val base = displayName.substringBeforeLast('.', displayName)
            prepareDocument(
                bytes = raw,
                displayName = displayName,
                sendName = "$base.txt",
                mimeType = "text/plain",
                alreadyUsedBytes = alreadyUsedBytes
            )
        }
        else -> AttachmentResult.Failure(
            "Формат «${extension.ifEmpty { "без расширения" }}» модель не читает. " +
                "Подойдут PDF, DOCX, XLSX, PPTX, TXT, EPUB и картинки."
        )
    }
}

private fun prepareImage(
    raw: ByteArray,
    displayName: String,
    alreadyUsedBytes: Long
): AttachmentResult {
    val bitmap = decodeSampled(raw, ChatFileLimits.MaxImageSide)
        ?: return AttachmentResult.Failure("Не удалось открыть изображение «$displayName».")

    val rotated = applyExifRotation(raw, bitmap)
    val scaled = scaleDown(rotated, ChatFileLimits.MaxImageSide)

    var quality = ChatFileLimits.ImageJpegQuality
    var encoded = compressJpeg(scaled, quality)
    // Если после первого прохода всё ещё тяжело — снижаем качество, а не размер:
    // текст на скриншоте важнее, чем отсутствие артефактов.
    while (encoded.size > ChatFileLimits.MaxImageBytes && quality > 45) {
        quality -= 15
        encoded = compressJpeg(scaled, quality)
    }

    if (encoded.size > ChatFileLimits.MaxImageBytes) {
        return AttachmentResult.Failure(
            "Картинка «$displayName» слишком тяжёлая даже после сжатия."
        )
    }
    if (alreadyUsedBytes + encoded.size > ChatFileLimits.MaxTotalBytes) {
        return AttachmentResult.Failure(totalLimitMessage())
    }

    val base = displayName.substringBeforeLast('.', displayName)
    return AttachmentResult.Ok(
        PendingAttachment(
            name = "$base.jpg",
            displayName = displayName,
            mimeType = "image/jpeg",
            bytes = encoded,
            isImage = true,
            thumbnail = scaleDown(scaled, 480)
        )
    )
}

private fun prepareDocument(
    bytes: ByteArray,
    displayName: String,
    sendName: String,
    mimeType: String,
    alreadyUsedBytes: Long
): AttachmentResult {
    if (bytes.size > ChatFileLimits.MaxDocumentBytes) {
        return AttachmentResult.Failure(
            "«$displayName» весит ${formatFileSize(bytes.size.toLong())}, а предел — " +
                formatFileSize(ChatFileLimits.MaxDocumentBytes) + "."
        )
    }
    if (alreadyUsedBytes + bytes.size > ChatFileLimits.MaxTotalBytes) {
        return AttachmentResult.Failure(totalLimitMessage())
    }
    return AttachmentResult.Ok(
        PendingAttachment(
            name = sendName,
            displayName = displayName,
            mimeType = mimeType,
            bytes = bytes,
            isImage = false,
            thumbnail = null
        )
    )
}

private fun totalLimitMessage(): String =
    "Суммарно вложения не должны превышать ${formatFileSize(ChatFileLimits.MaxTotalBytes)}."

private fun documentMime(extension: String, fromResolver: String): String = when (extension) {
    "pdf" -> "application/pdf"
    "txt" -> "text/plain"
    "doc" -> "application/msword"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "ppt" -> "application/vnd.ms-powerpoint"
    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "epub" -> "application/epub+zip"
    else -> fromResolver.ifEmpty { "application/octet-stream" }
}

// ---------------------------------------------------------------------------
// Картинки: декодирование, поворот, масштаб
// ---------------------------------------------------------------------------

/**
 * Декодирует картинку сразу уменьшенной. Двухпроходный приём (сначала
 * inJustDecodeBounds, потом inSampleSize) — единственный способ открыть снимок
 * с 108-мегапиксельной камеры, не выделив под него 400 МБ.
 */
fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val longest = max(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null

    var sample = 1
    while (longest / sample > maxSide * 2) sample *= 2

    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (e: OutOfMemoryError) {
        null
    }
}

/**
 * Фотографии с камеры почти всегда лежат «боком»: сенсор снимает в своей
 * ориентации, а поворот записан в EXIF. Без этого шага модель получает
 * повёрнутый на 90° лист с задачей и честно не может его прочитать.
 */
private fun applyExifRotation(raw: ByteArray, bitmap: Bitmap): Bitmap {
    val degrees = try {
        val exif = ExifInterface(raw.inputStream())
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (t: Throwable) {
        0f
    }
    if (degrees == 0f) return bitmap
    return try {
        val matrix = Matrix().apply { postRotate(degrees) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: OutOfMemoryError) {
        bitmap
    }
}

private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
    val longest = max(bitmap.width, bitmap.height)
    if (longest <= maxSide) return bitmap
    val ratio = maxSide.toFloat() / longest
    val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
    val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
    return try {
        Bitmap.createScaledBitmap(bitmap, width, height, true)
    } catch (e: OutOfMemoryError) {
        bitmap
    }
}

private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray {
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}

// ---------------------------------------------------------------------------
// Мелочи
// ---------------------------------------------------------------------------

fun encodeBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

fun decodeBase64(value: String): ByteArray? = try {
    Base64.decode(value, Base64.DEFAULT)
} catch (t: Throwable) {
    null
}

fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.getDefault(), "%.1f МБ", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.0f КБ", bytes / 1024.0)
    else -> "$bytes Б"
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/')
    } catch (t: Throwable) {
        uri.lastPathSegment?.substringAfterLast('/')
    }
}

private fun queryDeclaredSize(context: Context, uri: Uri): Long? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
    } catch (t: Throwable) {
        null
    }
}
