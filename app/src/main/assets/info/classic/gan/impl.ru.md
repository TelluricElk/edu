## Программная реализация GAN

### Реализация на Python

```python
import numpy as np


def train_gan(G, D, real_sampler, noise_dim, steps, lr_d, lr_g, batch_size, seed):
    rng = np.random.default_rng(seed)

    for step in range(steps):
        # --- Шаг 1: обучаем дискриминатор ---
        real_batch = real_sampler(rng, batch_size)
        noise_batch = rng.uniform(-1, 1, size=(batch_size, noise_dim))

        for x_real in real_batch:
            p_real = D.forward(x_real)
            D.backward_step(grad=p_real - 1.0)          # хотим D(x_real) -> 1

        for z in noise_batch:
            x_fake = G.forward(z)
            p_fake = D.forward(x_fake)
            D.backward_step(grad=p_fake - 0.0)           # хотим D(x_fake) -> 0

        D.apply_gradients(lr_d, n=batch_size * 2)

        # --- Шаг 2: обучаем генератор (дискриминатор в этот момент не меняется) ---
        noise_batch2 = rng.uniform(-1, 1, size=(batch_size, noise_dim))
        for z in noise_batch2:
            x_fake = G.forward(z)
            p_fake = D.forward(x_fake)
            grad_wrt_fake = D.backward_step(grad=p_fake - 1.0, update=False)  # хотим D(x_fake) -> 1
            G.backward_step(grad=grad_wrt_fake)

        G.apply_gradients(lr_g, n=batch_size)
```

Обратите внимание на ключевую асимметрию: при обучении генератора градиент считается **через** дискриминатор (чтобы понять, куда двигать выход генератора), но веса самого дискриминатора при этом не обновляются.

### Готовое решение: PyTorch

```python
import torch
import torch.nn as nn

G = nn.Sequential(nn.Linear(2, 10), nn.ReLU(), nn.Linear(10, 2))
D = nn.Sequential(nn.Linear(2, 10), nn.ReLU(), nn.Linear(10, 1), nn.Sigmoid())

opt_g = torch.optim.Adam(G.parameters(), lr=1e-3)
opt_d = torch.optim.Adam(D.parameters(), lr=1e-3)
loss_fn = nn.BCELoss()
```

### А что реально считает интерактив в этом приложении

Тот же принцип на Kotlin, с переиспользованием класса `Dense` (см. `NeuralPrimitives.kt`, тот же слой, что и в теме «Автокодировщик»):

```kotlin
class Generator(noiseDim: Int, hidden: Int, seed: Int) {
    val l1 = Dense(noiseDim, hidden, Activation.RELU, seed + 1)
    val l2 = Dense(hidden, 2, Activation.LINEAR, seed + 2)
    fun forward(z: FloatArray): FloatArray = l2.forward(l1.forward(z))
}

class Discriminator(hidden: Int, seed: Int) {
    val l1 = Dense(2, hidden, Activation.RELU, seed + 1)
    val l2 = Dense(hidden, 1, Activation.SIGMOID, seed + 2)
    fun forward(x: FloatArray): Float = l2.forward(l1.forward(x))[0]
}
```

Обучение честно чередует шаг дискриминатора и шаг генератора на каждой итерации — никакой заранее просчитанной анимации, только реальный, пусть и колеблющийся, процесс.

### Важная оговорка

Учебная реализация использует полный батч-градиент на каждом шаге вместо мини-батчей и оптимизатор Adam, что делает обучение более чувствительным к выбору learning rate, чем в промышленных реализациях. Именно поэтому в интерактиве колебания заметны сильнее, чем в современных производственных GAN-архитектурах, где для стабилизации используют дополнительные приёмы (спектральная нормализация, WGAN-GP и другие).
