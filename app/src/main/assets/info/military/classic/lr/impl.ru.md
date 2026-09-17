## Программная реализация

### Реализация с нуля на Python

Полный рабочий код: генерация истории атак, нормализация, градиентный спуск,
метрики и пересчёт коэффициентов обратно в гигабиты на тысячу узлов.

```python
import math

TRUE_K, TRUE_B = 0.42, 3.5      # истинная зависимость, из которой генерируем
NODES_MAX = 120.0               # максимум узлов ботнета, тысяч


class Lcg:
    """LCG с параметрами java.util.Random — тот же генератор стоит в Kotlin,
    поэтому история атак получается побитово одинаковой и числа из этого
    листинга совпадают с тем, что показывает приложение."""

    def __init__(self, seed):
        self.s = seed & 0xFFFFFFFFFFFF

    def nf(self):
        self.s = (self.s * 0x5DEECE66D + 0xB) & 0xFFFFFFFFFFFF
        return (self.s >> 24) / float(1 << 24)

    def gauss(self, mu, sd):
        return mu + sd * (sum(self.nf() for _ in range(6)) - 3.0) / math.sqrt(0.5)


def generate(seed, n, amp_rate):
    """amp_rate — доля атак с амплификацией. Такие атаки возможны только
    при небольшом ботнете: усилителем работают чужие DNS/NTP-серверы,
    поэтому много своих узлов не требуется. На графике они лежат слева
    и высоко — и именно поэтому перекашивают прямую."""
    rnd = Lcg(seed)
    data = []
    for _ in range(n):
        nodes = 4.0 + rnd.nf() * (NODES_MAX - 4.0)
        bw = TRUE_K * nodes + TRUE_B + rnd.gauss(0.0, 3.2)
        if nodes < 35.0 and rnd.nf() < amp_rate:
            bw += 25.0 + rnd.nf() * 45.0        # коэффициент усиления
        data.append((nodes, max(0.1, bw)))
    return data


def fit(data, lr, epochs, normalize, lam):
    xs = [x for x, _ in data]
    mu = sum(xs) / len(xs)
    sd = math.sqrt(sum((x - mu) ** 2 for x in xs) / len(xs))
    if not normalize:
        mu, sd = 0.0, 1.0           # обучаемся прямо на «сырых» тысячах узлов

    k, b = 0.0, 0.0
    n = len(data)
    history = []
    for _ in range(epochs):
        gk = gb = 0.0
        loss = 0.0
        for x, y in data:
            xn = (x - mu) / sd
            err = k * xn + b - y
            gk += err * xn
            gb += err
            loss += err * err
        k -= lr * (gk / n + lam * k)   # свободный член НЕ регуляризуется
        b -= lr * (gb / n)
        history.append(loss / n)
        if math.isnan(k) or abs(k) > 1e9:
            return k, b, mu, sd, True, history
    return k, b, mu, sd, False, history


def metrics(data, k, b, mu, sd):
    n = len(data)
    ybar = sum(y for _, y in data) / n
    sse = sae = sst = 0.0
    for x, y in data:
        pred = k * ((x - mu) / sd) + b
        sse += (pred - y) ** 2
        sae += abs(pred - y)
        sst += (y - ybar) ** 2
    return sse / n, sae / n, (1 - sse / sst if sst > 0 else 0.0)


def to_real_units(k, b, mu, sd):
    """Коэффициенты, обученные на нормализованном признаке, обратно
    в «гигабиты на тысячу узлов» и «гигабиты фона»."""
    return k / sd, b - k * mu / sd


data = generate(seed=42, n=160, amp_rate=0.0)
k, b, mu, sd, diverged, hist = fit(data, lr=0.08, epochs=120,
                                   normalize=True, lam=0.0)
mse, mae, r2 = metrics(data, k, b, mu, sd)
k_real, b_real = to_real_units(k, b, mu, sd)

print(f"наклон       = {k_real:.4f} Гбит/с на тысячу узлов (истина {TRUE_K})")
print(f"свободный член = {b_real:.4f} Гбит/с (истина {TRUE_B})")
print(f"MSE = {mse:.3f}   MAE = {mae:.3f}   R2 = {r2:.4f}")
pred50 = k * ((50.0 - mu) / sd) + b
print(f"прогноз для ботнета в 50 тыс. узлов: {pred50:.2f} Гбит/с")

print()
print("--- что делает выключенная нормализация ---")
for lr_try in [0.0001, 0.0003, 0.0008, 0.002]:
    _, _, _, _, div, _ = fit(data, lr_try, 300, normalize=False, lam=0.0)
    print(f"  lr={lr_try:<8} {'РАСХОДИТСЯ' if div else 'сходится'}")
for lr_try in [0.1, 0.5, 2.0]:
    _, _, _, _, div, _ = fit(data, lr_try, 300, normalize=True, lam=0.0)
    print(f"  lr={lr_try:<8} {'РАСХОДИТСЯ' if div else 'сходится'} (с нормализацией)")

print()
print("--- что делают выбросы-амплификации ---")
for rate in [0.0, 0.2, 0.5, 1.0]:
    d2 = generate(42, 160, rate)
    k2, b2, mu2, sd2, _, _ = fit(d2, 0.08, 300, True, 0.0)
    m2, a2, r22 = metrics(d2, k2, b2, mu2, sd2)
    kr, br = to_real_units(k2, b2, mu2, sd2)
    print(f"  доля {rate:4.2f}: наклон={kr:6.3f} фон={br:6.2f} "
          f"MSE={m2:7.2f} MAE={a2:6.2f} R2={r22:6.3f}")
```

Вывод:

```
наклон       = 0.4200 Гбит/с на тысячу узлов (истина 0.42)
свободный член = 3.1635 Гбит/с (истина 3.5)
MSE = 9.140   MAE = 2.458   R2 = 0.9592
прогноз для ботнета в 50 тыс. узлов: 24.16 Гбит/с

--- что делает выключенная нормализация ---
  lr=0.0001   сходится
  lr=0.0003   сходится
  lr=0.0008   РАСХОДИТСЯ
  lr=0.002    РАСХОДИТСЯ
  lr=0.1      сходится (с нормализацией)
  lr=0.5      сходится (с нормализацией)
  lr=2.0      сходится (с нормализацией)

--- что делают выбросы-амплификации ---
  доля 0.00: наклон= 0.420 фон=  3.16 MSE=   9.14 MAE=  2.46 R2= 0.959
  доля 0.20: наклон= 0.316 фон= 12.55 MSE= 144.81 MAE=  6.69 R2= 0.448
  доля 0.50: наклон= 0.230 фон= 20.19 MSE= 205.28 MAE=  9.65 R2= 0.220
  доля 1.00: наклон=-0.084 фон= 47.05 MSE= 242.00 MAE= 13.08 R2= 0.031
```

Три вывода, которые стоят всей этой темы.

Модель на чистых данных восстанавливает истину почти точно: 0,4200 против
0,42 и 3,16 против 3,5. Расхождение в свободном члене — это шум выборки, а не
ошибка метода.

Предел скорости обучения без нормализации лежит между 0,0003 и 0,0008. С
нормализацией спокойно работает 2,0 — запас примерно в три тысячи раз. Это
одно и то же обучение одной и той же модели, разница только в масштабе
признака.

Выбросы ломают модель постепенно и незаметно. При доле 0,20 наклон уже занижен
на четверть, но график всё ещё выглядит правдоподобно. При доле 1,00 наклон
становится **отрицательным** — модель утверждает, что чем больше ботнет, тем
слабее атака.

### Готовое решение

```python
import numpy as np
from sklearn.linear_model import LinearRegression, HuberRegressor, Ridge
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import make_pipeline
from sklearn.metrics import mean_squared_error, mean_absolute_error, r2_score

X = np.array([[x] for x, _ in data])
y = np.array([v for _, v in data])

model = make_pipeline(StandardScaler(), LinearRegression())
model.fit(X, y)

pred = model.predict(X)
print("MSE:", mean_squared_error(y, pred))
print("MAE:", mean_absolute_error(y, pred))
print("R2 :", r2_score(y, pred))

# коэффициент в исходных единицах достаётся из пайплайна
scaler, linreg = model.named_steps["standardscaler"], model.named_steps["linearregression"]
print("наклон:", linreg.coef_[0] / scaler.scale_[0])
```

Два замечания по этому листингу.

`LinearRegression` в sklearn решает нормальное уравнение напрямую, без
градиентного спуска, поэтому **нормализация ему не нужна** для сходимости — он
не итеративный. `StandardScaler` в пайплайне стоит по другой причине: чтобы
коэффициенты были сопоставимы между собой при нескольких признаках и чтобы
работала регуляризация. А вот `SGDRegressor`, который действительно
итеративный, без нормализации ведёт себя ровно так же скверно, как наш код.

Против выбросов в sklearn есть готовое средство — и это не `Ridge`:

```python
robust = make_pipeline(StandardScaler(), HuberRegressor(epsilon=1.35))
robust.fit(X_with_outliers, y_with_outliers)
# наклон останется около истинного даже при заметной доле амплификаций
```

### А что реально считает интерактив в этом приложении

В приложении тот же алгоритм на Kotlin, в `LrLab.kt`. Центральная часть —
эпоха градиентного спуска:

```kotlin
fun fit(lr: Double, epochs: Int, normalize: Boolean, lambda: Double,
        ampRate: Double): FitResult {
    val data = datasetFor(ampRate)
    val mu = if (normalize) data.map { it.nodes }.average() else 0.0
    val sd = if (normalize) stdDev(data, mu) else 1.0

    var k = 0.0
    var b = 0.0
    val n = data.size
    for (e in 0 until epochs) {
        var gk = 0.0
        var gb = 0.0
        for (p in data) {
            val xn = (p.nodes - mu) / sd
            val err = k * xn + b - p.bandwidth
            gk += err * xn
            gb += err
        }
        k -= lr * (gk / n + lambda * k)
        b -= lr * (gb / n)
        if (k.isNaN() || abs(k) > 1e9) return FitResult(k, b, mu, sd, true, e)
    }
    return FitResult(k, b, mu, sd, false, epochs)
}
```

Слайдер скорости обучения в интерактиве **логарифмический**: он проходит
диапазон от 0,00001 до 2,0. Линейный слайдер на таком диапазоне был бы
бесполезен — вся интересная область без нормализации уместилась бы в первые
доли процента его хода.

Доля амплификаций пересобирает выборку, поэтому при её изменении меняются и
сами точки на графике, а не только прямая.

### Важная оговорка

**Один признак.** Реальный прогноз мощности атаки учитывал бы тип атаки,
географию ботнета, пропускную способность заражённых устройств и историю
конкретной кампании. Один признак взят, чтобы всё помещалось на один график:
как только признаков становится два, наглядная прямая превращается в
плоскость, а при трёх рисовать уже нечего.

**Линейность — допущение, а не факт.** При очень крупных ботнетах зависимость
на практике загибается: упирается в пропускную способность магистралей у
самих атакующих. Наша история сгенерирована строго линейной, чтобы тема
оставалась про нормализацию и выбросы, а не про выбор формы кривой.

**Выбросы сгенерированы, а не собраны.** Амплификация в реальности сложнее:
коэффициент усиления зависит от протокола и составляет от нескольких единиц
для DNS до тысяч для memcached. Мы моделируем добавку от 25 до 70 Гбит/с —
порядок величины правдоподобный, конкретика условная.

**Метрики считаются на обучающей выборке.** Отдельной контрольной здесь нет,
в отличие от темы логистической регрессии. Для модели с двумя параметрами и
160 наблюдениями переобучение практически невозможно, поэтому разделение
выборки только запутало бы картину. Как только параметров становится много,
так делать нельзя.
