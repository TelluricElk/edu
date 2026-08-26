## Программная реализация линейной регрессии

### Реализация на Python

```python
import numpy as np


class LinearRegressionGD:
    """Линейная регрессия с обучением градиентным спуском —
    показывает каждый шаг, а не просто вызывает готовую формулу."""

    def __init__(self, learning_rate=0.01, epochs=200):
        self.learning_rate = learning_rate
        self.epochs = epochs
        self.w1 = 0.0
        self.w0 = 0.0
        self.loss_history = []

    def fit(self, x, y):
        x = np.array(x, dtype=float)
        y = np.array(y, dtype=float)
        n = len(x)

        for _ in range(self.epochs):
            predictions = self.w1 * x + self.w0
            errors = predictions - y

            grad_w1 = (2 / n) * np.sum(errors * x)
            grad_w0 = (2 / n) * np.sum(errors)

            self.w1 -= self.learning_rate * grad_w1
            self.w0 -= self.learning_rate * grad_w0

            mse = np.mean(errors ** 2)
            self.loss_history.append(mse)
        return self

    def predict(self, x):
        return self.w1 * np.array(x, dtype=float) + self.w0
```

Аналитическое решение (метод наименьших квадратов) — то, с чем можно сверять результат градиентного спуска:

```python
def closed_form(x, y):
    x = np.array(x, dtype=float)
    y = np.array(y, dtype=float)
    X = np.vstack([x, np.ones_like(x)]).T   # добавляем столбец единиц для w0
    w1, w0 = np.linalg.lstsq(X, y, rcond=None)[0]
    return w1, w0
```

Пример использования — тот же датасет «дальность → расход топлива», что и в интерактиве:

```python
distances = [32.1, 48.5, 61.0, 75.4, 90.2, 110.7]   # км
fuel = [72.9, 105.3, 128.6, 155.1, 183.4, 220.8]     # литров

model = LinearRegressionGD(learning_rate=0.05, epochs=300)
model.fit(distances, fuel)
model.predict([75])   # прогноз расхода топлива для маршрута 75 км
```

### Готовое решение: scikit-learn

```python
from sklearn.linear_model import LinearRegression

model = LinearRegression()
model.fit(X_train, y_train)     # X_train — двумерный массив [[дальность1], [дальность2], ...]
model.predict([[75]])           # прогноз для маршрута 75 км
model.score(X_test, y_test)     # R² на контрольной выборке
```

### А что реально считает интерактив в этом приложении

В интерактиве реализован настоящий градиентный спуск на Kotlin — с одним важным отличием от «наивной» версии выше: расчёт ведётся в **нормализованных координатах** (среднее 0, стандартное отклонение 1). Дальность маршрута и расход топлива — числа разного и достаточно большого масштаба, поэтому «сырой» градиент получается огромным, и любая интуитивно разумная скорость обучения либо ничего не меняет, либо мгновенно приводит к расхождению. Нормализация — стандартный приём, решающий именно эту проблему:

```kotlin
private fun gradientStepNormalized(
    data: List<FuelPoint>,
    a: Float, b: Float,
    lr: Float
): Pair<Float, Float> {
    var gradA = 0f
    var gradB = 0f
    data.forEach { point ->
        val xn = (point.distance - distanceMean) / distanceStd
        val yn = (point.fuel - fuelMean) / fuelStd
        val error = (a * xn + b) - yn
        gradA += error * xn
        gradB += error
    }
    gradA = 2f * gradA / data.size
    gradB = 2f * gradB / data.size
    return (a - lr * gradA) to (b - lr * gradB)
}

// После обучения нормализованные веса (a, b) пересчитываются обратно
// в реальные единицы (литры топлива от километров дальности) для отображения:
private fun toRealWeights(a: Float, b: Float): Pair<Float, Float> {
    val w1 = a * fuelStd / distanceStd
    val w0 = fuelMean + fuelStd * b - w1 * distanceMean
    return w1 to w0
}
```

Функция вызывается `epochs` раз подряд — ровно столько, сколько выбрано ползунком. Никакого притворства: если поставить слишком большую скорость обучения (в нормализованных координатах — примерно от 1,0), веса действительно начинают колебаться и расходиться, и это видно на графике и в предупреждении.

### Важная оговорка

Учебная реализация не включает нормализацию признаков и адаптивные оптимизаторы (Adam, RMSProp) — в реальных проектах для устойчивости и скорости сходимости используют именно их, а также `scikit-learn`/`PyTorch` вместо ручного цикла. Реальный расход топлива, разумеется, зависит от куда большего числа факторов (рельеф, погода, тип техники) — здесь он намеренно упрощён до одного признака ради ясности самого принципа линейной регрессии.
