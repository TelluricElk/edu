package com.eduappml.ui.menu

/**
 * Описание пузыря-узла.
 * xFrac / yFrac — стартовая позиция как доля ширины/высоты (0f..1f)
 * radiusDp — базовый радиус пузыря в dp.
 */
data class NodeSpec(
    val id: String,
    val label: String,
    val xFrac: Float,
    val yFrac: Float,
    val radiusDp: Float = 28f
)

/** Описание ребра между пузырями по id узлов. */
data class EdgeSpec(
    val fromId: String,
    val toId: String
)

/** Девять алгоритмов с короткими подписями и несимметричными стартовыми позициями. */
fun defaultNodes(): List<NodeSpec> = listOf(
    NodeSpec(id = "lr",   label = "LR",   xFrac = 0.12f, yFrac = 0.22f, radiusDp = 26f), // Linear Regression
    NodeSpec(id = "logr", label = "LogR", xFrac = 0.68f, yFrac = 0.18f, radiusDp = 28f), // Logistic Regression
    NodeSpec(id = "knn",  label = "kNN",  xFrac = 0.32f, yFrac = 0.38f, radiusDp = 26f),
    NodeSpec(id = "nb",   label = "NB",   xFrac = 0.78f, yFrac = 0.36f, radiusDp = 24f),
    NodeSpec(id = "svm",  label = "SVM",  xFrac = 0.22f, yFrac = 0.58f, radiusDp = 28f),
    NodeSpec(id = "dt",   label = "DT",   xFrac = 0.52f, yFrac = 0.52f, radiusDp = 26f),
    NodeSpec(id = "rf",   label = "RF",   xFrac = 0.10f, yFrac = 0.80f, radiusDp = 24f),
    NodeSpec(id = "gb",   label = "GB",   xFrac = 0.46f, yFrac = 0.86f, radiusDp = 28f),
    NodeSpec(id = "km",   label = "kM",   xFrac = 0.78f, yFrac = 0.74f, radiusDp = 26f)
)

/** Пример связей, чтобы получалась «сеточка». */
fun defaultEdges(): List<EdgeSpec> = listOf(
    EdgeSpec("lr", "knn"),
    EdgeSpec("lr", "svm"),
    EdgeSpec("knn", "dt"),
    EdgeSpec("dt", "svm"),
    EdgeSpec("dt", "gb"),
    EdgeSpec("rf", "gb"),
    EdgeSpec("gb", "km"),
    EdgeSpec("svm", "logr"),
    EdgeSpec("logr", "nb"),
    EdgeSpec("nb", "km")
)