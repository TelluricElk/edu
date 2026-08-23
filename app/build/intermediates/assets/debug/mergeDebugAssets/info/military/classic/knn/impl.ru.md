### Реализация на Python

Ниже — самодостаточный класс без сторонних ML-библиотек, чтобы был виден каждый шаг алгоритма: хранение данных, расстояние, отбор соседей, голосование.

```python
import numpy as np
from collections import Counter


class KNNClassifier:
    """Простая, прозрачная реализация k-NN на чистом numpy.
    Никакого "обучения" в привычном смысле нет — fit() просто
    запоминает данные, вся работа происходит в predict()."""

    def __init__(self, k=5, metric="euclidean", weighting="uniform"):
        self.k = k
        self.metric = metric
        self.weighting = weighting

    def fit(self, X, y):
        self.X_train = np.array(X, dtype=float)
        self.y_train = np.array(y)
        return self

    def _distance(self, point):
        diff = self.X_train - point
        if self.metric == "euclidean":
            return np.sqrt(np.sum(diff ** 2, axis=1))
        elif self.metric == "manhattan":
            return np.sum(np.abs(diff), axis=1)
        raise ValueError(f"Неизвестная метрика: {self.metric}")

    def predict_one(self, point):
        distances = self._distance(np.array(point, dtype=float))
        nearest_idx = np.argsort(distances)[: self.k]
        neighbor_labels = self.y_train[nearest_idx]
        neighbor_dist = distances[nearest_idx]

        if self.weighting == "distance":
            weights = 1.0 / (neighbor_dist + 0.05)
        else:
            weights = np.ones_like(neighbor_dist)

        votes = Counter()
        for label, weight in zip(neighbor_labels, weights):
            votes[label] += weight
        return votes.most_common(1)[0][0]

    def predict(self, X):
        return np.array([self.predict_one(point) for point in X])

    def score(self, X, y):
        predictions = self.predict(X)
        return float(np.mean(predictions == np.array(y)))
```

Пример использования — тот же датасет с воздушными целями, что в интерактиве (скорость, высота):

```python
X_train = [
    [7.3, 6.2], [7.6, 6.0], [7.0, 6.5],   # Свой
    [6.2, 8.4], [6.5, 8.1], [5.9, 8.7],   # Гражданский
    [2.2, 4.0], [1.9, 3.8], [2.5, 4.2],   # Противник
]
y_train = [
    "Свой", "Свой", "Свой",
    "Гражданский", "Гражданский", "Гражданский",
    "Противник", "Противник", "Противник",
]

model = KNNClassifier(k=5, metric="euclidean", weighting="distance")
model.fit(X_train, y_train)

new_target = [7.0, 6.0]
print(model.predict_one(new_target))   # -> "Свой"
print(model.score(X_train, y_train))   # точность на выборке, 0..1
```

### Готовое решение: scikit-learn

В реальном проекте писать k-NN с нуля почти никогда не нужно — для этого есть проверенная, оптимизированная реализация:

```python
from sklearn.neighbors import KNeighborsClassifier

model = KNeighborsClassifier(n_neighbors=5, metric="euclidean", weights="distance")
model.fit(X_train, y_train)

model.predict([[7.0, 6.0]])
model.score(X_test, y_test)
```

Разница не только в удобстве: `scikit-learn` под капотом использует KD-дерево или Ball-дерево (параметр `algorithm`), поэтому поиск соседей на больших датасетах происходит на порядки быстрее, чем перебор расстояний до каждой точки вручную.

### А что реально считает интерактив в этом приложении

Приложение написано на Kotlin, и логика внутри интерактива — прямой аналог Python-версии выше, только без numpy (датасет маленький, векторизация не нужна):

```kotlin
data class AircraftPoint(val speed: Float, val altitude: Float, val label: String)

enum class KnnMetric { EUCLIDEAN, MANHATTAN }
enum class KnnWeighting { UNIFORM, DISTANCE }

fun classify(
    speed: Float, altitude: Float, k: Int,
    metric: KnnMetric, weighting: KnnWeighting, data: List<AircraftPoint>
): String {
    val neighbors = data
        .map { it to distance(it, speed, altitude, metric) }
        .sortedBy { it.second }
        .take(k)

    val votes = mutableMapOf<String, Float>()
    neighbors.forEach { (point, dist) ->
        val weight = if (weighting == KnnWeighting.DISTANCE) 1f / (dist + 0.05f) else 1f
        votes[point.label] = (votes[point.label] ?: 0f) + weight
    }
    return votes.maxByOrNull { it.value }?.key ?: "Свой"
}
```

Поведение один в один совпадает с Python-версией: `fit()` там и хранение `data: List<AircraftPoint>` здесь — одно и то же "запоминание" данных; `predict_one()` и `classify()` — один и тот же перебор расстояний и голосование.

### Где именно это используется в приложении

- В **интерактиве** каждый тап по графику вызывает `classify(...)` для точки под пальцем.
- Фон-«карта решений» на графике — это `classify(...)`, вызванный примерно для 700 точек сетки (26×26), каждая закрашена цветом предсказанного класса.
- В **решении задачи** показывается точность с зафиксированными «эталонными» параметрами (k = 5, евклидова метрика, взвешивание по расстоянию) — аналог `model.score(X_test, y_test)` из Python-примера.

### Важная оговорка

Обе реализации — учебные: без KD-деревьев, без нормализации признаков, без кросс-валидации по нескольким разбиениям, и без реального радиолокационного или иного сенсорного контура — это условная симуляция на синтетических точках для отработки самого принципа k-NN. Для промышленного использования — `sklearn.neighbors.KNeighborsClassifier` (Python) или библиотека с аналогичной оптимизированной реализацией под ваш стек.
