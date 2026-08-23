package com.eduappml.ui.tr

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Военный вариант "эталонной задачи" self-attention: то же вручную заданное
 * предложение-донесение из семи слов вместо "Маленький кот быстро поймал
 * серую мышь ночью". Полный структурный аналог [TrLab] (который остаётся
 * нетронутым и больше нигде не используется) — грамматические роли и сами
 * векторы признаков сохранены один в один, изменились только слова.
 */
object TrLabMilitary {
    /** Донесение для демонстрации — фиксированное, семь слов. */
    val words: List<String> = listOf("Дозорный", "расчёт", "быстро", "обнаружил", "вражеский", "отряд", "ночью")

    const val EMBED_DIM = 6

    /**
     * ВАЖНО: эти векторы заданы вручную, а не выучены на реальном тексте — см. пояснение
     * в task.ru.md. Измерения (в этом порядке): [подлежащее/сущ., сказуемое/глагол,
     * определение/наречие, одушевлённость, время суток, смещение(bias)]. Это учебная
     * иллюстрация механизма, а не результат обучения на корпусе текстов.
     */
    val embeddings: Array<FloatArray> = arrayOf(
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), // Дозорный — определение
        floatArrayOf(1f, 0f, 0f, 1f, 0f, 1f), // расчёт — существительное, одушевлённое
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), // быстро — наречие
        floatArrayOf(0f, 1f, 0f, 0f, 0f, 1f), // обнаружил — глагол
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), // вражеский — определение
        floatArrayOf(1f, 0f, 0f, 1f, 0f, 1f), // отряд — существительное, одушевлённое
        floatArrayOf(0f, 0f, 0f, 0f, 1f, 1f)  // ночью — время
    )

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    private fun softmax(x: FloatArray): FloatArray {
        val max = x.max()
        val exps = FloatArray(x.size) { i -> exp((x[i] - max).toDouble()).toFloat() }
        val sum = exps.sum()
        return FloatArray(x.size) { i -> exps[i] / sum }
    }

    /**
     * Настоящий self-attention: Q = K = V = embeddings (единичные проекции — осознанное
     * упрощение, обучаемых матриц здесь нет), но сам softmax(QK^T/sqrt(d))V считается
     * по формуле без единого сокращения.
     */
    fun attentionWeights(): Array<FloatArray> {
        val n = words.size
        val scale = sqrt(EMBED_DIM.toFloat())
        val scores = Array(n) { i -> FloatArray(n) { j -> dot(embeddings[i], embeddings[j]) / scale } }
        return Array(n) { i -> softmax(scores[i]) }
    }

    /** Точная формула positional encoding из статьи "Attention Is All You Need" — без единого обучаемого параметра. */
    fun positionalEncoding(nPositions: Int, dModel: Int): Array<FloatArray> {
        return Array(nPositions) { pos ->
            FloatArray(dModel) { dim ->
                val i = dim / 2
                val divTerm = 10000.0.pow(2.0 * i / dModel)
                if (dim % 2 == 0) sin(pos / divTerm).toFloat()
                else cos(pos / divTerm).toFloat()
            }
        }
    }
}
