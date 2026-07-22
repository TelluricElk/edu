## Программная реализация SVM

### Реализация на Python

Ниже — алгоритм **Pegasos** (Primal Estimated sub-GrAdient SOlver) — простой и настоящий стохастический метод обучения линейного SVM, без сторонних библиотек:

```python
import numpy as np


class PegasosSVM:
    """Линейный SVM, обучаемый стохастическим субградиентным спуском
    (алгоритм Pegasos, Shalev-Shwartz et al., 2007)."""

    def __init__(self, C=1.0, epochs=200, seed=0):
        self.C = C
        self.epochs = epochs
        self.rng = np.random.default_rng(seed)
        self.w = None
        self.b = 0.0

    def fit(self, X, y):
        X = np.array(X, dtype=float)
        y = np.array(y, dtype=float)      # метки должны быть -1 / +1
        n, d = X.shape
        self.w = np.zeros(d)
        lam = 1.0 / (self.C * n)          # связь регуляризации с параметром C

        for t in range(1, self.epochs * n + 1):
            i = self.rng.integers(0, n)
            eta = 1.0 / (lam * t)
            margin = y[i] * (X[i] @ self.w + self.b)

            if margin < 1:
                self.w = (1 - eta * lam) * self.w + eta * y[i] * X[i]
                self.b += eta * y[i]
            else:
                self.w = (1 - eta * lam) * self.w
        return self

    def decision_function(self, X):
        return np.array(X, dtype=float) @ self.w + self.b

    def predict(self, X):
        return np.sign(self.decision_function(X))
```

### Готовое решение: scikit-learn

```python
from sklearn.svm import SVC

model = SVC(kernel="rbf", C=1.0, gamma="scale")   # или kernel="linear"
model.fit(X_train, y_train)
model.predict(X_test)
model.support_vectors_    # координаты опорных векторов
```

### А что реально считает интерактив в этом приложении

В интерактиве реализован тот же алгоритм Pegasos на Kotlin — с реальными шагами субградиентного спуска, а не заглушкой:

```kotlin
fun trainPegasos(data: List<LabeledPoint>, c: Float, epochs: Int): Pair<FloatArray, Float> {
    val n = data.size
    val lambda = 1f / (c * n)
    var w = floatArrayOf(0f, 0f)
    var b = 0f
    var t = 1

    repeat(epochs) {
        data.shuffled().forEach { point ->
            val eta = 1f / (lambda * t)
            val margin = point.label * (dot(w, point.features) + b)
            if (margin < 1f) {
                w = subtractScaled(w, eta * lambda, w)
                w = addScaled(w, eta * point.label, point.features)
                b += eta * point.label
            } else {
                w = subtractScaled(w, eta * lambda, w)
            }
            t++
        }
    }
    return w to b
}
```

### Важная оговорка

Pegasos обучает **линейный** SVM. Для ядровых (нелинейных) версий в интерактиве используется упрощённая ядровая модификация того же принципа — полноценный SMO-солвер (как в `libsvm`, на который опирается `scikit-learn`) значительно сложнее и выходит за рамки учебной реализации.
