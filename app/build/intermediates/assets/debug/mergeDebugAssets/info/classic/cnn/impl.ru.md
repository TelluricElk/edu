## Программная реализация свёрточной сети

### Реализация на Python

```python
import numpy as np


class Conv2D:
    def __init__(self, n_filters, k_size, seed):
        rng = np.random.default_rng(seed)
        self.filters = rng.uniform(-0.5, 0.5, size=(n_filters, k_size, k_size))
        self.biases = np.zeros(n_filters)
        self.k = k_size

    def forward(self, x):
        h, w = x.shape
        out_h, out_w = h - self.k + 1, w - self.k + 1
        out = np.zeros((len(self.filters), out_h, out_w))
        for f, kernel in enumerate(self.filters):
            for i in range(out_h):
                for j in range(out_w):
                    patch = x[i:i + self.k, j:j + self.k]
                    out[f, i, j] = np.sum(patch * kernel) + self.biases[f]
        self.last_x, self.last_z = x, out
        return np.maximum(0, out)                      # ReLU сразу внутри слоя

    def backward(self, grad_a, lr):
        grad_z = grad_a * (self.last_z > 0)             # производная ReLU
        grad_filters = np.zeros_like(self.filters)
        for f in range(len(self.filters)):
            for i in range(grad_z.shape[1]):
                for j in range(grad_z.shape[2]):
                    patch = self.last_x[i:i + self.k, j:j + self.k]
                    grad_filters[f] += grad_z[f, i, j] * patch   # градиент накапливается со всех позиций
        self.filters -= lr * grad_filters


class MaxPool2D:
    def forward(self, x):
        n_f, h, w = x.shape
        out = np.zeros((n_f, h // 2, w // 2))
        self.argmax = {}
        for f in range(n_f):
            for i in range(h // 2):
                for j in range(w // 2):
                    region = x[f, i*2:i*2+2, j*2:j*2+2]
                    idx = np.unravel_index(np.argmax(region), region.shape)
                    self.argmax[(f, i, j)] = (i*2 + idx[0], j*2 + idx[1])
                    out[f, i, j] = region[idx]
        return out

    def backward(self, grad_a, in_shape):
        grad_x = np.zeros(in_shape)
        for (f, i, j), (si, sj) in self.argmax.items():
            grad_x[f, si, sj] += grad_a[f, i, j]        # градиент идёт только туда, где был максимум
        return grad_x
```

### Готовое решение: PyTorch

```python
import torch.nn as nn

model = nn.Sequential(
    nn.Conv2d(1, 4, kernel_size=3),   # 4 фильтра 3x3
    nn.ReLU(),
    nn.MaxPool2d(2),
    nn.Flatten(),
    nn.Linear(4 * 3 * 3, 8),
    nn.ReLU(),
    nn.Linear(8, 1),
    nn.Sigmoid()
)
```

### А что реально считает интерактив в этом приложении

Та же логика на Kotlin — `Conv2D` и `MaxPool2D` с ручным forward/backward, а полносвязная "голова" переиспользует уже знакомый класс `Dense` (см. `NeuralPrimitives.kt`):

```kotlin
class Conv2D(nFilters: Int, private val k: Int, seed: Int) {
    val filters: Array<Array<FloatArray>> = /* nFilters x k x k, случайная инициализация */

    fun forward(x: Array<FloatArray>): Array<Array<FloatArray>> {
        // для каждого фильтра и каждой позиции — сумма произведений + ReLU
    }

    fun backward(gradA: Array<Array<FloatArray>>, lr: Float) {
        // градиент по каждому весу фильтра накапливается со ВСЕХ позиций,
        // где этот вес участвовал в прямом проходе
    }
}
```

Карты признаков, которые вы видите в интерактиве на вкладке «фильтры», — это буквально массив `out[f][i][j]`, посчитанный этим кодом для текущего изображения, а не заготовленная заранее картинка.

### Важная оговорка

Учебная реализация использует наивный тройной цикл для свёртки — понятный, но не самый быстрый способ. Промышленные библиотеки (PyTorch, TensorFlow) реализуют свёртку через оптимизированные алгоритмы (im2col + матричное умножение, или прямые GPU-ядра), которые на порядки быстрее, но реализуют математически ту же самую операцию.
