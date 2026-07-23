package com.eduappml.ui.som

import kotlin.math.exp
import kotlin.random.Random

data class RgbColor(val r: Float, val g: Float, val b: Float)

object SomLab {
    const val GRID_SIZE = 12
    const val COLOR_COUNT = 8

    /** Горизонт затухания скорости обучения и радиуса окрестности — фиксирован,
     * чтобы слайдер "шаг обучения" показывал реальный прогресс против одного и
     * того же расписания, а не менял сам темп затухания. */
    const val DECAY_HORIZON = 300
    private const val LR0 = 0.5f
    private val RADIUS0 = GRID_SIZE / 2f

    val inputColors: List<RgbColor> by lazy { generateColors(seed = 42, count = COLOR_COUNT) }

    private fun generateColors(seed: Int, count: Int): List<RgbColor> {
        val rnd = Random(seed)
        return List(count) { RgbColor(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) }
    }

    class Map(seed: Int) {
        /** weights[x][y] = [r, g, b] — цвет, который сейчас хранит нейрон (x, y). */
        val weights: Array<Array<FloatArray>> = run {
            val rnd = Random(seed)
            Array(GRID_SIZE) { Array(GRID_SIZE) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) } }
        }

        fun findBmu(x: RgbColor): Pair<Int, Int> {
            var bestGx = 0
            var bestGy = 0
            var bestDist = Float.MAX_VALUE
            for (gx in 0 until GRID_SIZE) {
                for (gy in 0 until GRID_SIZE) {
                    val w = weights[gx][gy]
                    val dr = w[0] - x.r
                    val dg = w[1] - x.g
                    val db = w[2] - x.b
                    val dist = dr * dr + dg * dg + db * db
                    if (dist < bestDist) {
                        bestDist = dist; bestGx = gx; bestGy = gy
                    }
                }
            }
            return bestGx to bestGy
        }

        /** Один шаг Кохонена: находим победителя, обновляем всю сетку с силой,
         * убывающей и по расстоянию до победителя, и по времени. */
        fun trainStep(input: RgbColor, t: Int, decayHorizon: Int) {
            val bmu = findBmu(input)
            val progress = t.toFloat() / decayHorizon
            val lr = LR0 * exp(-progress)
            val radius = RADIUS0 * exp(-progress)
            val twoRadiusSq = 2f * radius * radius + 1e-6f

            for (gx in 0 until GRID_SIZE) {
                for (gy in 0 until GRID_SIZE) {
                    val dx = (gx - bmu.first).toFloat()
                    val dy = (gy - bmu.second).toFloat()
                    val gridDistSq = dx * dx + dy * dy
                    val neighborhood = exp(-gridDistSq / twoRadiusSq)
                    val factor = lr * neighborhood
                    if (factor < 1e-4f) continue
                    val w = weights[gx][gy]
                    w[0] += factor * (input.r - w[0])
                    w[1] += factor * (input.g - w[1])
                    w[2] += factor * (input.b - w[2])
                }
            }
        }
    }

    /**
     * Строит карту "как она выглядела бы после [currentStep] шагов обучения",
     * против фиксированного расписания затухания [DECAY_HORIZON] — именно это
     * даёт настоящую, честную прогрессию при движении слайдера шагов.
     */
    fun trainUpTo(currentStep: Int, seed: Int = 7): Map {
        val map = Map(seed)
        val rnd = Random(seed + 1000)
        for (t in 0 until currentStep) {
            val input = inputColors[rnd.nextInt(inputColors.size)]
            map.trainStep(input, t, DECAY_HORIZON)
        }
        return map
    }

    /** Средняя "негладкость" карты — расстояние между соседними по сетке нейронами.
     * Меньше значение = более организованная, плавная карта. */
    fun averageNeighborDistance(map: Map): Float {
        var total = 0f
        var count = 0
        for (gx in 0 until GRID_SIZE) {
            for (gy in 0 until GRID_SIZE) {
                if (gx + 1 < GRID_SIZE) {
                    total += rgbDistance(map.weights[gx][gy], map.weights[gx + 1][gy]); count++
                }
                if (gy + 1 < GRID_SIZE) {
                    total += rgbDistance(map.weights[gx][gy], map.weights[gx][gy + 1]); count++
                }
            }
        }
        return if (count == 0) 0f else total / count
    }

    private fun rgbDistance(a: FloatArray, b: FloatArray): Float {
        val dr = a[0] - b[0]; val dg = a[1] - b[1]; val db = a[2] - b[2]
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }
}
