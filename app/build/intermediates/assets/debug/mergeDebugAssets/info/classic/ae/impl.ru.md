## Программная реализация автокодировщика

### Реализация на Python

```python
import numpy as np


class Dense:
    """Один полносвязный слой с ручным прямым и обратным проходом."""
    def __init__(self, n_in, n_out, activation, seed):
        rng = np.random.default_rng(seed)
        self.W = rng.uniform(-0.5, 0.5, size=(n_out, n_in))
        self.b = np.zeros(n_out)
        self.activation = activation  # "relu" или "linear"

    def forward(self, x):
        z = self.W @ x + self.b
        a = np.maximum(0, z) if self.activation == "relu" else z
        self.last_x, self.last_z = x, z
        return a

    def backward(self, grad_a, grad_accum):
        grad_z = grad_a * (self.last_z > 0) if self.activation == "relu" else grad_a
        grad_accum[0] += np.outer(grad_z, self.last_x)   # dL/dW
        grad_accum[1] += grad_z                            # dL/db
        return self.W.T @ grad_z                            # градиент, передаваемый дальше назад

    def apply(self, grad_accum, lr, n):
        self.W -= lr * grad_accum[0] / n
        self.b -= lr * grad_accum[1] / n


class Autoencoder:
    def __init__(self, bottleneck=1, hidden=6, seed=7):
        self.enc1 = Dense(2, hidden, "relu", seed + 1)
        self.enc2 = Dense(hidden, bottleneck, "linear", seed + 2)
        self.dec1 = Dense(bottleneck, hidden, "relu", seed + 3)
        self.dec2 = Dense(hidden, 2, "linear", seed + 4)
        self.layers = [self.enc1, self.enc2, self.dec1, self.dec2]

    def forward(self, x):
        h1 = self.enc1.forward(x)
        z = self.enc2.forward(h1)          # это и есть сжатый код
        h2 = self.dec1.forward(z)
        x_hat = self.dec2.forward(h2)
        return z, x_hat

    def train_step(self, data, lr):
        accums = {l: [np.zeros_like(l.W), np.zeros_like(l.b)] for l in self.layers}
        for x in data:
            z, x_hat = self.forward(x)
            grad = 2 * (x_hat - x)                       # dL/d(x_hat) для MSE
            for layer in reversed(self.layers):
                grad = layer.backward(grad, accums[layer])
        for layer in self.layers:
            layer.apply(accums[layer], lr, len(data))
```

### Готовое решение: PyTorch

```python
import torch.nn as nn

class Autoencoder(nn.Module):
    def __init__(self, bottleneck=1, hidden=6):
        super().__init__()
        self.encoder = nn.Sequential(nn.Linear(2, hidden), nn.ReLU(), nn.Linear(hidden, bottleneck))
        self.decoder = nn.Sequential(nn.Linear(bottleneck, hidden), nn.ReLU(), nn.Linear(hidden, 2))

    def forward(self, x):
        z = self.encoder(x)
        return self.decoder(z)
```

### А что реально считает интерактив в этом приложении

Тот же принцип на Kotlin — переиспользуется тот же класс `Dense`, что и в теме «Полносвязная сеть» (см. `FcLab.kt`), только теперь их четыре подряд, а функция потерь сравнивает выход не с меткой класса, а с самим входом:

```kotlin
class Autoencoder(bottleneck: Int, hidden: Int, seed: Int) {
    val enc1 = Dense(2, hidden, Activation.RELU, seed + 1)
    val enc2 = Dense(hidden, bottleneck, Activation.LINEAR, seed + 2)
    val dec1 = Dense(bottleneck, hidden, Activation.RELU, seed + 3)
    val dec2 = Dense(hidden, 2, Activation.LINEAR, seed + 4)

    fun forward(x: FloatArray): Pair<FloatArray, FloatArray> {
        val z = enc2.forward(enc1.forward(x))
        val xHat = dec2.forward(dec1.forward(z))
        return z to xHat
    }
}
```

### Важная оговорка

Учебная реализация не включает регуляризацию скрытого кода — это обычный (не вариационный) автокодировщик, который просто сжимает и восстанавливает, но не гарантирует, что случайная точка в пространстве кода даст осмысленный результат при декодировании. Для генерации новых примеров нужен именно VAE (см. раздел «Мат. основа»).
