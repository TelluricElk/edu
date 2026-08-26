## Программная реализация SOM

### Реализация на Python

```python
import numpy as np


class SOM:
    def __init__(self, grid_size=12, input_dim=3, seed=0):
        rng = np.random.default_rng(seed)
        self.grid_size = grid_size
        # Каждый нейрон сетки — вектор весов той же размерности, что вход (RGB = 3)
        self.weights = rng.random((grid_size, grid_size, input_dim))

    def find_bmu(self, x):
        distances = np.linalg.norm(self.weights - x, axis=2)
        return np.unravel_index(np.argmin(distances), distances.shape)

    def train(self, data, iterations, lr0=0.5, radius0=None):
        if radius0 is None:
            radius0 = self.grid_size / 2
        grid_x, grid_y = np.meshgrid(np.arange(self.grid_size), np.arange(self.grid_size), indexing="ij")

        for t in range(iterations):
            x = data[np.random.randint(len(data))]
            bmu = self.find_bmu(x)

            # Экспоненциально убывающие скорость обучения и радиус
            progress = t / iterations
            lr = lr0 * np.exp(-progress)
            radius = radius0 * np.exp(-progress)

            # Расстояние каждого нейрона до BMU на самой решётке (не в пространстве RGB!)
            grid_dist_sq = (grid_x - bmu[0]) ** 2 + (grid_y - bmu[1]) ** 2
            neighborhood = np.exp(-grid_dist_sq / (2 * radius ** 2 + 1e-6))

            # Обновляем ВСЕ нейроны сразу, с разной силой (векторизовано)
            self.weights += (lr * neighborhood)[:, :, None] * (x - self.weights)
```

### Готовое решение: MiniSom

```python
from minisom import MiniSom

som = MiniSom(x=12, y=12, input_len=3, sigma=6.0, learning_rate=0.5)
som.random_weights_init(colors)
som.train_random(colors, num_iteration=2000)
```

### А что реально считает интерактив в этом приложении

Тот же алгоритм на Kotlin — тот же поиск победителя и то же обновление всей сетки на каждом шаге:

```kotlin
fun trainStep(weights: Array<Array<FloatArray>>, input: FloatArray, t: Int, totalSteps: Int) {
    val bmu = findBmu(weights, input)
    val progress = t.toFloat() / totalSteps
    val lr = LR0 * exp(-progress)
    val radius = RADIUS0 * exp(-progress)

    for (gx in weights.indices) {
        for (gy in weights[0].indices) {
            val gridDistSq = ((gx - bmu.first) * (gx - bmu.first) + (gy - bmu.second) * (gy - bmu.second)).toFloat()
            val neighborhood = exp(-gridDistSq / (2f * radius * radius + 1e-6f))
            val factor = lr * neighborhood
            for (d in input.indices) {
                weights[gx][gy][d] += factor * (input[d] - weights[gx][gy][d])
            }
        }
    }
}
```

### Важная оговорка

Обновление всех нейронов сетки на каждом шаге (а не только победителя и ближайших соседей) — намеренное упрощение для маленькой сетки 12×12. На больших картах для скорости обычно ограничивают обновление лишь окрестностью в несколько клеток вокруг победителя, где `neighborhood` не пренебрежимо мала.
