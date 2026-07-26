## Программная реализация диффузионной модели

### Реализация на Python

```python
import numpy as np

MEANS = np.array([[-3.0, 0.0], [3.0, 0.0]])   # центры двух "облаков" данных
BASE_VAR = 0.5
WEIGHTS = np.array([0.5, 0.5])


def forward_noise(x0, sigma):
    """Прямой процесс — точная формула, без обучения."""
    return x0 + np.random.normal(0, sigma, size=x0.shape)


def score_exact(x, sigma):
    """Точная score-функция для смеси гауссиан — вычисляется по формуле,
    а не аппроксимируется нейросетью (см. раздел 'Мат. основа')."""
    var = BASE_VAR + sigma ** 2
    diffs = x[:, None, :] - MEANS[None, :, :]
    sq_dists = np.sum(diffs ** 2, axis=2)
    log_probs = -sq_dists / (2 * var) + np.log(WEIGHTS)[None, :]
    log_probs -= log_probs.max(axis=1, keepdims=True)
    probs = np.exp(log_probs)
    probs /= probs.sum(axis=1, keepdims=True)              # апостериорная вероятность компоненты

    score = np.zeros_like(x)
    for k in range(len(MEANS)):
        score += probs[:, k:k + 1] * (MEANS[k] - x) / var
    return score


def reverse_sample(n_points, n_steps, sigma_max=6.0, sigma_min=0.05, seed=1):
    """Обратный процесс (детерминированный, probability flow) — точный score на каждом шаге."""
    rng = np.random.default_rng(seed)
    x = rng.normal(0, sigma_max, size=(n_points, 2))         # старт из чистого шума
    sigmas = sigma_max * (sigma_min / sigma_max) ** (np.arange(n_steps + 1) / n_steps)

    for i in range(n_steps):
        sigma_t, sigma_next = sigmas[i], sigmas[i + 1]
        s = score_exact(x, sigma_t)
        x = x + (sigma_t ** 2 - sigma_next ** 2) * s          # шаг убирает ровно "свою" порцию шума
    return x
```

### Готовое решение: diffusers (Hugging Face)

```python
from diffusers import DDPMPipeline

pipeline = DDPMPipeline.from_pretrained("google/ddpm-cifar10-32")
image = pipeline().images[0]   # обученная модель, реальная score-сеть внутри
```

### А что реально считает интерактив в этом приложении

Тот же алгоритм на Kotlin — та же точная формула score для смеси гауссиан, тот же детерминированный обратный шаг:

```kotlin
fun scoreExact(x: FloatArray, sigma: Float): FloatArray {
    // апостериорные вероятности компонент смеси + взвешенная сумма (μ_k - x) / var
    // — точная формула, см. impl.ru.md выше и math.ru.md
}

fun reverseSample(nPoints: Int, nSteps: Int): List<Point2D> {
    // старт из чистого шума, затем nSteps детерминированных шагов по точному score
}
```

### Важная оговорка

Это единственная тема приложения, где раздел «Код» показывает не то, что стояло бы в реальном производственном проекте (там были бы обученные веса `U-Net`), а то, что **можно посчитать без обучения** для простого известного распределения. Показатель качества (число шагов денойзинга) здесь абсолютно реален и работает так же, как в настоящих диффузионных моделях — просто направление на каждом шаге в реальной модели даёт обученная сеть, а здесь — точная формула.
