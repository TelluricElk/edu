## Программная реализация логистической регрессии

### Реализация на Python

```python
import numpy as np


class LogisticRegressionGD:
    def __init__(self, learning_rate=0.1, epochs=200):
        self.learning_rate = learning_rate
        self.epochs = epochs
        self.w = 0.0
        self.b = 0.0

    @staticmethod
    def _sigmoid(z):
        return 1.0 / (1.0 + np.exp(-z))

    def fit(self, x, y):
        x = np.array(x, dtype=float)
        y = np.array(y, dtype=float)
        n = len(x)

        for _ in range(self.epochs):
            z = self.w * x + self.b
            p = self._sigmoid(z)
            error = p - y

            grad_w = np.mean(error * x)
            grad_b = np.mean(error)

            self.w -= self.learning_rate * grad_w
            self.b -= self.learning_rate * grad_b
        return self

    def predict_proba(self, x):
        return self._sigmoid(self.w * np.array(x, dtype=float) + self.b)

    def predict(self, x, threshold=0.5):
        return (self.predict_proba(x) >= threshold).astype(int)
```

### Готовое решение: scikit-learn

```python
from sklearn.linear_model import LogisticRegression

model = LogisticRegression()
model.fit(X_train, y_train)
model.predict_proba([[6]])   # вероятности для студента с 6 часами подготовки
model.predict([[6]])         # класс при пороге 0.5
```

### А что реально считает интерактив в этом приложении

```kotlin
private fun sigmoid(z: Float): Float = (1.0 / (1.0 + exp(-z.toDouble()))).toFloat()

fun gradientStep(data: List<StudyPoint>, w: Float, b: Float, lr: Float): Pair<Float, Float> {
    var gradW = 0f
    var gradB = 0f
    data.forEach { point ->
        val p = sigmoid(w * point.hours + b)
        val error = p - point.passed
        gradW += error * point.hours
        gradB += error
    }
    gradW /= data.size
    gradB /= data.size
    return (w - lr * gradW) to (b - lr * gradB)
}
```

Порог классификации применяется отдельно, уже после того как модель выдала вероятность — переобучать модель для этого не нужно, именно поэтому в интерактиве порог двигается мгновенно, без пересчёта весов.

### Важная оговорка

Учебная реализация не включает L2-регуляризацию и не проверяет численную стабильность (переполнение `exp` при больших по модулю *z*) — в `scikit-learn` эти детали уже решены.
