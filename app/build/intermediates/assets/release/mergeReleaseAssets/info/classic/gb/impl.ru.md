## Программная реализация градиентного бустинга

### Реализация на Python

Упрощённый градиентный бустинг для регрессии на "пеньках" (деревья глубины 1):

```python
import numpy as np


class Stump:
    """Дерево глубины 1: один порог по одному признаку."""
    def fit(self, x, residuals):
        best_sse, best_threshold = np.inf, None
        for t in np.unique(x):
            left = residuals[x <= t]
            right = residuals[x > t]
            if len(left) == 0 or len(right) == 0:
                continue
            sse = np.sum((left - left.mean()) ** 2) + np.sum((right - right.mean()) ** 2)
            if sse < best_sse:
                best_sse, best_threshold = sse, t
                self.left_value = left.mean()
                self.right_value = right.mean()
        self.threshold = best_threshold
        return self

    def predict(self, x):
        return np.where(x <= self.threshold, self.left_value, self.right_value)


class GradientBoostingRegressor:
    def __init__(self, n_estimators=50, learning_rate=0.1):
        self.n_estimators = n_estimators
        self.learning_rate = learning_rate
        self.trees = []

    def fit(self, x, y):
        x = np.array(x, dtype=float)
        y = np.array(y, dtype=float)
        self.f0 = y.mean()
        predictions = np.full_like(y, self.f0)

        for _ in range(self.n_estimators):
            residuals = y - predictions            # антиградиент для MSE — просто остаток
            tree = Stump().fit(x, residuals)
            predictions += self.learning_rate * tree.predict(x)
            self.trees.append(tree)
        return self

    def predict(self, x):
        x = np.array(x, dtype=float)
        result = np.full_like(x, self.f0)
        for tree in self.trees:
            result += self.learning_rate * tree.predict(x)
        return result
```

### Готовое решение: scikit-learn / XGBoost / LightGBM

```python
from sklearn.ensemble import GradientBoostingRegressor

model = GradientBoostingRegressor(n_estimators=100, learning_rate=0.1, max_depth=2)
model.fit(X_train, y_train)
model.predict(X_test)
```

В индустрии для градиентного бустинга чаще используют специализированные библиотеки — `XGBoost`, `LightGBM`, `CatBoost`: они работают на порядки быстрее за счёт гистограммных методов поиска разбиений и умеют работать с миллионами строк.

### А что реально считает интерактив в этом приложении

```kotlin
fun trainBoosting(data: List<PricePoint>, nEstimators: Int, learningRate: Float): List<Stump> {
    val f0 = data.map { it.price }.average().toFloat()
    var predictions = FloatArray(data.size) { f0 }
    val trees = mutableListOf<Stump>()

    repeat(nEstimators) {
        val residuals = data.mapIndexed { i, p -> p.price - predictions[i] }
        val stump = fitStump(data.map { it.area }, residuals)
        trees.add(stump)
        predictions = predictions.mapIndexed { i, pred -> pred + learningRate * stump.predict(data[i].area) }.toFloatArray()
    }
    return trees
}
```

### Важная оговорка

Учебная реализация ограничена "пеньками" (глубина 1) и полным перебором порогов — как и в теме «Дерево решений», промышленные библиотеки используют гистограммные приближения для скорости на больших данных.
