### Математические основы GNN

**Агрегация** (например, среднее):

$$h_v^{(k)} = \text{UPDATE}\left( h_v^{(k-1)}, \text{AGGREGATE}\left( \{ h_u^{(k-1)} : u \in \mathcal{N}(v) \} \right) \right)$$

Где:
- $h_v^{(k)}$ – представление узла $v$ на слое $k$.
- $\mathcal{N}(v)$ – соседи узла $v$.
- AGGREGATE – операция суммирования, усреднения или максимума.
- UPDATE – линейное преобразование + нелинейность.

**GCN** (Graph Convolutional Network):

$$H^{(k+1)} = \sigma\left( \tilde{D}^{-1/2} \tilde{A} \tilde{D}^{-1/2} H^{(k)} W^{(k)} \right)$$