## Программная реализация

### Реализация с нуля на Python

Полный рабочий код: генерация корпуса доменов с управляемой корреляцией
признаков, обучение гауссовского наивного Байеса, классификация и метрики
вместе со средней уверенностью модели.

```python
import math

# параметры классов: (среднее энтропии, разброс, среднее доли цифр, разброс)
LEGIT = (0.48, 0.15, 0.10, 0.09)
DGA   = (0.70, 0.14, 0.26, 0.14)


class Lcg:
    """LCG с параметрами java.util.Random — тот же генератор стоит в Kotlin,
    поэтому корпус доменов побитово совпадает и числа из этого листинга
    равны тем, что показывает приложение."""

    def __init__(self, seed):
        self.s = seed & 0xFFFFFFFFFFFF

    def nf(self):
        self.s = (self.s * 0x5DEECE66D + 0xB) & 0xFFFFFFFFFFFF
        return (self.s >> 24) / float(1 << 24)

    def gauss(self, mu, sd):
        return mu + sd * (sum(self.nf() for _ in range(6)) - 3.0) / math.sqrt(0.5)


def clamp01(v):
    return 0.0 if v < 0 else (1.0 if v > 1 else v)


def generate(seed, n, dga_rate, corr):
    """corr — сила связи признаков ВНУТРИ класса. Реализована через общий
    скрытый фактор: при corr = 0 признаки независимы и наивное допущение
    выполняется точно, при corr = 0.9 они почти дублируют друг друга."""
    rnd = Lcg(seed)
    data = []
    k = math.sqrt(corr)
    rest = math.sqrt(1.0 - corr)
    for _ in range(n):
        is_dga = 1.0 if rnd.nf() < dga_rate else 0.0
        m1, s1, m2, s2 = DGA if is_dga else LEGIT
        shared = rnd.gauss(0.0, 1.0)          # общий фактор для обоих признаков
        z1 = k * shared + rest * rnd.gauss(0.0, 1.0)
        z2 = k * shared + rest * rnd.gauss(0.0, 1.0)
        data.append(([clamp01(m1 + s1 * z1), clamp01(m2 + s2 * z2)], is_dga))
    return data


def fit(data, var_smoothing):
    """Всё обучение — один проход и восемь чисел на выходе.
    Ни итераций, ни градиентов, ни скорости обучения."""
    stats = {}
    for cls in (0.0, 1.0):
        pts = [x for x, y in data if y == cls]
        if len(pts) < 2:
            stats[cls] = None
            continue
        means, variances = [], []
        for j in (0, 1):
            vals = [p[j] for p in pts]
            m = sum(vals) / len(vals)
            v = sum((t - m) ** 2 for t in vals) / len(vals)
            means.append(m)
            variances.append(max(v + var_smoothing, 1e-6))
        stats[cls] = (means, variances, len(pts) / len(data))
    return stats


def log_gauss(x, mean, var):
    return -0.5 * math.log(2 * math.pi * var) - (x - mean) ** 2 / (2 * var)


def proba_dga(x, stats, prior_dga):
    """Априорная вероятность приходит ИЗВНЕ, а не из выборки: в обучающем
    корпусе доля DGA выровнена искусственно, а в реальном потоке она другая."""
    if stats[0.0] is None or stats[1.0] is None:
        return 0.5
    logp = {}
    for cls, prior in ((0.0, 1.0 - prior_dga), (1.0, prior_dga)):
        means, variances, _ = stats[cls]
        # вот он, наивный шаг: логарифмы правдоподобий просто СКЛАДЫВАЮТСЯ,
        # то есть сами правдоподобия перемножаются как независимые улики
        logp[cls] = math.log(max(prior, 1e-12)) + sum(
            log_gauss(x[j], means[j], variances[j]) for j in (0, 1)
        )
    mx = max(logp.values())
    a = math.exp(logp[1.0] - mx)
    b = math.exp(logp[0.0] - mx)
    return a / (a + b)


def evaluate(test, stats, prior, threshold):
    tp = fp = tn = fn = 0
    for x, y in test:
        pred = 1.0 if proba_dga(x, stats, prior) >= threshold else 0.0
        if pred == 1 and y == 1:
            tp += 1
        elif pred == 1 and y == 0:
            fp += 1
        elif pred == 0 and y == 0:
            tn += 1
        else:
            fn += 1
    prec = tp / (tp + fp) if tp + fp else 0.0
    rec = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0.0
    acc = (tp + tn) / max(1, len(test))
    return tp, fp, tn, fn, prec, rec, f1, acc


def mean_confidence(test, stats, prior):
    """Средняя уверенность модели в собственном ответе. Смотреть на неё
    нужно ВМЕСТЕ с F1 — расхождение и есть мера доверия к числам."""
    total = 0.0
    for x, _ in test:
        p = proba_dga(x, stats, prior)
        total += max(p, 1.0 - p)
    return total / len(test)


PRIOR = 0.35
train = generate(42, 300, PRIOR, corr=0.0)
test = generate(777, 200, PRIOR, corr=0.0)
stats = fit(train, var_smoothing=0.0)

r = evaluate(test, stats, PRIOR, 0.5)
print(f"TP={r[0]} FP={r[1]} TN={r[2]} FN={r[3]}")
print(f"precision={r[4]:.4f} recall={r[5]:.4f} F1={r[6]:.4f} accuracy={r[7]:.4f}")
print(f"средняя уверенность={mean_confidence(test, stats, PRIOR):.4f}")

m0, v0, _ = stats[0.0]
m1, v1, _ = stats[1.0]
print(f"легитимные: энтропия mu={m0[0]:.4f} var={v0[0]:.5f} | "
      f"цифры mu={m0[1]:.4f} var={v0[1]:.5f}")
print(f"DGA:        энтропия mu={m1[0]:.4f} var={v1[0]:.5f} | "
      f"цифры mu={m1[1]:.4f} var={v1[1]:.5f}")

print()
print("--- что делает нарушение наивного допущения ---")
for corr in [0.0, 0.3, 0.6, 0.9]:
    tr = generate(42, 300, PRIOR, corr)
    te = generate(777, 200, PRIOR, corr)
    st = fit(tr, 0.0)
    rr = evaluate(te, st, PRIOR, 0.5)
    print(f"  corr={corr:.1f}:  F1={rr[6]:.4f}  accuracy={rr[7]:.4f}  "
          f"уверенность={mean_confidence(te, st, PRIOR):.4f}")

print()
print("--- два конкретных домена ---")
for name, x in [("xkqvmz3r7pd2", [0.82, 0.35]), ("buhgalteria-plus", [0.40, 0.05])]:
    print(f"  {name:20s} P(DGA) = {proba_dga(x, stats, PRIOR):.4f}")
```

Вывод:

```
TP=62 FP=6 TN=115 FN=17
precision=0.9118 recall=0.7848 F1=0.8435 accuracy=0.8850
средняя уверенность=0.8733
легитимные: энтропия mu=0.4677 var=0.02451 | цифры mu=0.0972 var=0.00691
DGA:        энтропия mu=0.6817 var=0.02126 | цифры mu=0.2527 var=0.01755

--- что делает нарушение наивного допущения ---
  corr=0.0:  F1=0.8435  accuracy=0.8850  уверенность=0.8733
  corr=0.3:  F1=0.7919  accuracy=0.8450  уверенность=0.8847
  corr=0.6:  F1=0.7619  accuracy=0.8250  уверенность=0.8875
  corr=0.9:  F1=0.7273  accuracy=0.8050  уверенность=0.8753

--- два конкретных домена ---
  xkqvmz3r7pd2         P(DGA) = 0.9956
  buhgalteria-plus     P(DGA) = 0.0220
```

Вот главное число этой темы, и оно в средней колонке. При росте корреляции F1
падает с 0,844 до 0,727, точность — с 0,885 до 0,805. А **уверенность модели
не падает вовсе**: 0,873 в начале и 0,875 в конце, причём в середине она даже
слегка растёт. Модель ошибается на четверть чаще и сообщает об этом ровно
ноль раз.

Восемь чисел модели, напечатанных выше, стоит рассмотреть отдельно. Средние
энтропии — 0,468 против 0,682 при разбросах около 0,157: классы перекрываются
заметно. По доле цифр — 0,097 против 0,253, перекрытие меньше. Признак доли
цифр сильнее, но ни один из них не решает задачу в одиночку.

### Готовое решение

```python
import numpy as np
from sklearn.naive_bayes import GaussianNB
from sklearn.calibration import CalibratedClassifierCV
from sklearn.metrics import classification_report, brier_score_loss

X = np.array([x for x, _ in train])
y = np.array([v for _, v in train])
X_test = np.array([x for x, _ in test])
y_test = np.array([v for _, v in test])

model = GaussianNB(
    priors=[1 - 0.35, 0.35],   # априорные вероятности задаём явно
    var_smoothing=1e-9,        # аналог нашего var_smoothing
)
model.fit(X, y)
print(classification_report(y_test, model.predict(X_test), digits=3))

# модель, выученная одним проходом: те самые восемь чисел
print("средние:   ", model.theta_)
print("дисперсии: ", model.var_)
```

Параметр `priors` здесь важнее, чем кажется. Без него `GaussianNB` берёт
априорные вероятности из обучающей выборки — а она у нас сбалансирована
искусственно, и модель унаследует неверную базовую частоту.

Отдельного внимания заслуживает калибровка. Если ваша политика безопасности
сформулирована в вероятностях, сырой наивный Байес использовать нельзя:

```python
calibrated = CalibratedClassifierCV(GaussianNB(), method="isotonic", cv=5)
calibrated.fit(X, y)

raw = GaussianNB().fit(X, y).predict_proba(X_test)[:, 1]
cal = calibrated.predict_proba(X_test)[:, 1]
# мера Бриера у откалиброванной модели заметно ниже при той же точности
print("Brier сырой:        ", brier_score_loss(y_test, raw))
print("Brier откалиброван: ", brier_score_loss(y_test, cal))
```

Мера Бриера — средний квадрат отклонения предсказанной вероятности от
фактического исхода. В отличие от F1, она наказывает именно за ложную
уверенность, поэтому для наивного Байеса это правильная метрика контроля.

### А что реально считает интерактив в этом приложении

В приложении тот же алгоритм на Kotlin, в `NbLab.kt`. Обучение целиком:

```kotlin
private fun statFor(data: List<DomainSample>, dga: Boolean, varSmoothing: Double): ClassStat? {
    val pts = data.filter { (it.label >= 0.5) == dga }
    if (pts.size < 2) return null
    val means = DoubleArray(N_FEAT)
    val variances = DoubleArray(N_FEAT)
    for (j in 0 until N_FEAT) {
        var m = 0.0
        for (p in pts) m += p.x[j]
        m /= pts.size
        var v = 0.0
        for (p in pts) v += (p.x[j] - m) * (p.x[j] - m)
        v /= pts.size
        means[j] = m
        variances[j] = maxOf(v + varSmoothing, 1e-6)
    }
    return ClassStat(means, variances, pts.size.toDouble() / data.size)
}

fun fit(data: List<DomainSample>, varSmoothing: Double): NbModel =
    NbModel(statFor(data, false, varSmoothing), statFor(data, true, varSmoothing))
```

А вот тот самый наивный шаг — в предсказании складываются логарифмы, то есть
правдоподобия перемножаются:

```kotlin
var logDga = ln(maxOf(priorDga, 1e-12))
var logLegit = ln(maxOf(1.0 - priorDga, 1e-12))
for (j in 0 until N_FEAT) {
    logDga += logGauss(x[j], dga.means[j], dga.variances[j])
    logLegit += logGauss(x[j], legit.means[j], legit.variances[j])
}
```

Граница решения на первом графике строится перебором по сетке: для каждой
ячейки считается вероятность, и рисуется линия смены знака. Второй график
показывает те самые четыре нормальные кривые — по две на признак.

Касание графика создаёт «свой» домен с координатами точки касания. Это
единственное место в теме, где используется `detectTapGestures`, а не
`clickable`: нужны координаты касания, а `clickable` их не даёт.

### Важная оговорка

**Признаков два, а не двадцать.** Настоящий детектор DGA использует длину
имени, частоты n-грамм, словарные признаки, возраст домена, репутацию зоны,
характер ответов DNS. Два признака взяты, чтобы всё помещалось на плоскость
и границу решения можно было увидеть глазами. Ирония в том, что на двух
признаках наивный Байес работает хуже всего: его сила проявляется как раз
там, где признаков сотни.

**Корпус синтетический.** Домены не собраны из реального трафика, а
сгенерированы из заданных распределений. Качественно поведение то же, но
абсолютные цифры у каждой организации будут свои.

**Базовая частота нереалистична.** Треть DGA в выборке — учебное упрощение;
в настоящем DNS-потоке это доли процента. Именно поэтому априорная вероятность
вынесена в отдельный слайдер: посмотрите, что происходит при 0,02.

**Корреляция смоделирована общим скрытым фактором.** В реальности связь между
энтропией и долей цифр возникает естественно — цифры расширяют алфавит и
поднимают энтропию. Мы задаём эту связь искусственно, чтобы ею можно было
управлять слайдером и наблюдать эффект в чистом виде.

**Нормальность — допущение, и оно хромает.** Доля цифр у большинства
легитимных доменов равна ровно нулю, то есть распределение имеет тяжёлый
пик на границе диапазона и на нормальное похоже слабо. Мы это допущение
принимаем сознательно — как и весь метод, который называется наивным.
