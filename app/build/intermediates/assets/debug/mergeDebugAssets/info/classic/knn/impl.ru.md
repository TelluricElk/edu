## Программная реализация

### Реализация с нуля на Python

Полный рабочий код: генерация базы разобранных алертов, три метрики, два
правила голосования, нормализация признаков и шум разметки.

```python
import math

EVENTS_MAX = 500.0
CLASS_NAMES = ["ложное срабатывание", "подозрительно", "подтверждённый инцидент"]
# (среднее событий, разброс, среднее доли нерабочего времени, разброс)
CLASSES = [
    (70.0,  45.0, 0.22, 0.16),
    (210.0, 70.0, 0.50, 0.18),
    (330.0, 80.0, 0.74, 0.16),
]


class Lcg:
    """LCG с параметрами java.util.Random — тот же генератор стоит в Kotlin,
    поэтому база алертов побитово совпадает с приложением."""

    def __init__(self, seed):
        self.s = seed & 0xFFFFFFFFFFFF

    def nf(self):
        self.s = (self.s * 0x5DEECE66D + 0xB) & 0xFFFFFFFFFFFF
        return (self.s >> 24) / float(1 << 24)

    def gauss(self, mu, sd):
        return mu + sd * (sum(self.nf() for _ in range(6)) - 3.0) / math.sqrt(0.5)


def clamp(v, lo, hi):
    return lo if v < lo else (hi if v > hi else v)


def generate(seed, n, noise):
    """noise — доля алертов с испорченным вердиктом: аналитик ошибся при
    разборе. В реальной базе SOC такие записи есть всегда."""
    rnd = Lcg(seed)
    data = []
    for _ in range(n):
        c = min(int(rnd.nf() * 3), 2)
        me, se, mo, so = CLASSES[c]
        events = clamp(rnd.gauss(me, se), 0.0, EVENTS_MAX)
        off_hours = clamp(rnd.gauss(mo, so), 0.0, 1.0)
        label = c
        if rnd.nf() < noise:
            label = min(int(rnd.nf() * 3), 2)
        data.append(((events, off_hours), label))
    return data


def scales(train, normalize):
    """Масштабы признаков. Без нормализации оба равны единице — и тогда
    расстояние определяется почти исключительно числом событий."""
    if not normalize:
        return (1.0, 1.0)

    def sd(vals):
        m = sum(vals) / len(vals)
        return max(math.sqrt(sum((v - m) ** 2 for v in vals) / len(vals)), 1e-9)

    return (sd([p[0][0] for p in train]), sd([p[0][1] for p in train]))


def distance(a, b, sc, metric):
    d0 = (a[0] - b[0]) / sc[0]
    d1 = (a[1] - b[1]) / sc[1]
    if metric == "euclid":
        return math.sqrt(d0 * d0 + d1 * d1)
    if metric == "manhattan":
        return abs(d0) + abs(d1)
    return max(abs(d0), abs(d1))          # chebyshev


def neighbors(x, train, k, metric, sc):
    return sorted(((distance(x, p[0], sc, metric), p[1]) for p in train))[:k]


def classify(x, train, k, metric, weighting, sc):
    votes = [0.0, 0.0, 0.0]
    for d, label in neighbors(x, train, k, metric, sc):
        # взвешивание по расстоянию усиливает голос ближнего соседа —
        # включая ближнего соседа с ОШИБОЧНЫМ вердиктом
        votes[label] += 1.0 if weighting == "uniform" else 1.0 / (d + 1e-6)
    return votes.index(max(votes))


def accuracy(test, train, k, metric, weighting, normalize):
    sc = scales(train, normalize)
    ok = sum(1 for x, y in test if classify(x, train, k, metric, weighting, sc) == y)
    return ok / len(test)


train = generate(42, 240, noise=0.0)
test = generate(777, 150, noise=0.0)

print("--- цена масштаба признаков ---")
for nz in (False, True):
    a = accuracy(test, train, 7, "euclid", "uniform", nz)
    print(f"  нормализация {'ВКЛ ' if nz else 'ВЫКЛ'}: точность = {a:.4f}")

sc = scales(train, True)
print(f"  разброс: события {sc[0]:.1f}, доля нерабочего времени {sc[1]:.4f}")
print(f"  отношение разбросов = {sc[0] / sc[1]:.0f}, "
      f"отношение вкладов в квадрат расстояния = {(sc[0] / sc[1]) ** 2:.0f}")

print()
print("--- k при чистой и при шумной разметке ---")
noisy = generate(42, 240, noise=0.2)
print("    k   чистая  шум 20%")
for k in [1, 3, 5, 9, 15, 25, 41]:
    print(f"  {k:3d}   {accuracy(test, train, k, 'euclid', 'uniform', True):.4f}  "
          f"{accuracy(test, noisy, k, 'euclid', 'uniform', True):.4f}")

print()
print("--- взвешивание по расстоянию ---")
for noise, base in [(0.0, train), (0.2, noisy)]:
    u = accuracy(test, base, 7, "euclid", "uniform", True)
    w = accuracy(test, base, 7, "euclid", "distance", True)
    print(f"  шум {noise:4.2f}: равномерное {u:.4f}, по расстоянию {w:.4f}")

print()
print("--- метрика (k=7, нормализация вкл) ---")
for m in ["euclid", "manhattan", "chebyshev"]:
    print(f"  {m:10s} {accuracy(test, train, 7, m, 'uniform', True):.4f}")

print()
print("--- разбор одного алерта ---")
alert = (180.0, 0.62)
sc = scales(train, True)
print(f"  алерт: {alert[0]:.0f} событий, доля нерабочего времени {alert[1]:.2f}")
for d, label in neighbors(alert, train, 7, "euclid", sc):
    print(f"    сосед на расстоянии {d:.3f} -> {CLASS_NAMES[label]}")
print(f"  вердикт: {CLASS_NAMES[classify(alert, train, 7, 'euclid', 'uniform', sc)]}")
```

Вывод:

```
--- цена масштаба признаков ---
  нормализация ВЫКЛ: точность = 0.8000
  нормализация ВКЛ : точность = 0.8600
  разброс: события 128.0, доля нерабочего времени 0.2494
  отношение разбросов = 513, отношение вкладов в квадрат расстояния = 263340

--- k при чистой и при шумной разметке ---
    k   чистая  шум 20%
    1   0.8533  0.6533
    3   0.8533  0.7133
    5   0.8333  0.8000
    9   0.8533  0.8067
   15   0.8667  0.8467
   25   0.8800  0.8600
   41   0.8800  0.8600

--- взвешивание по расстоянию ---
  шум 0.00: равномерное 0.8600, по расстоянию 0.8733
  шум 0.20: равномерное 0.8400, по расстоянию 0.8000

--- метрика (k=7, нормализация вкл) ---
  euclid     0.8600
  manhattan  0.8533
  chebyshev  0.8533

--- разбор одного алерта ---
  алерт: 180 событий, доля нерабочего времени 0.62
    сосед на расстоянии 0.035 -> подозрительно
    сосед на расстоянии 0.121 -> подозрительно
    сосед на расстоянии 0.170 -> подозрительно
    сосед на расстоянии 0.185 -> подозрительно
    сосед на расстоянии 0.193 -> подозрительно
    сосед на расстоянии 0.243 -> подозрительно
    сосед на расстоянии 0.266 -> подозрительно
  вердикт: подозрительно
```

Последний блок — то самое объяснение через прецеденты. Аналитику показывается
не оценка вероятности, а семь конкретных исторических алертов с их вердиктами;
здесь все семь сошлись на одном ответе, и решение бесспорно. Когда соседи
расходятся во мнениях, это тоже видно сразу — и это честный сигнал, что случай
пограничный.

Три вывода, ради которых написана тема.

**Масштаб.** Отношение вкладов в квадрат расстояния — более четверти миллиона.
Доля нерабочего времени не «менее важна», она не участвует вовсе. При этом
точность упала всего с 0,86 до 0,80 — то есть на глаз всё выглядит рабочим.
Именно незаметность и делает эту ошибку опасной.

**k против шума.** На чистой базе k почти не важен: от 0,833 до 0,880 на всём
диапазоне. На базе с 20% ошибочных вердиктов разница между k = 1 и k = 25 —
это 0,653 против 0,860, то есть больше двадцати процентных пунктов. Настройка,
которая на чистых данных кажется несущественной, на реальных решает всё.

**Взвешивание по расстоянию не всегда помогает.** На чистых данных оно даёт
плюс: 0,873 против 0,860. На шумных — минус: 0,800 против 0,840. Причина в том,
что оно усиливает голос ближайшего соседа, а если тот размечен неверно,
усиливается именно ошибка.

### Готовое решение

```python
import numpy as np
from sklearn.neighbors import KNeighborsClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import make_pipeline
from sklearn.model_selection import GridSearchCV

X = np.array([x for x, _ in train])
y = np.array([lab for _, lab in train])

model = make_pipeline(
    StandardScaler(),                       # не опция, а обязательный шаг
    KNeighborsClassifier(n_neighbors=7, metric="minkowski", p=2, weights="uniform"),
)
model.fit(X, y)

grid = GridSearchCV(
    model,
    {
        "kneighborsclassifier__n_neighbors": [1, 3, 5, 9, 15, 25, 41],
        "kneighborsclassifier__weights": ["uniform", "distance"],
        "kneighborsclassifier__p": [1, 2],   # манхэттен и евклид
    },
    cv=5,
)
grid.fit(X, y)
print("лучшие параметры:", grid.best_params_)
```

Два замечания.

`StandardScaler` в пайплайне — не улучшение, а необходимость, и его
обязательно оборачивают именно в пайплайн: иначе при перекрёстной проверке
масштаб посчитается по всей выборке, включая проверочные части, и оценка
качества окажется завышенной. Это называется утечкой данных.

Параметр `p` у `KNeighborsClassifier` — показатель расстояния Минковского:
`p=1` даёт манхэттенское расстояние, `p=2` — евклидово. Для расстояния
Чебышёва отдельное значение `metric="chebyshev"`.

Для больших баз имеет смысл посмотреть на `algorithm="kd_tree"` или
`"ball_tree"`, а при миллионах записей — на специализированные библиотеки
приближённого поиска соседей.

### А что реально считает интерактив в этом приложении

В приложении тот же алгоритм на Kotlin, в `KnnLab.kt`. Поиск соседей и
голосование:

```kotlin
fun neighbors(x: DoubleArray, base: List<AlertCase>, k: Int,
              metric: KnnMetric, sc: DoubleArray): List<Neighbor> =
    base.map { Neighbor(distance(x, it.x, sc, metric), it.label) }
        .sortedBy { it.distance }
        .take(k)

fun classify(x: DoubleArray, base: List<AlertCase>, k: Int, metric: KnnMetric,
             weighting: KnnWeighting, sc: DoubleArray): Int {
    val votes = DoubleArray(CLASS_COUNT)
    for (nb in neighbors(x, base, k, metric, sc)) {
        votes[nb.label] += if (weighting == KnnWeighting.UNIFORM) 1.0
                           else 1.0 / (nb.distance + 1e-6)
    }
    var best = 0
    for (c in 1 until CLASS_COUNT) if (votes[c] > votes[best]) best = c
    return best
}
```

Карта решений строится перебором по сетке: для каждой ячейки вызывается
`classify`, и ячейка красится в цвет предсказанного класса. Именно поэтому при
выключенной нормализации карта распадается на вертикальные полосы — цвет
перестаёт зависеть от вертикальной координаты.

Касание карты задаёт новый алерт: подсвечиваются найденные соседи и
показываются их вердикты. Это единственное место темы, где нужен
`detectTapGestures`, а не `clickable`: требуются координаты касания.

### Важная оговорка

**Два признака вместо десятков.** Настоящий триаж алертов учитывает тип
правила, роль учётной записи, критичность актива, историю пользователя,
географию, репутацию адресов. Два признака взяты, чтобы карта решений
помещалась на экран. Ирония в том, что именно на двух признаках метод работает
лучше всего: при двухстах признаках проклятие размерности делает его
бесполезным без снижения размерности.

**Полный перебор.** Мы сравниваем запрос со всей базой из 240 записей. При
реальной базе в сотни тысяч инцидентов так делать нельзя — нужны индексные
структуры, о которых сказано в разделе «Мат. основа».

**База синтетическая.** Алерты сгенерированы из заданных распределений.
Качественно поведение то же, но в реальной базе классы разделены хуже, а
границы между «подозрительно» и «подтверждённым инцидентом» размыты сильнее.

**Шум разметки смоделирован равномерным.** Мы портим вердикт случайным
образом. В жизни ошибки разбора не случайны: чаще всего настоящий инцидент
закрывают как ложное срабатывание, а не наоборот, — и это смещение опаснее
равномерного шума, потому что оно систематически сдвигает границу.

**Нормализация считается по обучающей базе.** Так и надо: масштабы — часть
модели, и брать их с учётом проверочной выборки нельзя. В `sklearn` за это
отвечает пайплайн, о чём сказано выше.
