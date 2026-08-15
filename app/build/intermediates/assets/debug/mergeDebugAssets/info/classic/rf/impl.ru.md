## Программная реализация случайного леса

### Реализация на Python

Случайный лес — это просто много деревьев (реализацию одного дерева см. в теме «Дерево решений») плюс бэггинг:

```python
import numpy as np


class RandomForest:
    def __init__(self, n_estimators=20, max_depth=4, seed=0):
        self.n_estimators = n_estimators
        self.max_depth = max_depth
        self.rng = np.random.default_rng(seed)
        self.trees = []

    def _bootstrap_sample(self, X, y):
        n = len(X)
        idx = self.rng.integers(0, n, size=n)   # выбор с возвращением
        return X[idx], y[idx]

    def fit(self, X, y):
        X = np.array(X, dtype=float)
        y = np.array(y)
        self.trees = []
        for _ in range(self.n_estimators):
            X_sample, y_sample = self._bootstrap_sample(X, y)
            tree = build_tree(X_sample, y_sample, max_depth=self.max_depth)   # из темы "Дерево решений"
            self.trees.append(tree)
        return self

    def predict(self, x):
        votes = [predict_one(tree, x) for tree in self.trees]
        values, counts = np.unique(votes, return_counts=True)
        return values[np.argmax(counts)]
```

### Готовое решение: scikit-learn

```python
from sklearn.ensemble import RandomForestClassifier

model = RandomForestClassifier(n_estimators=100, max_depth=4, max_features="sqrt")
model.fit(X_train, y_train)
model.predict(X_test)
model.feature_importances_    # важность каждого признака
```

### А что реально считает интерактив в этом приложении

```kotlin
fun trainForest(data: List<CreditPoint>, nTrees: Int, maxDepth: Int, criterion: DtCriterion): List<DtNode> {
    return (0 until nTrees).map { seed ->
        val bootstrap = bootstrapSample(data, seed)
        DtLab.buildTree(bootstrap, criterion, maxDepth, minSamplesSplit = 4)
    }
}

fun predictForest(trees: List<DtNode>, point: CreditPoint): Boolean {
    val votes = trees.map { DtLab.predict(it, point) }
    return votes.count { it } >= trees.size - votes.count { it }
}
```

Каждое дерево обучается на своей бутстрап-выборке (функция `bootstrapSample` использует `seed`, чтобы деревья отличались друг от друга, но результат оставался воспроизводимым).

### Важная оговорка

В интерактиве всего два признака, поэтому случайный выбор *подмножества* признаков не даёт эффекта (нечего выбирать) — учебная реализация полагается только на бэггинг. В реальных задачах с десятками и сотнями признаков оба источника случайности (бутстрап + случайные признаки) работают вместе.
