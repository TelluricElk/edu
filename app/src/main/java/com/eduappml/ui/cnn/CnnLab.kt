package com.eduappml.ui.cnn

import com.eduappml.ui.common.Activation
import com.eduappml.ui.common.Dense
import kotlin.random.Random

const val GRID = 8
private const val KERNEL = 3
private const val CONV_OUT = GRID - KERNEL + 1   // 6
private const val POOL_OUT = CONV_OUT / 2         // 3

data class CnnImage(val pixels: Array<FloatArray>, val label: Float) // label: 1f = горизонтальная, 0f = вертикальная

/** Свёрточный слой: несколько фильтров k×k, ReLU внутри, ручной forward/backward. */
class Conv2D(nFilters: Int, seed: Int) {
    val filters: Array<Array<FloatArray>> = run {
        val rnd = Random(seed)
        Array(nFilters) { Array(KERNEL) { FloatArray(KERNEL) { (rnd.nextFloat() * 2f - 1f) * 0.5f } } }
    }
    val biases: FloatArray = FloatArray(nFilters)
    val nFilters get() = filters.size

    private var lastX: Array<FloatArray> = arrayOf()
    private var lastZ: Array<Array<FloatArray>> = arrayOf()

    fun forward(x: Array<FloatArray>): Array<Array<FloatArray>> {
        val z = Array(nFilters) { f ->
            Array(CONV_OUT) { i ->
                FloatArray(CONV_OUT) { j ->
                    var sum = biases[f]
                    for (u in 0 until KERNEL) {
                        for (v in 0 until KERNEL) {
                            sum += x[i + u][j + v] * filters[f][u][v]
                        }
                    }
                    sum
                }
            }
        }
        lastX = x
        lastZ = z
        return Array(nFilters) { f -> Array(CONV_OUT) { i -> FloatArray(CONV_OUT) { j -> maxOf(0f, z[f][i][j]) } } }
    }

    fun backward(gradA: Array<Array<FloatArray>>, lr: Float) {
        val gradFilters = Array(nFilters) { Array(KERNEL) { FloatArray(KERNEL) } }
        val gradBiases = FloatArray(nFilters)
        for (f in 0 until nFilters) {
            for (i in 0 until CONV_OUT) {
                for (j in 0 until CONV_OUT) {
                    val gradZ = if (lastZ[f][i][j] > 0f) gradA[f][i][j] else 0f
                    if (gradZ == 0f) continue
                    for (u in 0 until KERNEL) {
                        for (v in 0 until KERNEL) {
                            gradFilters[f][u][v] += gradZ * lastX[i + u][j + v]
                        }
                    }
                    gradBiases[f] += gradZ
                }
            }
        }
        for (f in 0 until nFilters) {
            for (u in 0 until KERNEL) {
                for (v in 0 until KERNEL) {
                    filters[f][u][v] -= lr * gradFilters[f][u][v]
                }
            }
            biases[f] -= lr * gradBiases[f]
        }
    }
}

/** Max-pooling 2x2, шаг 2 — градиент идёт только в позицию, бывшую максимумом. */
class MaxPool2D {
    private var argMaxI: Array<IntArray> = arrayOf()
    private var argMaxJ: Array<IntArray> = arrayOf()
    private var nFiltersLast = 0

    fun forward(x: Array<Array<FloatArray>>): Array<Array<FloatArray>> {
        val nF = x.size
        nFiltersLast = nF
        argMaxI = Array(nF) { IntArray(POOL_OUT * POOL_OUT) }
        argMaxJ = Array(nF) { IntArray(POOL_OUT * POOL_OUT) }
        return Array(nF) { f ->
            Array(POOL_OUT) { i ->
                FloatArray(POOL_OUT) { j ->
                    var best = Float.NEGATIVE_INFINITY
                    var bi = i * 2
                    var bj = j * 2
                    for (di in 0..1) {
                        for (dj in 0..1) {
                            val v = x[f][i * 2 + di][j * 2 + dj]
                            if (v > best) {
                                best = v; bi = i * 2 + di; bj = j * 2 + dj
                            }
                        }
                    }
                    argMaxI[f][i * POOL_OUT + j] = bi
                    argMaxJ[f][i * POOL_OUT + j] = bj
                    best
                }
            }
        }
    }

    fun backward(gradA: Array<Array<FloatArray>>): Array<Array<FloatArray>> {
        val gradX = Array(nFiltersLast) { Array(CONV_OUT) { FloatArray(CONV_OUT) } }
        for (f in 0 until nFiltersLast) {
            for (i in 0 until POOL_OUT) {
                for (j in 0 until POOL_OUT) {
                    val bi = argMaxI[f][i * POOL_OUT + j]
                    val bj = argMaxJ[f][i * POOL_OUT + j]
                    gradX[f][bi][bj] += gradA[f][i][j]
                }
            }
        }
        return gradX
    }
}

object CnnLab {
    const val HIDDEN = 8

    val trainSet: List<CnnImage> by lazy { generate(seed = 42, perClass = 25) }
    val testSet: List<CnnImage> by lazy { generate(seed = 777, perClass = 15) }

    private fun generate(seed: Int, perClass: Int): List<CnnImage> {
        val rnd = Random(seed)
        val images = mutableListOf<CnnImage>()
        repeat(perClass) { images.add(makeImage(horizontal = true, rnd)) }
        repeat(perClass) { images.add(makeImage(horizontal = false, rnd)) }
        return images
    }

    private fun makeImage(horizontal: Boolean, rnd: Random): CnnImage {
        val pixels = Array(GRID) { FloatArray(GRID) }
        if (horizontal) {
            val row = rnd.nextInt(GRID)
            for (c in 0 until GRID) pixels[row][c] = 1f
        } else {
            val col = rnd.nextInt(GRID)
            for (r in 0 until GRID) pixels[r][col] = 1f
        }
        for (r in 0 until GRID) {
            for (c in 0 until GRID) {
                val noise = (rnd.nextFloat() - 0.5f) * 0.3f
                pixels[r][c] = (pixels[r][c] + noise).coerceIn(0f, 1f)
            }
        }
        return CnnImage(pixels, if (horizontal) 1f else 0f)
    }

    class Network(nFilters: Int, seed: Int) {
        val conv = Conv2D(nFilters, seed + 1)
        val pool = MaxPool2D()
        val flatSize = nFilters * POOL_OUT * POOL_OUT
        val dense1 = Dense(flatSize, HIDDEN, Activation.RELU, seed + 10)
        val dense2 = Dense(HIDDEN, 1, Activation.SIGMOID, seed + 11)

        private var lastPoolShape = 0

        fun predictProba(pixels: Array<FloatArray>): Float {
            val c = conv.forward(pixels)
            val p = pool.forward(c)
            val flat = flatten(p)
            val h = dense1.forward(flat)
            return dense2.forward(h)[0]
        }

        /** Возвращает карты признаков (после ReLU) — для визуализации фильтров в интерактиве. */
        fun featureMaps(pixels: Array<FloatArray>): Array<Array<FloatArray>> = conv.forward(pixels)

        private fun flatten(p: Array<Array<FloatArray>>): FloatArray {
            val out = FloatArray(flatSize)
            var idx = 0
            for (f in p.indices) {
                for (i in p[f].indices) {
                    for (j in p[f][i].indices) {
                        out[idx++] = p[f][i][j]
                    }
                }
            }
            return out
        }

        fun trainStep(data: List<CnnImage>, lr: Float) {
            data.forEach { img ->
                val c = conv.forward(img.pixels)
                val p = pool.forward(c)
                val flat = flatten(p)
                val h = dense1.forward(flat)
                val pred = dense2.forward(h)[0]

                var grad = floatArrayOf(pred - img.label)
                val (a2w, a2b) = dense2.newAccumulators()
                grad = dense2.backward(grad, a2w, a2b)
                dense2.apply(a2w, a2b, lr, 1)

                val (a1w, a1b) = dense1.newAccumulators()
                grad = dense1.backward(grad, a1w, a1b)
                dense1.apply(a1w, a1b, lr, 1)

                val gradPool = Array(conv.nFilters) { f ->
                    Array(POOL_OUT) { i ->
                        FloatArray(POOL_OUT) { j -> grad[f * POOL_OUT * POOL_OUT + i * POOL_OUT + j] }
                    }
                }
                val gradConv = pool.backward(gradPool)
                conv.backward(gradConv, lr)
            }
        }
    }

    fun train(nFilters: Int, lr: Float, epochs: Int, seed: Int = 3): Network {
        val net = Network(nFilters, seed)
        repeat(epochs) { net.trainStep(trainSet, lr) }
        return net
    }

    fun accuracy(net: Network, data: List<CnnImage>): Float {
        if (data.isEmpty()) return 0f
        val correct = data.count { (net.predictProba(it.pixels) >= 0.5f) == (it.label >= 0.5f) }
        return correct.toFloat() / data.size
    }
}
