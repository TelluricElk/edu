## Программная реализация

### Реализация с нуля на Python

Ниже полный код эталонного прогона: генерация коллекции, построение дерева,
обучение леса, OOB-оценка и важности признаков. Никаких библиотек, кроме
`math`, не требуется. Генератор псевдослучайных чисел здесь тот же, что в
приложении, поэтому листинг выдаёт ровно те числа, которые показывает экран.

```python
import math

 # ---------- генератор, совпадающий с приложением ----------
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

N_FEAT = 6
NAMES = ["Энтропия .text", "Доля подозрительных импортов", "Число секций",
         "Размер оверлея", "Достоверность подписи", "Доля URL-строк"]
FMIN = [4.5, 0.0, 2.0, 0.0, 0.0, 0.0]
FMAX = [8.0, 1.0, 12.0, 400.0, 1.0, 1.0]

def true_rule(x):
    ent, susp, sec, ovl, sign, urls = x
    score = (3.4 * (ent - 6.25) / 1.75 + 1.0 * (susp - 0.5)
             + 0.55 * (urls - 0.5) + 0.45 * ((sec - 7.0) / 5.0)
             + 0.5 * (ovl / 400.0 - 0.5) - 0.9 * (sign - 0.5))
    if ent > 7.0 and susp > 0.6:
        score += 1.3
    if sign > 0.8 and susp < 0.3:
        score -= 1.1
    return score > 0.30

def generate(seed, n, noise):
    rnd = Lcg(seed)
    out = []
    for _ in range(n):
        x = [FMIN[f] + rnd.next_double() * (FMAX[f] - FMIN[f]) for f in range(N_FEAT)]
        y = true_rule(x)
        if rnd.next_double() < noise:
            y = not y
        out.append((x, y))
    return out

 # ---------- дерево ----------
def imp(pos, neg, crit):
    t = pos + neg
    if t == 0:
        return 0.0
    p, q = pos / t, neg / t
    if crit == 'gini':
        return 1 - p * p - q * q
    r = 0.0
    for v in (p, q):
        if v > 0:
            r -= v * math.log(v, 2)
    return r

def best_split(data, crit, feats):
    """Один проход по отсортированным значениям с накопительными счётчиками."""
    n = len(data)
    if n < 2:
        return None
    pos_all = sum(1 for _, y in data if y)
    parent = imp(pos_all, n - pos_all, crit)
    bg, bf, bt = 0.0, -1, 0.0
    for f in feats:
        order = sorted(data, key=lambda d: d[0][f])
        lp = ln = 0
        for i in range(n - 1):
            if order[i][1]:
                lp += 1
            else:
                ln += 1
            v, w = order[i][0][f], order[i + 1][0][f]
            if v == w:
                continue
            left = lp + ln
            right = n - left
            wi = (left / n) * imp(lp, ln, crit) + \
                 (right / n) * imp(pos_all - lp, (n - pos_all) - ln, crit)
            if parent - wi > bg:
                bg, bf, bt = parent - wi, f, (v + w) / 2.0
    return (bf, bt) if bf >= 0 else None

def pick_features(mtry, rnd):
    """Своё подпространство признаков ДЛЯ КАЖДОГО УЗЛА, а не для дерева."""
    if mtry >= N_FEAT:
        return list(range(N_FEAT))
    pool = list(range(N_FEAT))
    out = [pool.pop(rnd.next_int(len(pool))) for _ in range(mtry)]
    return sorted(out)

class Node:
    def __init__(self, f=-1, t=0.0, l=None, r=None, p=None, n=0):
        self.f, self.t, self.l, self.r, self.p, self.n = f, t, l, r, p, n
    @property
    def leaf(self):
        return self.p is not None

def majority(data):
    pos = sum(1 for _, y in data if y)
    return pos >= len(data) - pos

def build(data, crit, max_depth, min_split, mtry, rnd, depth=0):
    if not data:
        return Node(p=False)
    first = data[0][1]
    if all(y == first for _, y in data) or depth >= max_depth or len(data) < min_split:
        return Node(p=majority(data), n=len(data))
    s = best_split(data, crit, pick_features(mtry, rnd))
    if s is None:
        return Node(p=majority(data), n=len(data))
    f, t = s
    left = [d for d in data if d[0][f] <= t]
    right = [d for d in data if d[0][f] > t]
    if not left or not right:
        return Node(p=majority(data), n=len(data))
    return Node(f, t,
                build(left, crit, max_depth, min_split, mtry, rnd, depth + 1),
                build(right, crit, max_depth, min_split, mtry, rnd, depth + 1),
                None, len(data))

def predict(node, x):
    while not node.leaf:
        node = node.l if x[node.f] <= node.t else node.r
    return node.p

def accuracy(tree, data):
    return sum(1 for x, y in data if predict(tree, x) == y) / len(data)

 # ---------- лес ----------
def train_forest(train, crit, n_trees, max_depth, min_split, mtry, bootstrap):
    trees, oob = [], []
    n = len(train)
    for i in range(n_trees):
        rnd = Lcg(1000 + i * 7919)
        if bootstrap:
            idx = [rnd.next_int(n) for _ in range(n)]
            used = set(idx)
            trees.append(build([train[j] for j in idx], crit,
                               max_depth, min_split, mtry, rnd))
            oob.append([j for j in range(n) if j not in used])
        else:
            trees.append(build(train, crit, max_depth, min_split, mtry, rnd))
            oob.append([])
    return trees, oob

def forest_accuracy(trees, data):
    ok = 0
    for x, y in data:
        p = sum(1 for t in trees if predict(t, x))
        if (p >= len(trees) - p) == y:
            ok += 1
    return ok / len(data)

def oob_accuracy(trees, oob, train):
    pos = [0] * len(train)
    tot = [0] * len(train)
    for k, t in enumerate(trees):
        for j in oob[k]:
            tot[j] += 1
            if predict(t, train[j][0]):
                pos[j] += 1
    ok = cnt = 0
    for j in range(len(train)):
        if tot[j] == 0:
            continue
        cnt += 1
        if (pos[j] >= tot[j] - pos[j]) == train[j][1]:
            ok += 1
    return ok / cnt if cnt else 0.0

def importance(trees):
    v = [0.0] * N_FEAT
    def walk(nd):
        if nd.leaf:
            return
        v[nd.f] += nd.n
        walk(nd.l); walk(nd.r)
    for t in trees:
        walk(t)
    s = sum(v)
    return [a / s for a in v]

 # ---------- эталонный прогон ----------
TRAIN, TEST, NOISE = 400, 400, 0.10
TREES, DEPTH, MTRY, MIN_SPLIT = 25, 10, 2, 4

train = generate(42, TRAIN, NOISE)
test = generate(909, TEST, NOISE)

single = build(train, 'gini', DEPTH, MIN_SPLIT, N_FEAT, Lcg(5))
forest, oob = train_forest(train, 'gini', TREES, DEPTH, MIN_SPLIT, MTRY, True)

print("доля ВПО: обучающая %.4f, контрольная %.4f"
      % (sum(1 for _, y in train if y) / TRAIN, sum(1 for _, y in test if y) / TEST))
print()
print("одно дерево: обучающая %.4f, контрольная %.4f"
      % (accuracy(single, train), accuracy(single, test)))
print("лес из %d:   обучающая %.4f, контрольная %.4f, OOB %.4f"
      % (TREES, forest_accuracy(forest, train), forest_accuracy(forest, test),
         oob_accuracy(forest, oob, train)))
print()
print("глубина: дерево против леса (контрольная)")
for d in (2, 4, 6, 8, 10, 12, 14):
    one = build(train, 'gini', d, MIN_SPLIT, N_FEAT, Lcg(5))
    f, o = train_forest(train, 'gini', TREES, d, MIN_SPLIT, MTRY, True)
    print("  %2d: дерево %.4f  лес %.4f  разница %+.4f"
          % (d, accuracy(one, test), forest_accuracy(f, test),
             forest_accuracy(f, test) - accuracy(one, test)))
print()
print("два источника разнообразия (контрольная)")
for boot in (True, False):
    for m in (2, N_FEAT):
        f, o = train_forest(train, 'gini', TREES, DEPTH, MIN_SPLIT, m, boot)
        print("  бутстрап %-5s mtry=%d: %.4f"
              % ("есть" if boot else "нет", m, forest_accuracy(f, test)))
print()
print("важность признаков")
for i, v in enumerate(importance(forest)):
    print("  %-30s %.4f" % (NAMES[i], v))
```

Вывод:

```
доля ВПО: обучающая 0.4650, контрольная 0.4725

одно дерево: обучающая 0.9825, контрольная 0.7575
лес из 25:   обучающая 0.9750, контрольная 0.8400, OOB 0.8275

глубина: дерево против леса (контрольная)
   2: дерево 0.8300  лес 0.6975  разница -0.1325
   4: дерево 0.8250  лес 0.8400  разница +0.0150
   6: дерево 0.7725  лес 0.8425  разница +0.0700
   8: дерево 0.7675  лес 0.8450  разница +0.0775
  10: дерево 0.7575  лес 0.8400  разница +0.0825
  12: дерево 0.7400  лес 0.8225  разница +0.0825
  14: дерево 0.7400  лес 0.8325  разница +0.0925

два источника разнообразия (контрольная)
  бутстрап есть  mtry=2: 0.8400
  бутстрап есть  mtry=6: 0.8375
  бутстрап нет   mtry=2: 0.8275
  бутстрап нет   mtry=6: 0.7575

важность признаков
  Энтропия .text                 0.2087
  Доля подозрительных импортов   0.1435
  Число секций                   0.1538
  Размер оверлея                 0.1952
  Достоверность подписи          0.1627
  Доля URL-строк                 0.1360
```

Разберём три места, на которых держится вся тема.

**Функция `pick_features` вызывается внутри `build` при каждом разбиении.**
Это не мелочь реализации. Если выбрать подпространство признаков один раз на
дерево, дерево, которому не достался сильный признак, окажется испорчено
целиком. При выборе в каждом узле сильный признак просто не попадает в часть
узлов, и там дереву приходится искать разрез среди остальных — деревья
расходятся, а поодиночке остаются полноценными.

**В `train_forest` один и тот же объект `rnd` обслуживает и бутстрап-выборку,
и последующий выбор признаков.** Это делает дерево с номером `i` полностью
детерминированным при заданном сиде `1000 + i * 7919`. Без такой
воспроизводимости отладка ансамбля превращается в гадание: любое изменение
результата невозможно отличить от случайного разброса.

**В ветке `bootstrap=False` объект `rnd` создаётся, но при `mtry = N_FEAT`
из него не берут ни одного числа.** Отсюда и вырождение: все деревья строятся
по одним и тем же данным одним и тем же детерминированным алгоритмом и
совпадают до последнего узла. Последняя строка таблицы разнообразия —
`0.7575` — равна точности одиночного дерева не приблизительно, а точно.

**`best_split` идёт одним проходом по отсортированным значениям.** Наивная
версия для каждого кандидата в порог заново разделяла бы выборку и считала
неоднородность обеих половин — это O(n^2) на признак. Здесь счётчики классов
слева накапливаются по мере движения, счётчики справа получаются вычитанием,
и стоимость падает до сортировки. Для одного дерева разница терпима, для леса
из пятидесяти — это разница между отзывчивым ползунком и зависшим экраном.

### Готовое решение

На практике лес берут из библиотеки. Интерфейс `scikit-learn` покрывает всё,
что делает листинг выше, включая OOB.

```python
from sklearn.ensemble import RandomForestClassifier

clf = RandomForestClassifier(
    n_estimators=25,
    max_depth=10,
    max_features=2,        # mtry
    min_samples_split=4,
    bootstrap=True,
    oob_score=True,        # включает OOB-оценку
    n_jobs=-1,             # деревья независимы, параллелятся идеально
    random_state=0
)
clf.fit(X_train, y_train)

print("контрольная:", clf.score(X_test, y_test))
print("OOB:        ", clf.oob_score_)
print("важности:   ", clf.feature_importances_)
```

Доля голосов — то, ради чего в SOC лес и берут, — доступна как вероятность
положительного класса:

```python
scores = clf.predict_proba(X_new)[:, 1]
uncertain = X_new[(scores > 0.35) & (scores < 0.65)]
```

Эти `uncertain` и есть очередь ручного разбора. Её размер стоит измерить
до внедрения: если она составляет треть потока, фильтр не решает исходную
задачу.

Три параметра, которые в реальной работе стоит перебрать по OOB, а не по
контрольной выборке: `max_features`, `min_samples_leaf` и `class_weight`.
Последний почти всегда нужен: вредоносных файлов в потоке на порядки меньше,
чем чистых, а `accuracy` на несбалансированных данных не значит ничего.

### А что реально считает интерактив в этом приложении

Kotlin-версия в `RfLab.kt` повторяет листинг строка в строку, включая LCG
без скремблирования сида. Одно место написано иначе — накопление кривой по
числу деревьев.

```kotlin
for (k in 0 until m) {
    val tree = forest.trees[k]
    for (i in test.indices) {
        if (predict(tree, test[i].x)) testPos[i]++
    }
    for (j in forest.oob[k]) {
        oobTotal[j]++
        if (predict(tree, train[j].x)) oobPos[j]++
    }
    // точность прочитывается из уже накопленных голосов
    val votes = k + 1
    var ok = 0
    for (i in test.indices) {
        val yes = testPos[i] >= votes - testPos[i]
        if (yes == test[i].malware) ok++
    }
    testCurve[k] = ok.toDouble() / test.size
}
```

Голоса накапливаются, а не пересчитываются заново для каждой длины префикса.
Прямолинейная реализация прогоняла бы все k деревьев по всей контрольной
выборке для каждого k — то есть выполняла бы порядка B в квадрате прогонов
вместо B. При пятидесяти деревьях это разница в двадцать пять раз, и она
отчётливо ощущается пальцем на ползунке.

### Важная оговорка

Все числа этой темы получены на синтетической коллекции, размеченной
известным правилом. Это сделано намеренно: только так можно показать, что лес
восстанавливает взаимодействия признаков, которых одиночное дерево на той же
глубине не удерживает.

На настоящей коллекции PE-файлов картина будет другой в двух отношениях.

Во-первых, классы окажутся сильно несбалансированы, и `accuracy` перестанет
быть осмысленной метрикой — смотреть придётся на precision, recall и цену
пропуска, как в теме «Логистическая регрессия».

Во-вторых, распределение признаков поедет со временем: выйдет новый
упаковщик, и энтропия перестанет значить то, что значила. Для статических
детекторов ВПО это не редкость, а норма, и модель приходится переобучать
регулярно. OOB-оценка здесь особенно удобна: она позволяет следить за
качеством переобучения, не откладывая каждый раз свежую контрольную выборку.
