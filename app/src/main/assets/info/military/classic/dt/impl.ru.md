## Программная реализация

### Реализация с нуля на Python

Полный рабочий код: генерация истории инцидентов по заданной политике,
построение дерева, два критерия неоднородности, два ограничителя роста и
печать дерева в виде читаемых правил.

```python
import math

LOGINS_MAX = 50.0


class Lcg:
    """LCG с параметрами java.util.Random — тот же генератор стоит в Kotlin,
    поэтому история инцидентов побитово совпадает с приложением."""

    def __init__(self, seed):
        self.s = seed & 0xFFFFFFFFFFFF

    def nf(self):
        self.s = (self.s * 0x5DEECE66D + 0xB) & 0xFFFFFFFFFFFF
        return (self.s >> 24) / float(1 << 24)


def true_rule(logins, priv):
    """Настоящая политика эскалации. Модели её не показывают — она нужна
    только чтобы сверить то, что дерево выведет из данных."""
    if priv > 0.85:
        return True                       # администратор домена — всегда
    if priv > 0.60 and logins > 8:
        return True                       # подбор к привилегированной учётке
    if logins > 30:
        return True                       # массовый подбор по любой учётке
    return False


def generate(seed, n, noise):
    """noise — доля инцидентов с неверным вердиктом: аналитик ошибся."""
    rnd = Lcg(seed)
    data = []
    for _ in range(n):
        logins = rnd.nf() * LOGINS_MAX
        priv = rnd.nf()
        label = true_rule(logins, priv)
        if rnd.nf() < noise:
            label = not label
        data.append(((logins, priv), label))
    return data


def impurity(labels, criterion):
    if not labels:
        return 0.0
    p = sum(1 for v in labels if v) / len(labels)
    q = 1.0 - p
    if criterion == "gini":
        return 1.0 - (p * p + q * q)
    def term(v):
        return 0.0 if v <= 0 else -v * math.log(v, 2)
    return term(p) + term(q)


def fval(x, feature):
    return x[0] if feature == 0 else x[1]


def best_split(data, criterion):
    """Перебор всех признаков и всех порогов. Пороги — середины между
    соседними уникальными значениями: порог ровно по наблюдению зависел бы
    от соглашения о строгости неравенства."""
    parent = impurity([y for _, y in data], criterion)
    best_gain, best_f, best_t = 0.0, None, None
    n = float(len(data))
    for f in (0, 1):
        values = sorted(set(fval(x, f) for x, _ in data))
        for i in range(len(values) - 1):
            t = (values[i] + values[i + 1]) / 2.0
            left = [(x, y) for x, y in data if fval(x, f) <= t]
            right = [(x, y) for x, y in data if fval(x, f) > t]
            if not left or not right:
                continue
            # взвешивание по размеру обязательно: без него разбиение,
            # отрезающее одно наблюдение в чистый лист, выглядело бы лучшим
            w = (len(left) / n) * impurity([y for _, y in left], criterion) + \
                (len(right) / n) * impurity([y for _, y in right], criterion)
            if parent - w > best_gain:
                best_gain, best_f, best_t = parent - w, f, t
    return (best_f, best_t) if best_f is not None else None


class Node:
    def __init__(self, feature=None, threshold=None, left=None, right=None, pred=None, n=0):
        self.feature, self.threshold = feature, threshold
        self.left, self.right, self.pred, self.n = left, right, pred, n


def majority(data):
    t = sum(1 for _, y in data if y)
    return t >= len(data) - t


def build(data, criterion, max_depth, min_split, depth=0):
    if not data:
        return Node(pred=False, n=0)
    if all(y == data[0][1] for _, y in data) or depth >= max_depth or len(data) < min_split:
        return Node(pred=majority(data), n=len(data))
    split = best_split(data, criterion)
    if split is None:
        return Node(pred=majority(data), n=len(data))
    f, t = split
    left = [(x, y) for x, y in data if fval(x, f) <= t]
    right = [(x, y) for x, y in data if fval(x, f) > t]
    if not left or not right:
        return Node(pred=majority(data), n=len(data))
    return Node(f, t,
                build(left, criterion, max_depth, min_split, depth + 1),
                build(right, criterion, max_depth, min_split, depth + 1),
                n=len(data))


def predict(node, x):
    while node.pred is None:
        node = node.left if fval(x, node.feature) <= node.threshold else node.right
    return node.pred


def accuracy(tree, data):
    return sum(1 for x, y in data if predict(tree, x) == y) / len(data) if data else 0.0


def leaves(node):
    return 1 if node.pred is not None else leaves(node.left) + leaves(node.right)


def dump(node, indent=0, label="корень"):
    pad = "   " * indent
    if node.pred is not None:
        verdict = "ЭСКАЛИРОВАТЬ" if node.pred else "не эскалировать"
        print(f"{pad}{label}: {verdict} ({node.n})")
    else:
        name = "неудачных входов" if node.feature == 0 else "привилегии"
        print(f"{pad}{label}: {name} <= {node.threshold:.2f}?")
        dump(node.left, indent + 1, "да ")
        dump(node.right, indent + 1, "нет")


N_TRAIN, N_TEST, NOISE = 200, 140, 0.15
train = generate(42, N_TRAIN, NOISE)
test = generate(777, N_TEST, NOISE)

print("--- переобучение по глубине (шум 15%) ---")
print("  глубина  обучающая  контрольная  листьев")
for d in range(1, 13):
    t = build(train, "gini", d, 4)
    print(f"  {d:6d}   {accuracy(t, train):.4f}     {accuracy(t, test):.4f}      {leaves(t):4d}")

print()
print("--- второй ограничитель: минимум объектов для разбиения (глубина 10) ---")
for ms in [2, 4, 8, 16, 32, 64]:
    t = build(train, "gini", 10, ms)
    print(f"  минимум={ms:3d}: обучающая={accuracy(t, train):.4f}"
          f"  контрольная={accuracy(t, test):.4f}  листьев={leaves(t):3d}")

print()
print("--- критерий неоднородности (глубина 4) ---")
for c in ["gini", "entropy"]:
    t = build(train, c, 4, 4)
    print(f"  {c:8s}: обучающая={accuracy(t, train):.4f}"
          f"  контрольная={accuracy(t, test):.4f}  листьев={leaves(t)}")

print()
print("--- дерево на ЧИСТЫХ данных, глубина 4 ---")
clean_train = generate(42, N_TRAIN, 0.0)
clean_test = generate(777, N_TEST, 0.0)
tc = build(clean_train, "gini", 4, 4)
print(f"  обучающая={accuracy(tc, clean_train):.4f}"
      f"  контрольная={accuracy(tc, clean_test):.4f}  листьев={leaves(tc)}")
dump(tc)
```

Вывод:

```
--- переобучение по глубине (шум 15%) ---
  глубина  обучающая  контрольная  листьев
       1   0.7850     0.7143         2
       2   0.8600     0.8214         4
       3   0.8900     0.8214         7
       4   0.9150     0.8500        12
       5   0.9350     0.7929        19
       6   0.9400     0.7714        26
       7   0.9500     0.7429        29
       8   0.9500     0.7429        30
       9   0.9550     0.7357        31
      10   0.9550     0.7357        32
      11   0.9600     0.7286        33
      12   0.9600     0.7286        33

--- второй ограничитель: минимум объектов для разбиения (глубина 10) ---
  минимум=  2: обучающая=0.9950  контрольная=0.7429  листьев= 40
  минимум=  4: обучающая=0.9550  контрольная=0.7357  листьев= 32
  минимум=  8: обучающая=0.9350  контрольная=0.8071  листьев= 26
  минимум= 16: обучающая=0.9300  контрольная=0.8214  листьев= 21
  минимум= 32: обучающая=0.8950  контрольная=0.8214  листьев= 11
  минимум= 64: обучающая=0.8600  контрольная=0.8214  листьев=  6

--- критерий неоднородности (глубина 4) ---
  gini    : обучающая=0.9150  контрольная=0.8500  листьев=12
  entropy : обучающая=0.8950  контрольная=0.8000  листьев=12

--- дерево на ЧИСТЫХ данных, глубина 4 ---
  обучающая=1.0000  контрольная=0.9786  листьев=5
корень: неудачных входов <= 29.51?
   да : привилегии <= 0.60?
      да : не эскалировать (76)
      нет: неудачных входов <= 7.89?
         да : привилегии <= 0.87?
            да : не эскалировать (12)
            нет: ЭСКАЛИРОВАТЬ (6)
         нет: ЭСКАЛИРОВАТЬ (27)
   нет: ЭСКАЛИРОВАТЬ (79)
```

Три вещи, ради которых стоит смотреть на этот вывод.

**Кривые расходятся демонстративно.** Обучающая точность растёт монотонно с
0,785 до 0,960 — и это происходило бы при любых данных, даже полностью
случайных. Контрольная поднимается до 0,850 на глубине четыре и падает до
0,729. Разница между максимумом и концом — двенадцать процентных пунктов,
потерянных исключительно на заучивании чужих ошибок.

**Второй ограничитель работает не хуже первого.** При фиксированной глубине 10
поднятие минимума с 2 до 16 возвращает контрольную точность с 0,743 до 0,821.
Предельная глубина при этом не менялась вовсе — ветви обрезаются там, где они
обслуживают единичные наблюдения.

**На чистых данных дерево читает политику обратно.** Пять листьев, обучающая
точность единица, и пороги:

    29,51 против настоящего 30
    0,60 против настоящего 0,60
    7,89 против настоящего 8
    0,87 против настоящего 0,85

Модели никто не говорил, из какого правила сгенерированы данные. Она вывела
его из двухсот примеров и выдала в том же виде, в котором его писал бы
человек.

### Готовое решение

```python
import numpy as np
from sklearn.tree import DecisionTreeClassifier, export_text, plot_tree
from sklearn.model_selection import GridSearchCV, validation_curve

X = np.array([x for x, _ in train])
y = np.array([lab for _, lab in train])
X_test = np.array([x for x, _ in test])
y_test = np.array([lab for _, lab in test])

tree = DecisionTreeClassifier(
    criterion="gini",
    max_depth=4,
    min_samples_split=4,
    random_state=0,
)
tree.fit(X, y)

# главное, ради чего берут деревья: правила в читаемом виде
print(export_text(tree, feature_names=["failedLogins", "privLevel"]))
print("важность признаков:", tree.feature_importances_)
```

Три замечания по этому листингу.

Никакого `StandardScaler` в пайплайне нет, и это не забывчивость. Дерево
сравнивает признак с порогом, а такое сравнение не меняется от умножения
признака на константу. После тем, где нормализация решала всё, это стоит
отметить отдельно.

`export_text` — то, ради чего деревья держат в безопасности. Результат
вставляется в плейбук почти без правки.

В sklearn есть усечение, которого нет в нашей реализации: параметр `ccp_alpha`
включает усечение по стоимости и сложности. Подбирают его так:

```python
path = tree.cost_complexity_pruning_path(X, y)
for alpha in path.ccp_alphas:
    t = DecisionTreeClassifier(ccp_alpha=alpha, random_state=0).fit(X, y)
    print(f"alpha={alpha:.5f}  листьев={t.get_n_leaves()}  тест={t.score(X_test, y_test):.4f}")
```

Это обычно работает лучше, чем подбор предельной глубины: усечение режет
именно бесполезные ветви, а глубина рубит все одинаково.

### А что реально считает интерактив в этом приложении

В приложении тот же алгоритм на Kotlin, в `DtLab.kt`. Поиск лучшего разбиения:

```kotlin
private fun bestSplit(data: List<IncidentCase>, criterion: DtCriterion): Pair<Int, Double>? {
    val parent = impurity(data, criterion)
    var bestGain = 0.0
    var bestFeature = -1
    var bestThreshold = 0.0
    val n = data.size.toDouble()

    for (f in 0 until N_FEAT) {
        val values = data.map { it.x[f] }.distinct().sorted()
        for (i in 0 until values.size - 1) {
            val t = (values[i] + values[i + 1]) / 2.0
            val left = data.filter { it.x[f] <= t }
            val right = data.filter { it.x[f] > t }
            if (left.isEmpty() || right.isEmpty()) continue
            val weighted = (left.size / n) * impurity(left, criterion) +
                (right.size / n) * impurity(right, criterion)
            if (parent - weighted > bestGain) {
                bestGain = parent - weighted
                bestFeature = f
                bestThreshold = t
            }
        }
    }
    return if (bestFeature >= 0) bestFeature to bestThreshold else null
}
```

Дерево на экране рисуется рекурсивно, с подписями порогов в узлах и числом
записей в листьях. Рядом — карта решений, на которой хорошо видно, что все
границы параллельны осям: диагонали дерево изображает лесенкой.

Обе кривые точности, обучающая и контрольная, нарисованы на одном графике по
всему диапазону глубины — именно их расхождение и есть содержание темы.

### Важная оговорка

**Два признака вместо десятков.** Настоящее правило эскалации учитывает
критичность актива, время суток, источник события, наличие подтверждённой
компрометации у соседних хостов. Два признака взяты, чтобы карта решений
помещалась на экран, а дерево можно было нарисовать целиком.

**Политика кусочно-постоянная.** Настоящая политика, из которой генерируется
история, специально составлена из пороговых правил — то есть ровно того вида,
который дерево выражает точно. Это делает демонстрацию честной по части
интерпретируемости, но льстит методу: будь зависимость диагональной, дерево
приближало бы её лесенкой и выглядело бы куда хуже.

**Усечения нет.** Реализованы только два ограничителя роста. Усечение по
стоимости и сложности, как в CART, дало бы лучший результат, но потребовало бы
отдельной отложенной выборки внутри обучения и усложнило бы экран.

**Шум разметки смоделирован симметричным.** Мы переворачиваем вердикт в обе
стороны с равной вероятностью. В жизни ошибки разбора смещены: настоящий
инцидент закрывают как ложное срабатывание чаще, чем наоборот, — и такое
смещение опаснее, потому что систематически двигает границу в одну сторону.

**Одно дерево неустойчиво.** Поменяйте долю шума на пару процентов, и
структура дерева может измениться целиком при почти той же точности. Это не
дефект реализации, а свойство метода — и причина, по которой существуют
следующие две темы приложения.
