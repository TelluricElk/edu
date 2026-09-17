package com.eduappml.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Визуальная часть работы с вложениями в чате: панель выбора источника,
 * полоса ещё не отправленных файлов, отрисовка вложений внутри пузыря и
 * «печатает»-индикатор.
 *
 * Стилистика — та же, что и во всём приложении: полупрозрачное стекло поверх
 * затемнённого фона, акцент [ChatAccent], скругления 16–20dp. Отдельный файл
 * нужен, чтобы ChatScreen.kt остался читаемым: там и так живут инсеты,
 * клавиатура и список сообщений.
 */

internal val ChatAccent = Color(0xFFB9B6FF)
internal val GlassFill = Color.White.copy(alpha = 0.12f)
internal val GlassStroke = Color.White.copy(alpha = 0.22f)

// ---------------------------------------------------------------------------
// Панель выбора источника
// ---------------------------------------------------------------------------

/**
 * Раскрывается НАД строкой ввода, внутри той же колонки — не всплывающим
 * окном. Так она автоматически живёт по тем же правилам инсетов и клавиатуры,
 * что и вся колонка разговора, и не приходится вручную вычислять, на сколько
 * её поднять над полем ввода.
 */
@Composable
internal fun AttachSourcePanel(
    visible: Boolean,
    scale: Float,
    cameraAvailable: Boolean,
    onPickImages: () -> Unit,
    onPickDocuments: () -> Unit,
    onCamera: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + expandVertically(tween(200)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
                .padding(6.dp)
        ) {
            AttachSourceRow(
                icon = Icons.Filled.Image,
                title = "Изображение",
                subtitle = "Фото, скриншот, скан конспекта",
                scale = scale,
                onClick = onPickImages
            )
            if (cameraAvailable) {
                AttachSourceRow(
                    icon = Icons.Filled.PhotoCamera,
                    title = "Снять на камеру",
                    subtitle = "Сфотографировать задачу или доску",
                    scale = scale,
                    onClick = onCamera
                )
            }
            AttachSourceRow(
                icon = Icons.Filled.Description,
                title = "Документ",
                subtitle = "PDF, DOCX, XLSX, PPTX, TXT, код",
                scale = scale,
                onClick = onPickDocuments
            )
        }
    }
}

@Composable
private fun AttachSourceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    scale: Float,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp * scale)
                .clip(CircleShape)
                .background(ChatAccent.copy(alpha = 0.22f))
                .border(1.dp, ChatAccent.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ChatAccent,
                modifier = Modifier.size(21.dp * scale)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Полоса ещё не отправленных вложений
// ---------------------------------------------------------------------------

@Composable
internal fun PendingAttachmentsStrip(
    items: List<PendingAttachment>,
    scale: Float,
    onRemove: (String) -> Unit
) {
    AnimatedVisibility(
        visible = items.isNotEmpty(),
        enter = fadeIn(tween(150)) + expandVertically(tween(180)),
        exit = fadeOut(tween(110)) + shrinkVertically(tween(140))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${items.size} ${filesWord(items.size)} · " +
                        formatFileSize(items.sumOf { it.sizeBytes }),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    PendingAttachmentChip(item = item, scale = scale, onRemove = { onRemove(item.id) })
                }
            }
        }
    }
}

@Composable
private fun PendingAttachmentChip(
    item: PendingAttachment,
    scale: Float,
    onRemove: () -> Unit
) {
    val height = 60.dp * scale
    Box {
        if (item.isImage && item.thumbnail != null) {
            Image(
                bitmap = item.thumbnail.asImageBitmap(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(height)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
            )
        } else {
            Row(
                modifier = Modifier
                    .height(height)
                    .widthIn(min = 140.dp * scale, max = 210.dp * scale)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassFill)
                    .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = iconForExtension(item.extension),
                    contentDescription = null,
                    tint = ChatAccent,
                    modifier = Modifier.size(22.dp * scale)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.extension.ifEmpty { "ФАЙЛ" }} · ${item.readableSize}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // Крестик заходит на угол плитки — так он не съедает ширину строки и
        // при этом остаётся пальцеразмерным (мишень 24dp + отступ).
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Убрать вложение",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Плашка ошибки прикрепления
// ---------------------------------------------------------------------------

@Composable
internal fun AttachErrorBanner(message: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(150)) + expandVertically(tween(180)),
        exit = fadeOut(tween(110)) + shrinkVertically(tween(140))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFF6B6B).copy(alpha = 0.20f))
                .border(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFFFC2C2),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message.orEmpty(),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Вложения внутри уже отправленного сообщения
// ---------------------------------------------------------------------------

@Composable
internal fun MessageAttachments(
    attachments: List<ChatUiAttachment>,
    bubbleWidth: Dp,
    onOpenImage: (ChatUiAttachment) -> Unit
) {
    val images = attachments.filter { it.isImage && it.preview != null }
    val documents = attachments.filterNot { it.isImage && it.preview != null }

    if (images.size == 1) {
        val single = images.first()
        val preview = single.preview!!
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = single.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onOpenImage(single) }
        )
    } else if (images.isNotEmpty()) {
        // Сетка по два — без FlowRow: он всё ещё экспериментальный, а ручные
        // строки по chunked(2) дают ровно тот же результат и не тянут @OptIn.
        val cell = (bubbleWidth - 6.dp) / 2
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            images.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { item ->
                        Image(
                            bitmap = item.preview!!.asImageBitmap(),
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(cell)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenImage(item) }
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.size(cell))
                }
            }
        }
    }

    if (documents.isNotEmpty()) {
        if (images.isNotEmpty()) Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            documents.forEach { doc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.22f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = iconForExtension(doc.extension),
                        contentDescription = null,
                        tint = ChatAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.displayName,
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = doc.readableSize,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Картинки, сгенерированные моделью
// ---------------------------------------------------------------------------

@Composable
internal fun GeneratedImages(
    images: List<ChatUiImage>,
    onOpen: (ChatUiImage) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        images.forEach { image ->
            // Декодируем один раз на сообщение: LazyColumn пересобирает пузырь
            // на каждой прокрутке, и декодирование JPEG в композиции быстро
            // превратилось бы в подтормаживание списка.
            val bitmap = remember(image.id) { decodeSampled(image.bytes, 1280) }
            if (bitmap != null) {
                val ratio = if (bitmap.height > 0) {
                    (bitmap.width.toFloat() / bitmap.height.toFloat()).coerceIn(0.5f, 2.0f)
                } else 1f
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Изображение от Edu.AI",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                        .clickable { onOpen(image) }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Индикатор «печатает»
// ---------------------------------------------------------------------------

@Composable
internal fun TypingBubble(label: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(GlassFill)
                .border(1.dp, GlassStroke, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp
            )
            Spacer(Modifier.width(8.dp))
            TypingDots()
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ChatAccent.copy(alpha = alpha))
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Мелочи
// ---------------------------------------------------------------------------

internal fun iconForExtension(extension: String): ImageVector =
    when (extension.lowercase(Locale.ROOT)) {
        "pdf" -> Icons.Filled.PictureAsPdf
        "xlsx", "xls", "csv", "tsv" -> Icons.Filled.GridOn
        "ppt", "pptx" -> Icons.Filled.Slideshow
        "doc", "docx", "txt", "md", "epub" -> Icons.Filled.Description
        else -> Icons.Filled.InsertDriveFile
    }

private fun filesWord(count: Int): String {
    val rem100 = count % 100
    if (rem100 in 11..14) return "файлов"
    return when (count % 10) {
        1 -> "файл"
        2, 3, 4 -> "файла"
        else -> "файлов"
    }
}
