package com.eduappml.domain

data class Topic(
    val id: String,      // внутренний ID
    val title: String,   // заголовок в UI
    val group: String,   // "classic" | "arch" | др.
    val slug: String     // папка в assets: info/{group}/{slug}/...
)

object Topics {
    // Заполняй по мере надобности
    val all: List<Topic> = listOf(
        Topic(id = "knn",  title = "k-Nearest Neighbors",  group = "classic", slug = "knn"),
        Topic(id = "svm",  title = "Support Vector Machine", group = "classic", slug = "svm"),
        Topic(id = "rf",   title = "Random Forest",         group = "classic", slug = "rf"),
        Topic(id = "dt",   title = "Decision Tree",         group = "classic", slug = "dt"),
        Topic(id = "nb",   title = "Naive Bayes",           group = "classic", slug = "nb"),
        Topic(id = "lr",   title = "Linear Regression",     group = "classic", slug = "lr"),
        Topic(id = "logr", title = "Logistic Regression",   group = "classic", slug = "logr"),
        Topic(id = "km",   title = "K-Means",               group = "classic", slug = "km"),
        Topic(id = "gb",   title = "Gradient Boosting",     group = "classic", slug = "gb"),
        // Пример для архитектур:
        // Topic(id = "cnn", title = "Convolutional NN", group = "arch", slug = "cnn"),
    )

    fun byId(id: String): Topic? = all.find { it.id == id }
}