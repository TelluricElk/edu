## Программная реализация полносвязной сети

### Реализация на Python

Сеть «2 входа → k нейронов ReLU → 1 сигмоида» с обучением через обратное распространение ошибки, написанная на чистом numpy:

```python
import numpy as np


class TinyMLP:
    def __init__(self, hidden_size=4, learning_rate=0.5, seed=0):
        rng = np.random.default_rng(seed)
        self.lr = learning_rate
        # Инициализация небольшими случайными числами — иначе ReLU-нейроны
        # могут "заглохнуть" (всегда выдавать 0) с самого начала
        self.W1 = rng.normal(0, 0.8, size=(hidden_size, 2))
        self.b1 = np.zeros(hidden_size)
        self.W2 = rng.normal(0, 0.8, size=(1, hidden_size))
        self.b2 = np.zeros(1)

    @staticmethod
    def relu(z):
        return np.maximum(0, z)

    @staticmethod
    def sigmoid(z):
        return 1 / (1 + np.exp(-z))

    def forward(self, x):
        z1 = self.W1 @ x + self.b1
        h = self.relu(z1)
        z2 = self.W2 @ h + self.b2
        p = self.sigmoid(z2)
        return z1, h, p

    def train_step(self, X, y):
        grad_W1 = np.zeros_like(self.W1)
        grad_b1 = np.zeros_like(self.b1)
        grad_W2 = np.zeros_like(self.W2)
        grad_b2 = np.zeros_like(self.b2)

        for x, target in zip(X, y):
            z1, h, p = self.forward(x)
            error = p - target                      # dL/dp * dp/dz2 (сигмоида + log-loss упрощаются)

            grad_W2 += np.outer(error, h)
            grad_b2 += error

            dh = self.W2.T.flatten() * error         # градиент, пришедший от выходного слоя
            dz1 = dh * (z1 > 0)                       # производная ReLU: 1, если z1>0, иначе 0

            grad_W1 += np.outer(dz1, x)
            grad_b1 += dz1

        n = len(X)
        self.W1 -= self.lr * grad_W1 / n
        self.b1 -= self.lr * grad_b1 / n
        self.W2 -= self.lr * grad_W2 / n
        self.b2 -= self.lr * grad_b2 / n
```

### Готовое решение: PyTorch

```python
import torch.nn as nn

model = nn.Sequential(
    nn.Linear(2, 8),
    nn.ReLU(),
    nn.Linear(8, 1),
    nn.Sigmoid()
)
```

### А что реально считает интерактив в этом приложении

Тот же алгоритм на Kotlin — та же логика прямого и обратного прохода, только без матричных библиотек (сеть маленькая, циклы по нейронам вполне достаточно быстры):

```kotlin
class TinyMlp(hiddenSize: Int, private val lr: Float, seed: Int) {
    private val rnd = Random(seed)
    val w1 = Array(hiddenSize) { floatArrayOf(rnd.nextGaussian() * 0.8f, rnd.nextGaussian() * 0.8f) }
    val b1 = FloatArray(hiddenSize)
    val w2 = FloatArray(hiddenSize) { rnd.nextGaussian() * 0.8f }
    var b2 = 0f

    fun forward(x1: Float, x2: Float): Triple<FloatArray, FloatArray, Float> {
        val z1 = FloatArray(w1.size) { i -> w1[i][0] * x1 + w1[i][1] * x2 + b1[i] }
        val h = FloatArray(z1.size) { i -> max(0f, z1[i]) }               // ReLU
        val z2 = h.indices.sumOf { (h[it] * w2[it]).toDouble() }.toFloat() + b2
        val p = 1f / (1f + exp(-z2))                                      // сигмоида
        return Triple(z1, h, p)
    }

    fun trainStep(data: List<XorPoint>) {
        // накапливаем градиенты по всем точкам, затем один шаг градиентного спуска —
        // полное описание в KnnLab-подобном файле FcLab.kt
    }
}
```

### Важная оговорка

Учебная реализация обучается батч-градиентным спуском по всем точкам сразу (без мини-батчей) и без продвинутых оптимизаторов (Adam, momentum) — для десятков точек и пары нейронов это не нужно, но на реальных задачах именно эти детали часто решают, обучится сеть за разумное время или нет.
