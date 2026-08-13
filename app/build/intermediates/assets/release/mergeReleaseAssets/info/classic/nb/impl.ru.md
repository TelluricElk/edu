## Программная реализация наивного Байеса

### Реализация на Python

```python
import numpy as np


class GaussianNaiveBayes:
    def fit(self, X, y):
        X = np.array(X, dtype=float)
        y = np.array(y)
        self.classes = np.unique(y)
        self.stats = {}       # класс -> (среднее, дисперсия, априорная вероятность)

        for c in self.classes:
            X_c = X[y == c]
            mean = X_c.mean(axis=0)
            var = X_c.var(axis=0) + 1e-6      # небольшая добавка для устойчивости
            prior = len(X_c) / len(X)
            self.stats[c] = (mean, var, prior)
        return self

    @staticmethod
    def _gaussian_log_prob(x, mean, var):
        return -0.5 * np.log(2 * np.pi * var) - ((x - mean) ** 2) / (2 * var)

    def predict_log_proba(self, x):
        x = np.array(x, dtype=float)
        scores = {}
        for c, (mean, var, prior) in self.stats.items():
            log_prior = np.log(prior)
            log_likelihood = self._gaussian_log_prob(x, mean, var).sum()
            scores[c] = log_prior + log_likelihood
        return scores

    def predict(self, x):
        scores = self.predict_log_proba(x)
        return max(scores, key=scores.get)
```

### Готовое решение: scikit-learn

```python
from sklearn.naive_bayes import GaussianNB

model = GaussianNB()
model.fit(X_train, y_train)
model.predict_proba([[24, 60]])   # вероятности классов для температуры 24°, влажности 60%
model.predict([[24, 60]])
```

### А что реально считает интерактив в этом приложении

```kotlin
fun gaussianLogProb(x: Float, mean: Float, variance: Float): Float {
    val safeVar = variance + 1e-6f
    return (-0.5f * ln(2f * PI.toFloat() * safeVar)
        - (x - mean) * (x - mean) / (2f * safeVar))
}

fun classify(point: WeatherPoint, stats: Map<Boolean, ClassStats>): Boolean {
    return stats.maxByOrNull { (_, s) ->
        ln(s.prior) +
            gaussianLogProb(point.temperature, s.meanTemp, s.varTemp) +
            gaussianLogProb(point.humidity, s.meanHumidity, s.varHumidity)
    }!!.key
}
```

Именно эта функция вызывается при каждом тапе по графику в интерактиве — среднее, дисперсия и априорная вероятность для каждого класса пересчитываются по обучающей выборке заранее, один раз.

### Важная оговорка

Гауссовский наивный Байес предполагает, что признаки внутри каждого класса распределены нормально — это упрощение, которое не всегда верно. Для дискретных признаков (например, категорий или частот слов) используют другие варианты: Multinomial Naive Bayes или Bernoulli Naive Bayes.
