### Математические основы трансформера

**Self-attention** для матрицы входов $X$:

$$Q = X W_Q, \quad K = X W_K, \quad V = X W_V$$

$$Attention(Q, K, V) = \text{softmax}\left( \frac{Q K^T}{\sqrt{d_k}} \right) V$$

**Multi-head attention**:

$$\text{MultiHead}(Q, K, V) = \text{Concat}(\text{head}_1, \dots, \text{head}_h) W_O$$

где $\text{head}_i = Attention(QW_Q^i, KW_K^i, VW_V^i)$.

**Позиционное кодирование**:

$$PE_{(pos, 2i)} = \sin(pos / 10000^{2i/d_{model}}), \quad PE_{(pos, 2i+1)} = \cos(pos / 10000^{2i/d_{model}})$$

**Feed-forward слой** (два линейных преобразования с ReLU):

$$FFN(x) = \max(0, xW_1 + b_1) W_2 + b_2$$