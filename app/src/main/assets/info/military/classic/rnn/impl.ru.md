## Программная реализация RNN

### Реализация на Python

```python
import numpy as np


class RNNCell:
    def __init__(self, hidden_size, seed):
        rng = np.random.default_rng(seed)
        self.h = hidden_size
        self.Wxh = rng.uniform(-0.5, 0.5, size=hidden_size)               # вход (скаляр) -> hidden
        self.Whh = rng.uniform(-0.5, 0.5, size=(hidden_size, hidden_size))
        self.bh = np.zeros(hidden_size)
        self.Why = rng.uniform(-0.5, 0.5, size=hidden_size)               # hidden -> выход
        self.by = 0.0

    def forward(self, sequence):
        h = np.zeros(self.h)
        self.h_history = [h.copy()]          # запоминаем ВСЕ промежуточные состояния — нужны для BPTT
        self.x_history = []
        for x in sequence:
            z = self.Wxh * x + self.Whh @ h + self.bh
            h = np.tanh(z)
            self.h_history.append(h.copy())
            self.x_history.append(x)
        y_lin = self.Why @ h + self.by
        self.p = 1 / (1 + np.exp(-y_lin))
        return self.p

    def backward(self, label, lr):
        T = len(self.x_history)
        grad_p = self.p - label
        grad_Why = grad_p * self.h_history[T]
        grad_h_next = grad_p * self.Why          # градиент, "втекающий" в последний шаг времени

        grad_Wxh = np.zeros(self.h)
        grad_Whh = np.zeros((self.h, self.h))
        grad_bh = np.zeros(self.h)

        for t in reversed(range(T)):              # идём НАЗАД по времени — это и есть BPTT
            h_t = self.h_history[t + 1]
            h_prev = self.h_history[t]
            dtanh = (1 - h_t ** 2) * grad_h_next   # производная tanh
            grad_Wxh += dtanh * self.x_history[t]
            grad_Whh += np.outer(dtanh, h_prev)
            grad_bh += dtanh
            grad_h_next = self.Whh.T @ dtanh       # передаём градиент на шаг раньше

        self.Wxh -= lr * grad_Wxh
        self.Whh -= lr * grad_Whh
        self.bh -= lr * grad_bh
        self.Why -= lr * grad_Why
        self.by -= lr * grad_p
```

### Готовое решение: PyTorch

```python
import torch.nn as nn

rnn = nn.RNN(input_size=1, hidden_size=4, batch_first=True)
head = nn.Sequential(nn.Linear(4, 1), nn.Sigmoid())

# в реальных проектах почти всегда используют nn.LSTM или nn.GRU вместо nn.RNN —
# именно из-за проблемы затухающего градиента, разобранной в разделе "Мат. основа"
```

### А что реально считает интерактив в этом приложении

Тот же принцип на Kotlin — та же простая (Elman) RNN-ячейка, тот же настоящий BPTT:

```kotlin
class Network(seed: Int = 3) {
    val wxh: FloatArray = /* HIDDEN весов, вход -> скрытое состояние */
    val whh: Array<FloatArray> = /* HIDDEN x HIDDEN, скрытое -> скрытое */
    val why: FloatArray = /* HIDDEN весов, скрытое -> выход */

    fun forward(sequence: List<Float>): Pair<List<FloatArray>, Float> {
        // проходит по всей последовательности, возвращает историю скрытых
        // состояний (нужна для BPTT) и итоговую вероятность
    }

    fun trainStep(sequence: List<Float>, label: Float, lr: Float) {
        // обратное распространение во времени: идём от последнего шага к первому,
        // на каждом шаге домножая градиент на производную tanh
    }
}
```

### Важная оговорка

Учебная реализация — простая (Elman) RNN без LSTM/GRU-вентилей, поэтому проблема затухающего градиента видна в интерактиве без прикрас — на реальных длинных последовательностях (текст, речь) её решают именно вентильные архитектуры или трансформеры (следующая тема).
