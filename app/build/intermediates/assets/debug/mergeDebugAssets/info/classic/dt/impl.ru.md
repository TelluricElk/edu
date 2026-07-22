## Программная реализация дерева решений

### Реализация на Python

Настоящий CART: рекурсивное бинарное разбиение по индексу Джини.

```python
import numpy as np


class Node:
    def __init__(self, feature=None, threshold=None, left=None, right=None, prediction=None):
        self.feature = feature
        self.threshold = threshold
        self.left = left
        self.right = right
        self.prediction = prediction     # заполнено только у листьев


def gini(y):
    _, counts = np.unique(y, return_counts=True)
    p = counts / len(y)
    return 1.0 - np.sum(p ** 2)


def best_split(X, y):
    best_gain, best_feature, best_threshold = -1, None, None
    parent_impurity = gini(y)

    for feature in range(X.shape[1]):
        thresholds = np.unique(X[:, feature])
        for t in thresholds:
            left_mask = X[:, feature] <= t
            if left_mask.sum() == 0 or left_mask.sum() == len(y):
                continue
            left_impurity = gini(y[left_mask])
            right_impurity = gini(y[~left_mask])
            n = len(y)
            weighted = (left_mask.sum() / n) * left_impurity + ((~left_mask).sum() / n) * right_impurity
            gain = parent_impurity - weighted
            if gain > best_gain:
                best_gain, best_feature, best_threshold = gain, feature, t

    return best_feature, best_threshold, best_gain


def build_tree(X, y, depth=0, max_depth=4, min_samples_split=4):
    if len(np.unique(y)) == 1 or len(y) < min_samples_split or depth >= max_depth:
        values, counts = np.unique(y, return_counts=True)
        return Node(prediction=values[np.argmax(counts)])

    feature, threshold, gain = best_split(X, y)
    if feature is None or gain <= 0:
        values, counts = np.unique(y, return_counts=True)
        return Node(prediction=values[np.argmax(counts)])

    left_mask = X[:, feature] <= threshold
    left = build_tree(X[left_mask], y[left_mask], depth + 1, max_depth, min_samples_split)
    right = build_tree(X[~left_mask], y[~left_mask], depth + 1, max_depth, min_samples_split)
    return Node(feature=feature, threshold=threshold, left=left, right=right)


def predict_one(node, x):
    if node.prediction is not None:
        return node.prediction
    branch = node.left if x[node.feature] <= node.threshold else node.right
    return predict_one(branch, x)
```

### Готовое решение: scikit-learn

```python
from sklearn.tree import DecisionTreeClassifier

model = DecisionTreeClassifier(criterion="gini", max_depth=4, min_samples_split=4)
model.fit(X_train, y_train)
model.predict(X_test)
```

### А что реально считает интерактив в этом приложении

Тот же CART-алгоритм на Kotlin — рекурсивное построение, реальный расчёт Джини на каждом узле:

```kotlin
data class TreeNode(
    val feature: Int? = null,
    val threshold: Float? = null,
    val left: TreeNode? = null,
    val right: TreeNode? = null,
    val prediction: String? = null
)

fun gini(labels: List<String>): Float {
    val counts = labels.groupingBy { it }.eachCount()
    val n = labels.size.toFloat()
    return 1f - counts.values.sumOf { (it / n).toDouble().let { p -> p * p } }.toFloat()
}

fun buildTree(data: List<CreditPoint>, depth: Int, maxDepth: Int): TreeNode {
    if (depth >= maxDepth || data.map { it.label }.distinct().size == 1) {
        return TreeNode(prediction = data.groupingBy { it.label }.eachCount().maxByOrNull { it.value }!!.key)
    }
    val (feature, threshold) = findBestSplit(data) ?: return leafNode(data)
    val (left, right) = data.partition { splitValue(it, feature) <= threshold }
    return TreeNode(feature, threshold, buildTree(left, depth + 1, maxDepth), buildTree(right, depth + 1, maxDepth))
}
```

### Важная оговорка

Учебная реализация перебирает пороги полным перебором уникальных значений признака — на больших данных это медленно. Промышленные библиотеки используют предварительную сортировку и гистограммный подход (например, `LightGBM` и современный `sklearn`) для ускорения поиска разбиений.
