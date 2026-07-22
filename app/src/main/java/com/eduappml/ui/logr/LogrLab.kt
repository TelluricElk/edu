package com.eduappml.ui.logr

import kotlin.math.exp
import kotlin.random.Random

data class StudyPoint(val hours: Float, val passed: Float) // passed: 0f или 1f

object LogrLab {
    const val HOURS_MIN = 0f
    const val HOURS_MAX = 12f

    val trainSet: List<StudyPoint> by lazy { generate(seed = 42, n = 50) }
    val testSet: List<StudyPoint> by lazy { generate(seed = 777, n = 20) }

    private fun generate(seed: Int, n: Int): List<StudyPoint> {
        val rnd = Random(seed)
        return (0 until n).map {
            val hours = HOURS_MIN + rnd.nextFloat() * (HOURS_MAX - HOURS_MIN)
            // истинная вероятность сдачи растёт с часами подготовки (порог около 6 часов)
            val trueProb = sigmoid(1.1f * (hours - 6f))
            val passed = if (rnd.nextFloat() < trueProb) 1f else 0f
            StudyPoint(hours, passed)
        }
    }

    fun sigmoid(z: Float): Float = (1.0 / (1.0 + exp(-z.toDouble()))).toFloat()

    private fun gradientStep(data: List<StudyPoint>, w: Float, b: Float, lr: Float): Pair<Float, Float> {
        var gradW = 0f
        var gradB = 0f
        data.forEach { p ->
            val prob = sigmoid(w * p.hours + b)
            val error = prob - p.passed
            gradW += error * p.hours
            gradB += error
        }
        gradW /= data.size
        gradB /= data.size
        return (w - lr * gradW) to (b - lr * gradB)
    }

    data class FitResult(val w: Float, val b: Float, val diverged: Boolean)

    fun fit(lr: Float, epochs: Int): FitResult {
        var w = 0f
        var b = 0f
        for (e in 0 until epochs) {
            val (nw, nb) = gradientStep(trainSet, w, b, lr)
            w = nw; b = nb
            if (w.isNaN() || b.isNaN() || kotlin.math.abs(w) > 1e4f) return FitResult(w, b, true)
        }
        return FitResult(w, b, false)
    }

    fun predictProba(hours: Float, w: Float, b: Float): Float = sigmoid(w * hours + b)

    data class ConfusionMatrix(val tp: Int, val fp: Int, val tn: Int, val fn: Int) {
        val precision: Float get() = if (tp + fp == 0) 0f else tp.toFloat() / (tp + fp)
        val recall: Float get() = if (tp + fn == 0) 0f else tp.toFloat() / (tp + fn)
        val accuracy: Float get() {
            val total = tp + fp + tn + fn
            return if (total == 0) 0f else (tp + tn).toFloat() / total
        }
    }

    fun confusionMatrix(data: List<StudyPoint>, w: Float, b: Float, threshold: Float): ConfusionMatrix {
        var tp = 0; var fp = 0; var tn = 0; var fn = 0
        data.forEach { p ->
            val predicted = if (predictProba(p.hours, w, b) >= threshold) 1f else 0f
            when {
                predicted == 1f && p.passed == 1f -> tp++
                predicted == 1f && p.passed == 0f -> fp++
                predicted == 0f && p.passed == 0f -> tn++
                else -> fn++
            }
        }
        return ConfusionMatrix(tp, fp, tn, fn)
    }
}
