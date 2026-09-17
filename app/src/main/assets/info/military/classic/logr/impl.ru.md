## Программная реализация

### Реализация с нуля на Python

Ниже — полный, работающий код: генерация того же корпуса писем, обучение
логистической регрессии полным батч-градиентом, взвешивание классов,
L2-регуляризация и подбор порога по стоимости ошибок. Никаких библиотек, кроме
`math` — чтобы было видно каждое действие.

```python
import math

N_FEAT = 5
FEATURES = ["spf", "age", "links", "urgency", "attach"]


class Lcg:
    """Линейный конгруэнтный генератор с параметрами java.util.Random.
    Нужен, чтобы Python и Kotlin породили ПОБИТОВО одинаковый корпус писем:
    иначе числа в этом разделе и в интерактиве разойдутся."""

    def __init__(self, seed):
        self.s = seed & 0xFFFFFFFFFFFF

    def next_float(self):
        self.s = (self.s * 0x5DEECE66D + 0xB) & 0xFFFFFFFFFFFF
        return (self.s >> 24) / float(1 << 24)

    def bern(self, p):
        return 1.0 if self.next_float() < p else 0.0

    def gauss(self, mu, sd):
        # сумма шести равномерных даёт приближение нормального (Ирвин-Холл):
        # дисперсия суммы равна 0.5, поэтому делим на sqrt(0.5)
        s = sum(self.next_float() for _ in range(6)) - 3.0
        return mu + sd * s / math.sqrt(0.5)


def clamp01(v):
    return 0.0 if v < 0 else (1.0 if v > 1 else v)


def generate(seed, n, phish_rate=0.30):
    """Корпус писем почтового шлюза. Классы намеренно пересекаются:
    ни один признак не разделяет их сам по себе."""
    rnd = Lcg(seed)
    data = []
    for _ in range(n):
        is_phish = 1.0 if rnd.next_float() < phish_rate else 0.0
        if is_phish:
            x = [rnd.bern(0.62),                   # SPF/DKIM провален
                 clamp01(rnd.gauss(0.28, 0.20)),   # домен молодой
                 clamp01(rnd.gauss(0.58, 0.22)),   # много ссылок
                 clamp01(rnd.gauss(0.63, 0.22)),   # давление срочностью
                 rnd.bern(0.34)]                   # рискованное вложение
        else:
            x = [rnd.bern(0.16),
                 clamp01(rnd.gauss(0.68, 0.22)),   # домен старый
                 clamp01(rnd.gauss(0.34, 0.20)),
                 clamp01(rnd.gauss(0.34, 0.21)),
                 rnd.bern(0.08)]
        data.append((x, is_phish))
    return data


def sigmoid(z):
    if z < -30.0:
        return 1e-13
    if z > 30.0:
        return 1.0 - 1e-13
    return 1.0 / (1.0 + math.exp(-z))


def fit(data, lr, epochs, lam, w_pos):
    w = [0.0] * N_FEAT
    b = 0.0
    # Зажим эффективного затухания весов. Множитель (1 - lr*lam) обязан
    # оставаться положительным, иначе веса меняют знак каждый шаг и модель
    # разлетается. Это не урок о регуляризации, а численная ловушка.
    decay = min(lr * lam, 0.5)

    for _ in range(epochs):
        gw = [0.0] * N_FEAT
        gb = 0.0
        wsum = 0.0
        for x, y in data:
            z = sum(w[j] * x[j] for j in range(N_FEAT)) + b
            p = sigmoid(z)
            cw = w_pos if y == 1.0 else 1.0     # вес класса
            wsum += cw
            err = cw * (p - y)                  # тот самый (p - y)
            for j in range(N_FEAT):
                gw[j] += err * x[j]
            gb += err
        for j in range(N_FEAT):
            w[j] -= lr * (gw[j] / wsum) + decay * w[j]
        b -= lr * (gb / wsum)                   # свободный член НЕ затухает
    return w, b


def proba(x, w, b):
    return sigmoid(sum(w[j] * x[j] for j in range(N_FEAT)) + b)


def confusion(data, w, b, thr):
    tp = fp = tn = fn = 0
    for x, y in data:
        pred = 1.0 if proba(x, w, b) >= thr else 0.0
        if pred == 1 and y == 1:
            tp += 1
        elif pred == 1 and y == 0:
            fp += 1
        elif pred == 0 and y == 0:
            tn += 1
        else:
            fn += 1
    return tp, fp, tn, fn


def cost(cm, c_fn):
    tp, fp, tn, fn = cm
    return fp * 1.0 + fn * c_fn


def best_threshold(data, w, b, c_fn):
    """Перебор порога по контрольной выборке: та самая величина,
    которую в интерактиве показывают рядом с теоретической 1/(1+C)."""
    best_t, best_c = 0.5, float("inf")
    t = 0.05
    while t <= 0.951:
        c = cost(confusion(data, w, b, t), c_fn)
        if c < best_c:
            best_c, best_t = c, t
        t += 0.01
    return best_t, best_c


train = generate(42, 300)
test = generate(777, 200)

w, b = fit(train, lr=1.0, epochs=120, lam=0.01, w_pos=1.0)

print("веса:")
for name, wj in zip(FEATURES, w):
    print(f"  {name:8s} {wj:+.3f}")
print(f"  bias     {b:+.3f}")

cm = confusion(test, w, b, 0.5)
tp, fp, tn, fn = cm
prec = tp / (tp + fp)
rec = tp / (tp + fn)
print(f"TP={tp} FP={fp} TN={tn} FN={fn}")
print(f"precision={prec:.3f} recall={rec:.3f}")
print(f"стоимость при C=10 и пороге 0.5: {cost(cm, 10.0):.0f}")
print("лучший порог при C=10: %.2f (стоимость %.0f)"
      % best_threshold(test, w, b, 10.0))
```

Вывод этого кода:

```
веса:
  spf      +1.289
  age      -2.778
  links    +1.371
  urgency  +1.892
  attach   +0.784
  bias     -1.487
TP=51 FP=4 TN=132 FN=13
precision=0.927 recall=0.797
стоимость при C=10 и пороге 0.5: 134
лучший порог при C=10: 0.32 (стоимость 55)
```

Обратите внимание на три вещи. Во-первых, вес признака `age` отрицателен, как
и предсказывалось в «Эталонной задаче»: старый домен — аргумент в пользу
легитимности. Во-вторых, самый сильный по модулю вес именно у него — возраст
домена оказался информативнее провала SPF. В-третьих, простой сдвиг порога с
0,5 до 0,32 уменьшает ущерб организации в два с половиной раза, не меняя в
модели вообще ничего.

### Готовое решение

На практике это пишется в четыре строки. Обратите внимание, что `C` в
`LogisticRegression` — величина, **обратная** силе регуляризации, а
`class_weight` соответствует нашему `w_pos`.

```python
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, roc_auc_score

X_train = np.array([x for x, _ in train])
y_train = np.array([y for _, y in train])
X_test = np.array([x for x, _ in test])
y_test = np.array([y for _, y in test])

model = LogisticRegression(
    C=1.0,                    # обратная сила L2-регуляризации
    class_weight=None,        # {0: 1, 1: 10} — аналог w_pos = 10
    max_iter=1000,
    solver="lbfgs",
)
model.fit(X_train, y_train)

proba_test = model.predict_proba(X_test)[:, 1]
print(classification_report(y_test, proba_test >= 0.5, digits=3))
print("ROC AUC:", roc_auc_score(y_test, proba_test))

# Подбор порога под стоимость ошибок — этого sklearn за вас не сделает
thresholds = np.linspace(0.05, 0.95, 91)
costs = [((proba_test >= t) & (y_test == 0)).sum()
         + 10 * ((proba_test < t) & (y_test == 1)).sum()
         for t in thresholds]
print("лучший порог:", thresholds[int(np.argmin(costs))])
```

Здесь есть важная деталь, из-за которой в продакшене ошибаются постоянно:
`model.predict(X)` жёстко зашит на порог 0,5. Если решение принимается по
стоимости ошибок — пользоваться нужно `predict_proba`, а сравнение с порогом
писать руками. Метод `predict` в задачах безопасности почти всегда неправильный
ответ.

Для реальной эксплуатации к этому добавляют калибровку
(`CalibratedClassifierCV`), потому что после `class_weight` вероятности
смещены, а политика безопасности формулируется именно в вероятностях.

### А что реально считает интерактив в этом приложении

В приложении тот же алгоритм написан на Kotlin, в `LogrLab.kt`. Вот
центральная часть — один шаг градиентного спуска и обучение целиком:

```kotlin
fun fit(lr: Double, epochs: Int, lambda: Double, wPos: Double): FitResult {
    val w = DoubleArray(N_FEAT)
    var b = 0.0
    // тот же зажим, что в Python: (1 - lr*lambda) не должен стать отрицательным
    val decay = minOf(lr * lambda, 0.5)

    for (e in 0 until epochs) {
        val gw = DoubleArray(N_FEAT)
        var gb = 0.0
        var wsum = 0.0
        for (m in trainSet) {
            var z = b
            for (j in 0 until N_FEAT) z += w[j] * m.x[j]
            val p = sigmoid(z)
            val cw = if (m.label >= 0.5) wPos else 1.0
            wsum += cw
            val err = cw * (p - m.label)
            for (j in 0 until N_FEAT) gw[j] += err * m.x[j]
            gb += err
        }
        for (j in 0 until N_FEAT) w[j] -= lr * (gw[j] / wsum) + decay * w[j]
        b -= lr * (gb / wsum)
    }
    return FitResult(w, b, diverged = false)
}
```

Генератор корпуса писем в Kotlin — тот же LCG с теми же константами, поэтому
письма получаются побитово те же, что в Python выше. Это сделано намеренно: вы
можете запустить питоновский код у себя, получить ровно те числа, что видите на
экране телефона, и поэкспериментировать дальше.

Визуализация в интерактиве строится из этих же величин: гистограмма
распределения предсказанных вероятностей по двум классам, вертикальная линия
порога и ROC-кривая с точкой, отмечающей текущий порог.

### Важная оговорка

Что здесь упрощено по сравнению с настоящим почтовым шлюзом.

**Признаки.** Пять численных признаков — это учебная модель. Настоящий
антифишинг работает с тысячами признаков, включая мешок слов по теме и телу,
репутацию IP-адреса отправителя, историю переписки с этим контрагентом и
результаты детонации вложения в песочнице. Пять признаков выбраны так, чтобы
их можно было показать на экране телефона и обсудить каждый.

**Корпус синтетический.** Письма сгенерированы из заданных распределений, а не
собраны из реального трафика. Это честно: на настоящем корпусе модель вела бы
себя качественно так же, но цифры зависели бы от конкретной организации.
Главное отличие — базовая частота: в нашей выборке фишинга треть, в реальном
потоке доли процента, и precision при этом обваливается (формула есть в разделе
«Мат. основа»).

**Батч-градиент вместо L-BFGS.** Настоящие библиотеки решают эту задачу
методами второго порядка за десятки итераций вместо сотен. Простой градиентный
спуск выбран потому, что в нём видно, что делает каждый слайдер — а именно это
и есть цель интерактива.

**Зажим затухания весов.** Произведение скорости обучения на лямбду ограничено
величиной 0,5. Без этого при крайних положениях двух слайдеров модель
численно разлетается, и пользователь видит не урок о регуляризации, а
бессмысленные числа. Ограничение не влияет на поведение в рабочей области
параметров.

**Стоимость ошибок — условная.** Мы считаем, что все ложные тревоги стоят
одинаково и все пропуски стоят одинаково. В жизни фишинг, уведший учётную
запись администратора домена, и фишинг, пойманный бдительным пользователем,
различаются на порядки.
