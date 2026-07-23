package com.eduappml.ui.rl

import kotlin.random.Random

object RlLab {
    const val GRID = 8
    val START = 0 to 0
    val GOAL = 7 to 7

    /** Стена с одним проёмом на y=4 — заставляет агента найти обходной путь. */
    val WALLS: Set<Pair<Int, Int>> = (0 until GRID).filter { it != 4 }.map { 3 to it }.toSet()

    // up, down, right, left
    private val ACTIONS = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
    private const val MAX_STEPS = 200
    private const val GAMMA = 0.9f

    data class StepResult(val newPos: Pair<Int, Int>, val reward: Float, val done: Boolean)

    fun envStep(pos: Pair<Int, Int>, actionIdx: Int): StepResult {
        val (dx, dy) = ACTIONS[actionIdx]
        val next = (pos.first + dx) to (pos.second + dy)
        val inBounds = next.first in 0 until GRID && next.second in 0 until GRID
        if (!inBounds || next in WALLS) {
            return StepResult(pos, -1f, false)
        }
        if (next == GOAL) {
            return StepResult(next, 50f, true)
        }
        return StepResult(next, -1f, false)
    }

    class QTable {
        val table = HashMap<Pair<Int, Int>, FloatArray>()
        fun get(state: Pair<Int, Int>): FloatArray = table.getOrPut(state) { FloatArray(4) }
    }

    data class TrainResult(val q: QTable, val stepsPerEpisode: List<Int>)

    /** Полное обучение на [episodes] эпизодов. Настоящий табличный Q-learning, не имитация. */
    fun train(episodes: Int, alpha: Float, epsilon: Float, seed: Int = 1): TrainResult {
        val rnd = Random(seed)
        val q = QTable()
        val history = mutableListOf<Int>()

        repeat(episodes) {
            var pos = START
            var steps = 0
            for (s in 0 until MAX_STEPS) {
                steps = s + 1
                val qValues = q.get(pos)
                val action = if (rnd.nextFloat() < epsilon) {
                    rnd.nextInt(4)
                } else {
                    qValues.indices.maxByOrNull { qValues[it] }!!
                }
                val result = envStep(pos, action)
                val qNext = q.get(result.newPos)
                qValues[action] += alpha * (result.reward + GAMMA * qNext.max() - qValues[action])
                pos = result.newPos
                if (result.done) break
            }
            history.add(steps)
        }
        return TrainResult(q, history)
    }

    /** Жадный путь по уже обученной Q-таблице — то, что реально "показал бы" агент сейчас. */
    fun greedyPath(q: QTable, maxLen: Int = 60): List<Pair<Int, Int>> {
        val path = mutableListOf(START)
        var pos = START
        repeat(maxLen) {
            val qValues = q.get(pos)
            val action = qValues.indices.maxByOrNull { qValues[it] }!!
            val result = envStep(pos, action)
            path.add(result.newPos)
            pos = result.newPos
            if (result.done) return path
            // застрял (бьётся туда-сюда) — прекращаем, чтобы не зациклиться в отрисовке
            if (path.size > 4 && path[path.size - 1] == path[path.size - 3]) return path
        }
        return path
    }
}
