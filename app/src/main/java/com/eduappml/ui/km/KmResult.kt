package com.eduappml.ui.km

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.AskChatButton
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.QuizOption
import com.eduappml.ui.common.QuizQuestion
import com.eduappml.ui.common.QuizSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val kmQuizIb = listOf(
    QuizQuestion(
        "Инерция при k=8 оказалась вдвое меньше, чем при k=4. Значит ли это, что восемь кластеров лучше четырёх?",
        listOf(
            QuizOption(
                "Нет. Инерция убывает при любом росте k по определению и обращается в ноль, когда кластеров столько же, сколько объектов",
                true
            ),
            QuizOption("Да, инерция — основная метрика качества кластеризации", false),
            QuizOption("Да, но только если силуэт тоже вырос", false),
            QuizOption("Нельзя сказать: инерцию нельзя сравнивать при разных k", false)
        ),
        "Каждый новый центр забирает себе часть точек и уменьшает сумму квадратов расстояний. " +
            "Поэтому по инерции ищут излом на глаз — метод локтя, — и это ненадёжный приём: " +
            "излом часто размазан. Силуэт в нашей задаче ведёт себя честнее: он имеет максимум " +
            "при k=4 (0,677), что и есть настоящее число групп в потоке."
    ),
    QuizQuestion(
        "Нормализацию выключили, и силуэт вырос с 0,677 до 0,722. Стала ли кластеризация лучше?",
        listOf(
            QuizOption(
                "Нет, стала хуже: совпадение с настоящими группами упало с 92,9% до 79,4%. Силуэт считается в том же искажённом пространстве и потому неверный масштаб не ловит",
                true
            ),
            QuizOption("Да, силуэт — независимая метрика, ей можно верить", false),
            QuizOption("Да, но выигрыш в пределах погрешности", false),
            QuizOption("Силуэт при разных пространствах вообще несравним, вопрос некорректен", false)
        ),
        "Это самая коварная ловушка темы. Внутренние метрики — инерция и силуэт — оценивают " +
            "разбиение теми же расстояниями, по которым оно построено. Если расстояние " +
            "посчитано в кривых единицах, метрика этого не заметит и может даже вырасти. " +
            "Масштаб признаков проверяют до кластеризации и головой, а не метрикой после."
    ),
    QuizQuestion(
        "Почему k-means даёт разные результаты при разных случайных сидах?",
        listOf(
            QuizOption(
                "Обе фазы алгоритма не увеличивают инерцию, поэтому он сходится в ближайший локальный минимум, а какой он — определяют начальные центры",
                true
            ),
            QuizOption("Из-за ошибок округления при усреднении координат", false),
            QuizOption("Потому что порядок обхода точек случайный", false),
            QuizOption("Он не должен давать разные результаты, это признак ошибки в коде", false)
        ),
        "В нашей задаче при случайной инициализации двенадцать сидов дают шесть разных " +
            "исходов, а инерция гуляет от 2,52 до 5,89 — более чем вдвое. Выбраться из " +
            "плохого минимума нельзя: переназначение точек и пересчёт центров оба только " +
            "уменьшают инерцию, так что подняться вверх процедура не может."
    ),
    QuizQuestion(
        "Что делает k-means++ и что он гарантирует?",
        listOf(
            QuizOption(
                "Разносит начальные центры, выбирая каждый следующий с вероятностью, пропорциональной квадрату расстояния до ближайшего уже выбранного; гарантий оптимума не даёт, но сильно сужает разброс",
                true
            ),
            QuizOption("Гарантирует глобальный минимум инерции", false),
            QuizOption("Подбирает оптимальное число кластеров", false),
            QuizOption("Нормализует признаки перед кластеризацией", false)
        ),
        "На двенадцати сидах k-means++ даёт три разных исхода вместо шести, и хорошее " +
            "решение выпадает в восьми случаях из двенадцати против двух. Это существенно, но не " +
            "гарантия. Поэтому на практике алгоритм всё равно запускают несколько раз с " +
            "разными сидами и берут прогон с наименьшей инерцией — в библиотеках за это " +
            "отвечает параметр n_init."
    ),
    QuizQuestion(
        "Алертов от массового обновления ПО в потоке 90, а от бокового смещения — 20. Как это влияет на разбиение?",
        listOf(
            QuizOption(
                "Метод стремится к кластерам сопоставимого размера: лишний центр скорее разрежет крупную плотную группу, чем найдёт мелкую",
                true
            ),
            QuizOption("Никак: алгоритм не учитывает размеры кластеров", false),
            QuizOption("Мелкая группа будет найдена первой, потому что она компактнее", false),
            QuizOption("Крупная группа получит больше центров пропорционально размеру, и это правильно", false)
        ),
        "Минимизируется суммарная сумма квадратов расстояний, а вклад крупной группы в неё " +
            "больше просто из-за количества точек. Разрезать её надвое выгоднее, чем выделить " +
            "двадцать алертов в стороне. Это принципиальное ограничение метода: он ищет " +
            "кластеры примерно равного размера и примерно шарообразные. Для вытянутых или " +
            "сильно разноразмерных групп берут DBSCAN или иерархическую кластеризацию."
    )
)

/** Результаты эталонных прогонов — считаются в фоне, их много. */
private class KmReference(
    val normOn: KmState,
    val normOff: KmState,
    val curvesOn: KmLab.KCurves,
    val curvesOff: KmLab.KCurves,
    val bestKOn: Int,
    val bestKOff: Int,
    val randomInertia: DoubleArray,
    val ppInertia: DoubleArray,
    val randomPurity: DoubleArray,
    val ppPurity: DoubleArray,
    val compositions: List<IntArray>,
    val convergence: IntArray
)

private fun lo(v: DoubleArray): Double {
    var m = v[0]
    for (x in v) if (x < m) m = x
    return m
}

private fun hi(v: DoubleArray): Double {
    var m = v[0]
    for (x in v) if (x > m) m = x
    return m
}

private fun distinctCount(v: DoubleArray): Int {
    val seen = ArrayList<String>()
    for (x in v) {
        val s = "%.4f".format(x)
        if (!seen.contains(s)) seen.add(s)
    }
    return seen.size
}

/**
 * Сколько сидов привели к хорошему решению — инерции в пределах пяти
 * процентов от наилучшей из всех найденных. Считать точные совпадения
 * бессмысленно: разные локальные минимумы могут отличаться в четвёртом
 * знаке и при этом быть одинаково хорошими.
 */
private fun goodCount(v: DoubleArray, threshold: Double): Int {
    var c = 0
    for (x in v) if (x <= threshold) c++
    return c
}

/**
 * Экран «Решение задачи» для метода k средних.
 *
 * Четыре сюжета, все числа считаются на лету через [KmLab]:
 *   1. эталонный прогон и расшифровка кластеров;
 *   2. нормализация — и почему силуэт её отсутствие не ловит;
 *   3. зависимость от инициализации и что даёт k-means++;
 *   4. выбор числа кластеров: локоть против силуэта.
 *
 * Прогонов здесь около сорока, и каждый считает силуэт за квадратичное
 * время, поэтому всё вынесено в фон.
 */
@Composable
fun KmResult(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFFFFD93D)
    val good = Color(0xFF6BCB77)
    val bad = Color(0xFFFF6B6B)
    val topicTitle = title ?: "Метод k средних"

    var ref by remember { mutableStateOf<KmReference?>(null) }

    LaunchedEffect(Unit) {
        ref = withContext(Dispatchers.Default) {
            val k = KmLab.DEFAULT_K
            val it0 = KmLab.DEFAULT_ITERATIONS
            val sd = KmLab.DEFAULT_SEED
            val pp = KmInit.PLUS_PLUS

            val on = KmLab.run(k, it0, sd, pp, true)
            val off = KmLab.run(k, it0, sd, pp, false)
            val cOn = KmLab.kCurves(it0, sd, pp, true)
            val cOff = KmLab.kCurves(it0, sd, pp, false)

            val seeds = KmLab.SEED_MAX - KmLab.SEED_MIN + 1
            val rndI = DoubleArray(seeds)
            val ppI = DoubleArray(seeds)
            val rndP = DoubleArray(seeds)
            val ppP = DoubleArray(seeds)
            for (i in 0 until seeds) {
                val s = KmLab.SEED_MIN + i
                val a = KmLab.run(k, it0, s, KmInit.RANDOM, true)
                val b = KmLab.run(k, it0, s, pp, true)
                rndI[i] = a.inertia; rndP[i] = a.purity
                ppI[i] = b.inertia; ppP[i] = b.purity
            }

            val conv = IntArray(8)
            for (i in 0 until 8) conv[i] = KmLab.run(k, i + 1, sd, pp, true).moved

            KmReference(
                normOn = on,
                normOff = off,
                curvesOn = cOn,
                curvesOff = cOff,
                bestKOn = KmLab.bestKBySilhouette(cOn),
                bestKOff = KmLab.bestKBySilhouette(cOff),
                randomInertia = rndI,
                ppInertia = ppI,
                randomPurity = rndP,
                ppPurity = ppP,
                compositions = (0 until k).map { KmLab.composition(on, it) },
                convergence = conv
            )
        }
    }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = topicTitle,
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        val r = ref
        if (r == null) {
            Text("Считаем эталонные прогоны…",
                color = textColor.copy(alpha = 0.6f), fontSize = 14.sp)
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Эталонное решение", color = textColor,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${KmLab.DEFAULT_K} кластера, ${KmLab.DEFAULT_ITERATIONS} шагов, " +
                            "инициализация k-means++, сид ${KmLab.DEFAULT_SEED}, нормализация " +
                            "включена. Поток — ${KmLab.alerts.size} алертов, меток нет.",
                        color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Инерция ${"%.4f".format(r.normOn.inertia)}   •   силуэт " +
                            "${"%.4f".format(r.normOn.silhouette)}   •   совпало с настоящими " +
                            "группами ${"%.2f".format(r.normOn.purity * 100)}%",
                        color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Что попало в каждый кластер:", color = textColor.copy(alpha = 0.7f),
                        fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    for (j in 0 until KmLab.DEFAULT_K) {
                        val comp = r.compositions[j]
                        val parts = ArrayList<String>()
                        for (g in KmLab.GROUPS.indices) {
                            if (comp[g] > 0) parts.add("${KmLab.GROUPS[g].name} — ${comp[g]}")
                        }
                        Text(
                            "•  ${r.normOn.sizes[j]} алертов: ${parts.joinToString(", ")}",
                            color = textColor.copy(alpha = 0.85f), fontSize = 13.sp,
                            lineHeight = 18.sp, modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Три группы из четырёх выделены почти в чистом виде. Смешались подбор " +
                            "пароля и боковое смещение — обе дают мало затронутых хостов и " +
                            "длительные серии, и по этим двум признакам они действительно " +
                            "похожи. Чтобы их разделить, нужен третий признак, а не другой " +
                            "алгоритм.",
                        color = textColor.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Главный вывод: метрика не заметит неверного масштаба",
                        color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        lineHeight = 21.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "С нормализацией:  совпало ${"%.2f".format(r.normOn.purity * 100)}%, " +
                            "силуэт ${"%.4f".format(r.normOn.silhouette)}",
                        color = good, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Без нормализации: совпало ${"%.2f".format(r.normOff.purity * 100)}%, " +
                            "силуэт ${"%.4f".format(r.normOff.silhouette)}",
                        color = bad, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Результат стал хуже на " +
                            "${"%.1f".format((r.normOn.purity - r.normOff.purity) * 100)} " +
                            "процентных пункта, а силуэт при этом ВЫРОС.\n\n" +
                            "Причина в том, что силуэт и инерция — внутренние метрики: они " +
                            "оценивают разбиение теми же расстояниями, по которым оно и " +
                            "построено. Если расстояние посчитано в единицах, где минуты в " +
                            "двадцать четыре раза «длиннее» хостов, метрика измерит это " +
                            "искажённое расстояние и останется довольна.\n\n" +
                            "Отсюда правило: масштаб признаков проверяют ДО кластеризации и " +
                            "своей головой. Никакая метрика после не подскажет, что " +
                            "пространство выбрано неверно. В теме «k ближайших соседей» эта " +
                            "же проблема хотя бы ловилась по контрольной выборке — здесь " +
                            "контрольной выборки нет вовсе.\n\n" +
                            "Есть и второе следствие: без нормализации силуэт максимален при " +
                            "k = ${r.bestKOff}, а с нормализацией — при k = ${r.bestKOn}. " +
                            "Настоящих групп ${KmLab.TRUE_K}.",
                        color = textColor.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Начальные центры решают многое",
                        color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        lineHeight = 21.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Двенадцать сидов, k = ${KmLab.DEFAULT_K}, нормализация включена:",
                        color = textColor.copy(alpha = 0.7f), fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    val rLo = lo(r.randomInertia)
                    val rHi = hi(r.randomInertia)
                    val pLo = lo(r.ppInertia)
                    val pHi = hi(r.ppInertia)
                    val good5 = minOf(rLo, pLo) * 1.05
                    listOf(
                        "случайная — инерция от ${"%.4f".format(rLo)} до ${"%.4f".format(rHi)}, " +
                            "${distinctCount(r.randomInertia)} разных исходов, хорошее решение " +
                            "в ${goodCount(r.randomInertia, good5)} сидах из 12",
                        "k-means++ — инерция от ${"%.4f".format(pLo)} до ${"%.4f".format(pHi)}, " +
                            "${distinctCount(r.ppInertia)} разных исходов, хорошее решение " +
                            "в ${goodCount(r.ppInertia, good5)} сидах из 12",
                        "совпадение с настоящими группами: случайная от " +
                            "${"%.1f".format(lo(r.randomPurity) * 100)}% до " +
                            "${"%.1f".format(hi(r.randomPurity) * 100)}%, k-means++ от " +
                            "${"%.1f".format(lo(r.ppPurity) * 100)}% до " +
                            "${"%.1f".format(hi(r.ppPurity) * 100)}%"
                    ).forEach {
                        Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 13.sp,
                            lineHeight = 18.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Разброс инерции более чем вдвое — это не погрешность вычислений, а " +
                            "разные локальные минимумы. Обе фазы алгоритма Ллойда только " +
                            "уменьшают инерцию: переназначение точек к ближайшим центрам " +
                            "уменьшает её при фиксированных центрах, пересчёт центров в " +
                            "средние — при фиксированном разбиении. Подняться вверх " +
                            "процедура не может, поэтому из плохого минимума не выбирается " +
                            "никогда.\n\n" +
                            "k-means++ борется с этим на входе: разносит начальные центры, " +
                            "выбирая каждый следующий с вероятностью, пропорциональной " +
                            "квадрату расстояния до ближайшего уже выбранного. Гарантии это " +
                            "не даёт, поэтому на практике делают и то, и другое: " +
                            "k-means++ плюс несколько запусков с выбором лучшего по инерции.",
                        color = textColor.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Локоть против силуэта",
                        color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        lineHeight = 21.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Нормализация включена, k-means++, сид ${KmLab.DEFAULT_SEED}:",
                        color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    for (i in r.curvesOn.ks.indices) {
                        val mark = if (r.curvesOn.ks[i] == r.bestKOn) "  ← максимум силуэта" else ""
                        Text(
                            "•  k = ${r.curvesOn.ks[i]}: инерция " +
                                "${"%.4f".format(r.curvesOn.inertia[i])}, силуэт " +
                                "${"%.4f".format(r.curvesOn.silhouette[i])}$mark",
                            color = if (r.curvesOn.ks[i] == r.bestKOn) accent
                                else textColor.copy(alpha = 0.85f),
                            fontSize = 13.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Инерция убывает всегда — при k, равном числу алертов, она обратится " +
                            "в ноль, потому что каждый алерт станет своим собственным " +
                            "кластером. Поэтому метод локтя предлагает искать излом на глаз, " +
                            "и это ненадёжно: излом часто размазан по двум-трём значениям.\n\n" +
                            "Силуэт имеет максимум и потому даёт ответ. Здесь он указывает на " +
                            "k = ${r.bestKOn} — ровно столько групп в потоке и есть. Но " +
                            "полагаться на него безоговорочно нельзя, что видно из " +
                            "предыдущей карточки: стоит испортить масштаб, и он укажет на " +
                            "${r.bestKOff}.\n\n" +
                            "В реальной работе к обеим метрикам добавляют третье соображение: " +
                            "сколько кластеров аналитик готов разбирать. Двадцать идеально " +
                            "разделённых кластеров бесполезны, если каждое утро их некому " +
                            "просматривать.",
                        color = textColor.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    AskChatButton(accent = accent, onClick = {
                        onOpenChat(
                            "Объясни, пожалуйста, простыми словами, почему получился именно " +
                                "такой результат в теме «$topicTitle» (Решение задачи).\n\n" +
                                "Задача: кластеризация ${KmLab.alerts.size} алертов SIEM по двум " +
                                "признакам — число затронутых хостов (до 60) и длительность " +
                                "серии событий в минутах (до 1440). Меток нет. В потоке " +
                                "${KmLab.TRUE_K} настоящие группы разного размера.\n" +
                                "Эталон (k=${KmLab.DEFAULT_K}, k-means++, нормализация): инерция " +
                                "${"%.4f".format(r.normOn.inertia)}, силуэт " +
                                "${"%.4f".format(r.normOn.silhouette)}, совпало " +
                                "${"%.2f".format(r.normOn.purity * 100)}%.\n" +
                                "Без нормализации: совпало " +
                                "${"%.2f".format(r.normOff.purity * 100)}%, силуэт " +
                                "${"%.4f".format(r.normOff.silhouette)} — то есть результат хуже, " +
                                "а метрика выше.\n" +
                                "Случайная инициализация на 12 сидах даёт инерцию от " +
                                "${"%.4f".format(lo(r.randomInertia))} до " +
                                "${"%.4f".format(hi(r.randomInertia))}.\n\n" +
                                "Почему внутренние метрики не ловят неверный масштаб и почему " +
                                "алгоритм застревает в локальных минимумах?"
                        )
                    })
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Полученные знания", color = textColor,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "Алгоритм Ллойда чередует две фазы — приписать точки к ближайшим " +
                            "центрам и пересчитать центры в средние. Обе только уменьшают " +
                            "инерцию, поэтому сходимость гарантирована, а оптимальность нет.",
                        "Сходимость определяется точно: ноль переназначенных точек за шаг " +
                            "означает неподвижную точку. В нашей задаче это " +
                            "${r.convergence.indexOfFirst { it == 0 } + 1} шагов.",
                        "Результат зависит от начальных центров. k-means++ разносит их и " +
                            "сокращает разброс, но не устраняет — поэтому делают несколько " +
                            "запусков и берут лучший по инерции.",
                        "Инерция убывает при любом росте k и потому не может служить " +
                            "критерием выбора; силуэт имеет максимум, но верит тому же " +
                            "искажённому расстоянию.",
                        "Нормализация обязательна, если признаки в разных единицах, и " +
                            "проверить её отсутствие метриками нельзя.",
                        "Метод ищет кластеры сопоставимого размера и примерно шарообразные. " +
                            "Для вытянутых, разноплотных или вложенных групп берут DBSCAN " +
                            "или иерархическую кластеризацию."
                    ).forEach {
                        Text(
                            "•  $it",
                            color = textColor.copy(alpha = 0.85f), fontSize = 13.sp,
                            lineHeight = 18.sp, modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = kmQuizIb, textColor = textColor, nodeId = "km")
    }
}
