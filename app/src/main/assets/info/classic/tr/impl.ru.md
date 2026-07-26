## Программная реализация self-attention

### Реализация на Python

```python
import numpy as np


def softmax(x, axis=-1):
    x = x - np.max(x, axis=axis, keepdims=True)   # для численной устойчивости
    e = np.exp(x)
    return e / np.sum(e, axis=axis, keepdims=True)


def self_attention(X, d_k=None):
    """X — матрица эмбеддингов слов (n_words, d_model).
    В этой упрощённой версии Q = K = V = X (единичные веса-проекции —
    честная демонстрация механизма без обучения самих проекций)."""
    Q, K, V = X, X, X
    d_k = d_k or X.shape[-1]

    scores = (Q @ K.T) / np.sqrt(d_k)      # (n_words, n_words)
    weights = softmax(scores, axis=-1)      # каждая строка суммируется в 1
    output = weights @ V                     # (n_words, d_model)
    return output, weights


def positional_encoding(n_positions, d_model):
    """Точная формула из статьи — фиксированная, без единого обучаемого параметра."""
    pe = np.zeros((n_positions, d_model))
    positions = np.arange(n_positions)[:, None]
    dims = np.arange(0, d_model, 2)
    div_term = 10000 ** (dims / d_model)
    pe[:, 0::2] = np.sin(positions / div_term)
    pe[:, 1::2] = np.cos(positions / div_term)
    return pe
```

### Готовое решение: PyTorch

```python
import torch.nn as nn

attn = nn.MultiheadAttention(embed_dim=64, num_heads=4, batch_first=True)
output, weights = attn(query, key, value)   # с настоящими обученными проекциями
```

### А что реально считает интерактив в этом приложении

Тот же алгоритм на Kotlin — настоящий softmax, настоящее скалярное произведение:

```kotlin
fun selfAttention(embeddings: Array<FloatArray>): Pair<Array<FloatArray>, Array<FloatArray>> {
    val n = embeddings.size
    val d = embeddings[0].size
    val scale = sqrt(d.toFloat())

    val scores = Array(n) { i -> FloatArray(n) { j -> dot(embeddings[i], embeddings[j]) / scale } }
    val weights = Array(n) { i -> softmax(scores[i]) }          // настоящий softmax по строке
    val output = Array(n) { i ->
        FloatArray(d) { k -> (0 until n).sumOf { j -> (weights[i][j] * embeddings[j][k]).toDouble() }.toFloat() }
    }
    return output to weights
}

fun positionalEncoding(nPositions: Int, dModel: Int): Array<FloatArray> {
    // точная формула sin/cos из раздела "Мат. основа" — вычисляется, а не обучается
}
```

### Важная оговорка

В этой реализации сознательно опущены обучаемые проекции Q/K/V (взяты единичными) и многоголовость (используется одна "голова") — это сделано специально, чтобы честно показать механизм attention без притворного обучения. Позиционное кодирование, напротив, реализовано **полностью точно** — это и в настоящем трансформере всего лишь формула, а не обучаемый параметр.
