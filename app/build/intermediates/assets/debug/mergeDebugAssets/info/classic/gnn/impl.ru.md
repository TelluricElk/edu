## Программная реализация GNN

### Реализация на Python

```python
import numpy as np


class GNNLayer:
    def __init__(self, in_dim, out_dim, seed):
        rng = np.random.default_rng(seed)
        self.Wself = rng.uniform(-0.5, 0.5, size=(out_dim, in_dim))
        self.Wneigh = rng.uniform(-0.5, 0.5, size=(out_dim, in_dim))
        self.b = np.zeros(out_dim)

    def forward(self, H, adj):
        """H — представления всех узлов (n, in_dim); adj[v] — список соседей узла v."""
        n = len(H)
        agg = np.zeros_like(H)
        for v in range(n):
            if adj[v]:
                agg[v] = np.mean([H[u] for u in adj[v]], axis=0)   # усреднение по соседям
        Z = H @ self.Wself.T + agg @ self.Wneigh.T + self.b
        self.last_H, self.last_agg, self.last_Z = H, agg, Z
        return np.maximum(0, Z)

    def backward(self, gradOut, adj, lr):
        gradZ = gradOut * (self.last_Z > 0)                        # производная ReLU
        gradWself = gradZ.T @ self.last_H
        gradWneigh = gradZ.T @ self.last_agg

        gradH = gradZ @ self.Wself                                  # прямой вклад (v как сам себя)
        for v in range(len(gradOut)):
            for w in adj[v]:                                        # v — сосед w, значит, входит в agg[w]
                gradH[v] += (gradZ[w] @ self.Wneigh) / len(adj[w])   # косвенный вклад

        self.Wself -= lr * gradWself
        self.Wneigh -= lr * gradWneigh
        self.b -= lr * gradZ.sum(axis=0)
        return gradH
```

### Готовое решение: PyTorch Geometric

```python
import torch
from torch_geometric.nn import SAGEConv

conv1 = SAGEConv(in_channels=2, out_channels=4)
conv2 = SAGEConv(in_channels=4, out_channels=4)
# forward: x = conv1(x, edge_index).relu(); x = conv2(x, edge_index).relu()
```

### А что реально считает интерактив в этом приложении

Тот же алгоритм на Kotlin: та же агрегация усреднением, тот же двухпутевой обратный проход через граф. Веса `Wself` и `Wneigh` для каждого слоя — переиспользуемые для всех узлов, ровно как описано в разделе «Мат. основа»:

```kotlin
class GnnLayer(inDim: Int, outDim: Int, seed: Int) {
    val wSelf = /* outDim x inDim */
    val wNeigh = /* outDim x inDim */

    fun forward(h: Array<FloatArray>, adjacency: List<List<Int>>): Array<FloatArray> {
        // агрегация соседей + линейное преобразование + ReLU для каждого узла
    }

    fun backward(gradOut: Array<FloatArray>, adjacency: List<List<Int>>, lr: Float): Array<FloatArray> {
        // прямой вклад узла + вклад от роли узла как соседа других — см. формулу в "Мат. основе"
    }
}
```

### Важная оговорка

Учебная реализация перебирает соседей узла явным циклом на каждом шаге — для графа в 14 узлов это несущественно, но настоящие библиотеки (PyTorch Geometric, DGL) используют разрежённые матричные операции, работающие на графах из миллионов узлов за счёт векторизации на GPU.
