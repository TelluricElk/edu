## Программная реализация

### Реализация с нуля на Python

Алгоритм Ллойда — две вложенные петли и ничего больше. Ниже полный код
эталонного прогона вместе с k-means++, силуэтом и учебной оценкой
совпадения с настоящими группами. Кроме `math`, ничего не требуется.

```python
import math

 # ---------- поток алертов, совпадающий с приложением ----------
MASK = (1 << 48) - 1

class Lcg:
    """Тот же LCG, что в java.util.Random, но без скремблирования сида."""
    def __init__(self, seed):
        self.s = seed & MASK
    def next_double(self):
        self.s = (self.s * 0x5DEECE66D + 0xB) & MASK
        return (self.s >> 24) / float(1 << 24)
    def next_int(self, bound):
        v = int(self.next_double() * bound)
        return bound - 1 if v >= bound else v

HOSTS_MAX = 60.0
MINUTES_MAX = 1440.0

 # имя, хосты от, хосты до, минуты от, минуты до, сколько алертов
GROUPS = [
    ("Массовое обновление ПО", 34.0, 56.0,  10.0,   90.0, 90),
    ("Сканирование сети",      18.0, 52.0, 520.0,  980.0, 25),
    ("Подбор пароля",           1.0,  6.0, 300.0, 1320.0, 35),
    ("Боковое смещение",        3.0, 13.0, 110.0,  380.0, 20),
]

def generate(seed=42):
    rnd = Lcg(seed)
    pts, truth = [], []
    for gi, (_, h0, h1, m0, m1, n) in enumerate(GROUPS):
        for _ in range(n):
            pts.append((h0 + rnd.next_double() * (h1 - h0),
                        m0 + rnd.next_double() * (m1 - m0)))
            truth.append(gi)
    return pts, truth

def space(pts, normalize):
    if not normalize:
        return [list(p) for p in pts]
    return [[p[0] / HOSTS_MAX, p[1] / MINUTES_MAX] for p in pts]

def d2(a, b):
    dx = a[0] - b[0]; dy = a[1] - b[1]
    return dx * dx + dy * dy

 # ---------- инициализация ----------
def init_random(xs, k, rnd):
    pool = list(range(len(xs)))
    return [list(xs[pool.pop(rnd.next_int(len(pool)))]) for _ in range(k)]

def init_pp(xs, k, rnd):
    """k-means++: каждый следующий центр — с вероятностью, пропорциональной
    квадрату расстояния до ближайшего уже выбранного."""
    cs = [list(xs[rnd.next_int(len(xs))])]
    while len(cs) < k:
        dd = [min(d2(x, c) for c in cs) for x in xs]
        total = sum(dd)
        if total <= 0:
            cs.append(list(xs[rnd.next_int(len(xs))])); continue
        t = rnd.next_double() * total
        acc = 0.0
        picked = len(xs) - 1
        for i, v in enumerate(dd):
            acc += v
            if acc >= t:
                picked = i; break
        cs.append(list(xs[picked]))
    return cs

 # ---------- алгоритм Ллойда ----------
def run(xs, k, iters, seed, pp):
    rnd = Lcg(seed)
    cs = init_pp(xs, k, rnd) if pp else init_random(xs, k, rnd)
    assign = [0] * len(xs)
    moved = 0
    for _ in range(iters):
        moved = 0
        for i, x in enumerate(xs):                  # фаза 1: приписать
            b, bd = 0, d2(x, cs[0])
            for j in range(1, k):
                dj = d2(x, cs[j])
                if dj < bd:
                    b, bd = j, dj
            if assign[i] != b:
                moved += 1
            assign[i] = b
        for j in range(k):                          # фаза 2: пересчитать
            sx = sy = 0.0; c = 0
            for i, x in enumerate(xs):
                if assign[i] == j:
                    sx += x[0]; sy += x[1]; c += 1
            if c:
                cs[j] = [sx / c, sy / c]
    inertia = sum(d2(xs[i], cs[assign[i]]) for i in range(len(xs)))
    return cs, assign, inertia, moved

 # ---------- оценки ----------
def silhouette(xs, assign, k):
    if k < 2:
        return 0.0
    groups = [[i for i in range(len(xs)) if assign[i] == j] for j in range(k)]
    total = 0.0
    cnt = 0
    for i in range(len(xs)):
        own = groups[assign[i]]
        if len(own) < 2:
            continue
        a = sum(math.sqrt(d2(xs[i], xs[j])) for j in own if j != i) / (len(own) - 1)
        b = None
        for j in range(k):
            if j == assign[i] or not groups[j]:
                continue
            v = sum(math.sqrt(d2(xs[i], xs[m])) for m in groups[j]) / len(groups[j])
            if b is None or v < b:
                b = v
        if b is None:
            continue
        total += (b - a) / max(a, b); cnt += 1
    return total / cnt if cnt else 0.0

def purity(assign, truth, k):
    """Учебная оценка: доля алертов, попавших в кластер, где преобладает их
    настоящая группа. В настоящей задаче настоящих групп нет."""
    ok = 0
    for j in range(k):
        mem = [truth[i] for i in range(len(truth)) if assign[i] == j]
        if mem:
            ok += max(mem.count(g) for g in set(mem))
    return ok / len(truth)

 # ---------- эталонный прогон ----------
K, ITERS, SEED = 4, 20, 7

pts, truth = generate()
xs = space(pts, True)
cs, assign, inertia, moved = run(xs, K, ITERS, SEED, True)

print("алертов %d, кластеров %d, инициализация k-means++, сид %d" % (len(pts), K, SEED))
print("  инерция  %.4f" % inertia)
print("  силуэт   %.4f" % silhouette(xs, assign, K))
print("  совпало  %.2f%%" % (purity(assign, truth, K) * 100))
print()
print("состав кластеров")
for j in range(K):
    mem = [truth[i] for i in range(len(truth)) if assign[i] == j]
    parts = ", ".join("%s %d" % (GROUPS[g][0], mem.count(g)) for g in sorted(set(mem)))
    print("  %2d алертов: %s" % (len(mem), parts))
print()
print("сходимость: переназначено алертов на шаге")
for it in range(1, 9):
    _, _, _, mv = run(xs, K, it, SEED, True)
    print("  шаг %d: %3d" % (it, mv))
print()
print("нормализация")
for norm in (True, False):
    z = space(pts, norm)
    _, a, inert, _ = run(z, K, ITERS, SEED, True)
    print("  %-4s совпало %.2f%%, силуэт %.4f, инерция %.4f"
          % ("вкл" if norm else "выкл", purity(a, truth, K) * 100,
             silhouette(z, a, K), inert))
print()
print("выбор числа кластеров (нормализация включена)")
for k in range(2, 9):
    _, a, inert, _ = run(xs, k, ITERS, SEED, True)
    print("  k=%d: инерция %7.4f, силуэт %.4f" % (k, inert, silhouette(xs, a, k)))
print()
print("зависимость от инициализации, 12 сидов")
for pp in (False, True):
    vals = [run(xs, K, ITERS, s, pp)[2] for s in range(1, 13)]
    uniq = len(set("%.4f" % v for v in vals))
    good = sum(1 for v in vals if v <= min(vals) * 1.05)
    print("  %-10s инерция от %.4f до %.4f, разных исходов %d, хороших сидов %d из 12"
          % ("k-means++" if pp else "случайная", min(vals), max(vals), uniq, good))
```

Вывод:

```
алертов 170, кластеров 4, инициализация k-means++, сид 7
  инерция  2.5146
  силуэт   0.6766
  совпало  92.94%

состав кластеров
  23 алертов: Сканирование сети 23
  90 алертов: Массовое обновление ПО 90
  27 алертов: Сканирование сети 2, Подбор пароля 25
  30 алертов: Подбор пароля 10, Боковое смещение 20

сходимость: переназначено алертов на шаге
  шаг 1: 127
  шаг 2:  16
  шаг 3:  17
  шаг 4:  26
  шаг 5:  11
  шаг 6:   1
  шаг 7:   2
  шаг 8:   0

нормализация
  вкл  совпало 92.94%, силуэт 0.6766, инерция 2.5146
  выкл совпало 79.41%, силуэт 0.7220, инерция 904604.9279

выбор числа кластеров (нормализация включена)
  k=2: инерция  8.7069, силуэт 0.6349
  k=3: инерция  6.6770, силуэт 0.5504
  k=4: инерция  2.5146, силуэт 0.6766
  k=5: инерция  1.8169, силуэт 0.5803
  k=6: инерция  1.6766, силуэт 0.5475
  k=7: инерция  1.2082, силуэт 0.5328
  k=8: инерция  1.1160, силуэт 0.5241

зависимость от инициализации, 12 сидов
  случайная  инерция от 2.5153 до 5.8937, разных исходов 6, хороших сидов 2 из 12
  k-means++  инерция от 2.5146 до 4.1946, разных исходов 3, хороших сидов 8 из 12
```

Четыре места, на которые стоит посмотреть.

**Цикл `for _ in range(iters)` содержит ровно две фазы и ничего между
ними.** Сначала каждая точка отходит к ближайшему центру, потом каждый
центр переезжает в среднее своих. Обе фазы не увеличивают инерцию, поэтому
процедура сходится; ни одна из них не умеет её увеличить, поэтому из
плохого минимума она не выбирается.

**Переменная `moved` — точный критерий остановки.** Если за целый шаг ни
одна точка не сменила кластер, то и центры не сдвинутся, и следующий шаг
повторится буквально. В выводе видно, как счётчик идёт 127, 16, 17, 26,
11, 1, 2, 0 — с всплеском на четвёртом шаге, потому что убывает инерция, а
не число переназначений.

**В `init_pp` вероятность пропорциональна `d2`, то есть КВАДРАТУ
расстояния.** Это не произвол: именно для квадрата доказана оценка качества
инициализации. С первой степенью расстояния схема тоже работала бы, но
гарантий бы не было.

**Функция `silhouette` считает `d2` — то же самое расстояние, в котором
строилось разбиение.** Отсюда и главный результат темы: в блоке
«нормализация» видно, что без неё совпадение падает с 92,94 до 79,41
процента, а силуэт при этом растёт с 0,6766 до 0,7220. Метрика меряет
искажённое расстояние и искажения не замечает.

### Готовое решение

```python
from sklearn.cluster import KMeans
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import silhouette_score
from sklearn.pipeline import make_pipeline

pipe = make_pipeline(
    StandardScaler(),          # НЕ опциональный шаг
    KMeans(
        n_clusters=4,
        init="k-means++",      # значение по умолчанию
        n_init=10,             # десять запусков, берётся лучший по инерции
        random_state=0
    )
)
labels = pipe.fit_predict(X)
print("инерция:", pipe[-1].inertia_)
print("силуэт:", silhouette_score(pipe[:-1].transform(X), labels))
```

Два места здесь принципиальны.

`StandardScaler` внутри конвейера, а не отдельным вызовом до него. Так
масштабирование становится частью модели и применяется к новым данным теми
же параметрами — иначе на проде легко посчитать среднее и дисперсию заново
и получить другое пространство.

`n_init=10` — те самые несколько запусков с разными начальными центрами и
выбором лучшего по инерции. Параметр стоит помнить: именно он превращает
алгоритм из лотереи в инструмент.

Для выбора числа кластеров обычно перебирают диапазон:

```python
for k in range(2, 12):
    km = KMeans(n_clusters=k, n_init=10, random_state=0).fit(Xs)
    print(k, km.inertia_, silhouette_score(Xs, km.labels_))
```

Когда данных миллионы, берут `MiniBatchKMeans`: он обновляет центры по
случайным порциям и работает на потоке. Когда кластеры заведомо не
шарообразные или нужны выбросы — берут `DBSCAN`, который умеет оставлять
точки вне кластеров вовсе.

### А что реально считает интерактив в этом приложении

Kotlin-версия в `KmLab.kt` повторяет листинг строка в строку. Одно место
устроено иначе — переключение пространства признаков.

```kotlin
private fun space(normalize: Boolean): Array<DoubleArray> =
    Array(alerts.size) { i ->
        val a = alerts[i]
        if (normalize) doubleArrayOf(a.hosts / HOSTS_MAX, a.minutes / MINUTES_MAX)
        else doubleArrayOf(a.hosts, a.minutes)
    }
```

Кластеризация идёт в этом пространстве, а центры перед отрисовкой
переводятся обратно в исходные единицы:

```kotlin
ch[j] = if (normalize) cs[j][0] * HOSTS_MAX else cs[j][0]
cm[j] = if (normalize) cs[j][1] * MINUTES_MAX else cs[j][1]
```

Благодаря этому график остаётся в хостах и минутах при любом положении
переключателя, и видно главное: без нормализации границы между кластерами
становятся почти горизонтальными. Число хостов на разбиение практически не
влияет, потому что в квадрат расстояния разница диапазонов входит
возведённой в квадрат — примерно в 576 раз.

### Важная оговорка

Данные этой темы синтетические, и четыре группы в них заложены явно. Это
сделано затем, чтобы можно было честно померить, насколько разбиение
совпало с реальностью, — в настоящей задаче такой возможности нет по
определению.

На реальном потоке алертов будут три существенных отличия.

Групп окажется не четыре, а несколько десятков, и границы между ними будут
куда менее чёткими. Значительная часть потока вообще не образует групп —
это единичные разнородные срабатывания, и k средних всё равно припишет
каждое к какому-нибудь центру, потому что понятия «вне кластера» у него нет.

Признаков будет больше двух, и здесь появляется отдельная проблема: в
пространстве высокой размерности расстояния между всеми парами точек
становятся похожими, и понятие «ближайшего центра» теряет смысл. Поэтому
перед кластеризацией размерность обычно снижают.

И наконец, поток меняется. Вышло обновление — появилась новая плотная
группа; сменился профиль работы компании — распались старые. Кластеризацию
в мониторинге перезапускают регулярно, а её результат сравнивают с
предыдущим: само по себе появление новой группы часто и есть тот сигнал,
ради которого всё затевалось.
