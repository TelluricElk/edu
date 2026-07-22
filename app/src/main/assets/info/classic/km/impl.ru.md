## Программная реализация k-средних

### Реализация на Python

```python
import numpy as np


def kmeans(X, k, n_iter=20, seed=0):
    rng = np.random.default_rng(seed)
    X = np.array(X, dtype=float)
    n = len(X)

    # инициализация: k случайных точек из выборки становятся начальными центроидами
    centroid_idx = rng.choice(n, size=k, replace=False)
    centroids = X[centroid_idx].copy()

    for _ in range(n_iter):
        # шаг присвоения — каждая точка к ближайшему центроиду
        distances = np.linalg.norm(X[:, None, :] - centroids[None, :, :], axis=2)
        labels = distances.argmin(axis=1)

        # шаг обновления — центроид как среднее точек своего кластера
        new_centroids = np.array([
            X[labels == j].mean(axis=0) if np.any(labels == j) else centroids[j]
            for j in range(k)
        ])

        if np.allclose(new_centroids, centroids):
            break
        centroids = new_centroids

    inertia = sum(
        np.sum((X[labels == j] - centroids[j]) ** 2)
        for j in range(k)
    )
    return centroids, labels, inertia
```

### Готовое решение: scikit-learn

```python
from sklearn.cluster import KMeans

model = KMeans(n_clusters=4, init="k-means++", n_init=10)
model.fit(X)
model.labels_       # номер кластера для каждой точки
model.cluster_centers_
model.inertia_
```

### А что реально считает интерактив в этом приложении

```kotlin
fun runKMeans(points: List<CustomerPoint>, k: Int, iterations: Int, seed: Int): KMeansResult {
    val rnd = Random(seed)
    var centroids = points.shuffled(rnd).take(k).map { it.income to it.frequency }.toMutableList()

    repeat(iterations) {
        val assignments = points.map { p -> nearestCentroid(p, centroids) }
        centroids = (0 until k).map { j ->
            val cluster = points.filterIndexed { idx, _ -> assignments[idx] == j }
            if (cluster.isEmpty()) centroids[j]
            else cluster.map { it.income }.average().toFloat() to cluster.map { it.frequency }.average().toFloat()
        }.toMutableList()
    }
    return KMeansResult(centroids, points.map { nearestCentroid(it, centroids) })
}
```

Каждый вызов пересчитывает шаги "присвоение → обновление" ровно столько раз, сколько выбрано ползунком итераций — можно буквально останавливать процесс на середине и смотреть, как центроиды ещё не успели сойтись.

### Важная оговорка

Учебная реализация использует простую случайную инициализацию, а не k-means++ — поэтому в интерактиве специально можно увидеть случаи неудачной сходимости при разных `seed`, это часть демонстрации.
