package com.eduappml.ui.gnn

import kotlin.math.exp
import kotlin.random.Random

const val NODE_COUNT_MIL = 14
const val FEAT_DIM_MIL = 2

/**
 * Военный вариант "эталонной задачи" GNN: определение принадлежности
 * поста связи к одной из двух групп взаимодействия по структуре графа
 * контактов. Полный структурный аналог [GnnLab] (который остаётся
 * нетронутым и больше нигде не используется) — только со сменой сцены
 * ("социальная сеть знакомых" → "сеть радиосвязи").
 */
object GnnLabMilitary {
    /** Список смежности — фиксированный граф с двумя группами (первые 7 узлов и последние 7). */
    val adjacency: List<MutableList<Int>> by lazy { buildGraph(seed = 42) }
    val community: IntArray by lazy { IntArray(NODE_COUNT_MIL) { if (it < NODE_COUNT_MIL / 2) 0 else 1 } }
    val features: Array<FloatArray> by lazy { generateFeatures(seed = 1) }

    private fun buildGraph(seed: Int): List<MutableList<Int>> {
        val rnd = Random(seed)
        val adj = List(NODE_COUNT_MIL) { mutableListOf<Int>() }
        val half = NODE_COUNT_MIL / 2
        for (i in 0 until NODE_COUNT_MIL) {
            for (j in i + 1 until NODE_COUNT_MIL) {
                val sameGroup = (i < half) == (j < half)
                val p = if (sameGroup) 0.6f else 0.05f
                if (rnd.nextFloat() < p) {
                    adj[i].add(j)
                    adj[j].add(i)
                }
            }
        }
        return adj
    }

    private fun generateFeatures(seed: Int): Array<FloatArray> {
        val rnd = Random(seed)
        // Признаки НАМЕРЕННО случайны и не связаны с группой — узнать группу
        // можно только через структуру связей, а не через сами эти числа.
        return Array(NODE_COUNT_MIL) { FloatArray(FEAT_DIM_MIL) { (rnd.nextFloat() - 0.5f) } }
    }

    /** Один слой обмена сообщениями: агрегация соседей усреднением + линейное преобразование + ReLU. */
    class GnnLayer(inDim: Int, private val outDim: Int, seed: Int) {
        val wSelf: Array<FloatArray> = run {
            val rnd = Random(seed)
            Array(outDim) { FloatArray(inDim) { (rnd.nextFloat() * 2f - 1f) * 0.5f } }
        }
        val wNeigh: Array<FloatArray> = run {
            val rnd = Random(seed + 500)
            Array(outDim) { FloatArray(inDim) { (rnd.nextFloat() * 2f - 1f) * 0.5f } }
        }
        val b: FloatArray = FloatArray(outDim)

        private var lastH: Array<FloatArray> = arrayOf()
        private var lastAgg: Array<FloatArray> = arrayOf()
        private var lastZ: Array<FloatArray> = arrayOf()

        fun forward(h: Array<FloatArray>, adj: List<List<Int>>): Array<FloatArray> {
            val n = h.size
            val inDim = h[0].size
            val agg = Array(n) { v ->
                if (adj[v].isEmpty()) FloatArray(inDim)
                else {
                    val sum = FloatArray(inDim)
                    adj[v].forEach { u -> for (d in 0 until inDim) sum[d] += h[u][d] }
                    FloatArray(inDim) { d -> sum[d] / adj[v].size }
                }
            }
            val z = Array(n) { v ->
                FloatArray(outDim) { k ->
                    var sum = b[k]
                    for (d in h[v].indices) sum += wSelf[k][d] * h[v][d]
                    for (d in agg[v].indices) sum += wNeigh[k][d] * agg[v][d]
                    sum
                }
            }
            lastH = h; lastAgg = agg; lastZ = z
            return Array(n) { v -> FloatArray(outDim) { k -> maxOf(0f, z[v][k]) } }
        }

        /** Двусторонний обратный проход: прямой вклад (узел как он сам) + косвенный (узел как чей-то сосед). */
        fun backward(gradOut: Array<FloatArray>, adj: List<List<Int>>, lr: Float): Array<FloatArray> {
            val n = gradOut.size
            val inDim = lastH[0].size
            val gradZ = Array(n) { v -> FloatArray(outDim) { k -> if (lastZ[v][k] > 0f) gradOut[v][k] else 0f } }

            val gradWSelf = Array(outDim) { FloatArray(inDim) }
            val gradWNeigh = Array(outDim) { FloatArray(inDim) }
            val gradB = FloatArray(outDim)
            for (v in 0 until n) {
                for (k in 0 until outDim) {
                    gradB[k] += gradZ[v][k]
                    for (d in 0 until inDim) {
                        gradWSelf[k][d] += gradZ[v][k] * lastH[v][d]
                        gradWNeigh[k][d] += gradZ[v][k] * lastAgg[v][d]
                    }
                }
            }

            // прямой вклад: узел v как собственный вход через wSelf
            val gradH = Array(n) { v ->
                FloatArray(inDim) { d ->
                    var sum = 0f
                    for (k in 0 until outDim) sum += wSelf[k][d] * gradZ[v][k]
                    sum
                }
            }
            // косвенный вклад: узел v как сосед узла w (v входит в agg[w])
            for (v in 0 until n) {
                for (w in adj[v]) {
                    val degW = adj[w].size
                    if (degW == 0) continue
                    for (d in 0 until inDim) {
                        var sum = 0f
                        for (k in 0 until outDim) sum += wNeigh[k][d] * gradZ[w][k]
                        gradH[v][d] += sum / degW
                    }
                }
            }

            for (k in 0 until outDim) {
                for (d in 0 until inDim) {
                    wSelf[k][d] -= lr * gradWSelf[k][d]
                    wNeigh[k][d] -= lr * gradWNeigh[k][d]
                }
                b[k] -= lr * gradB[k]
            }
            return gradH
        }
    }

    private fun sigmoid(z: Float): Float = (1.0 / (1.0 + exp(-z.toDouble()))).toFloat()

    class Head(inDim: Int, seed: Int) {
        val w: FloatArray = run {
            val rnd = Random(seed)
            FloatArray(inDim) { (rnd.nextFloat() * 2f - 1f) * 0.5f }
        }
        var b: Float = 0f
        private var lastX: Array<FloatArray> = arrayOf()
        private var lastP: FloatArray = floatArrayOf()

        fun forward(h: Array<FloatArray>): FloatArray {
            lastX = h
            lastP = FloatArray(h.size) { v ->
                var sum = b
                for (d in h[v].indices) sum += w[d] * h[v][d]
                sigmoid(sum)
            }
            return lastP
        }

        fun backward(labels: IntArray, lr: Float): Array<FloatArray> {
            val n = lastX.size
            val inDim = w.size
            val gradW = FloatArray(inDim)
            var gradB = 0f
            val gradH = Array(n) { FloatArray(inDim) }
            for (v in 0 until n) {
                val gradZ = lastP[v] - labels[v]   // упрощённая dL/dz для сигмоиды + log-loss (сумма по узлам)
                for (d in 0 until inDim) {
                    gradW[d] += gradZ * lastX[v][d]
                    gradH[v][d] = w[d] * gradZ
                }
                gradB += gradZ
            }
            for (d in 0 until inDim) w[d] -= lr * gradW[d]
            b -= lr * gradB
            return gradH
        }
    }

    data class Model(val layers: List<GnnLayer>, val head: Head)

    fun train(numLayers: Int, hidden: Int, lr: Float, epochs: Int, seed: Int = 11): Model {
        val layers = (0 until numLayers).map { i ->
            GnnLayer(if (i == 0) FEAT_DIM_MIL else hidden, hidden, seed + i * 7)
        }
        val head = Head(hidden, seed + 900)

        repeat(epochs) {
            var h = features
            layers.forEach { layer -> h = layer.forward(h, adjacency) }
            head.forward(h)
            var gradH = head.backward(community, lr)
            layers.reversed().forEach { layer -> gradH = layer.backward(gradH, adjacency, lr) }
        }
        return Model(layers, head)
    }

    fun predict(model: Model): FloatArray {
        var h = features
        model.layers.forEach { layer -> h = layer.forward(h, adjacency) }
        return model.head.forward(h)
    }

    fun accuracy(model: Model): Float {
        val probs = predict(model)
        val correct = probs.indices.count { v -> (probs[v] >= 0.5f) == (community[v] == 1) }
        return correct.toFloat() / NODE_COUNT_MIL
    }
}
