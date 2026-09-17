"""
Калибровка интерактива dt (ИБ): дерево решений строит интерпретируемые
правила эскалации инцидента в SOC.

Два признака:
  failedLogins — число неудачных входов перед событием, 0..50
  privLevel    — уровень привилегий учётной записи, 0..1

Главный сюжет — ПЕРЕОБУЧЕНИЕ ПО ГЛУБИНЕ: точность на обучающей выборке
растёт монотонно, на контрольной достигает максимума и падает. Классический
сюжет деревьев, и его нужно увидеть в числах до того, как писать Kotlin.
"""
import math

class Lcg:
    def __init__(self, seed): self.s = seed & 0xFFFFFFFFFFFF
    def nf(self):
        self.s = (self.s * 0x5DEECE66D + 0xB) & 0xFFFFFFFFFFFF
        return (self.s >> 24) / float(1 << 24)

LOGINS_MAX = 50.0

def true_rule(logins, priv):
    """Настоящая политика эскалации — кусочно-постоянная, то есть ровно того
    вида, который дерево умеет выражать точно."""
    if priv > 0.85:
        return True                      # администратор домена — всегда
    if priv > 0.60 and logins > 8:
        return True                      # привилегированная учётка + подбор
    if logins > 30:
        return True                      # массовый подбор по любой учётке
    return False

def gen(seed, n, noise):
    rnd = Lcg(seed)
    data = []
    for _ in range(n):
        logins = rnd.nf() * LOGINS_MAX
        priv = rnd.nf()
        lab = true_rule(logins, priv)
        if rnd.nf() < noise:
            lab = not lab                # аналитик ошибся при разборе
        data.append(((logins, priv), lab))
    return data

def impurity(labels, criterion):
    if not labels: return 0.0
    p1 = sum(1 for v in labels if v) / len(labels)
    p0 = 1.0 - p1
    if criterion == 'gini':
        return 1.0 - (p0*p0 + p1*p1)
    def term(p): return 0.0 if p <= 0 else -p * math.log(p, 2)
    return term(p0) + term(p1)

def fval(p, f): return p[0] if f == 0 else p[1]

def best_split(data, criterion):
    parent = impurity([y for _, y in data], criterion)
    best = (0.0, None, None)
    n = float(len(data))
    for f in (0, 1):
        vals = sorted(set(fval(x, f) for x, _ in data))
        # пороги — середины между соседними значениями, как в настоящих реализациях
        for i in range(len(vals) - 1):
            t = (vals[i] + vals[i+1]) / 2.0
            left = [(x, y) for x, y in data if fval(x, f) <= t]
            right = [(x, y) for x, y in data if fval(x, f) > t]
            if not left or not right: continue
            w = (len(left)/n) * impurity([y for _, y in left], criterion) + \
                (len(right)/n) * impurity([y for _, y in right], criterion)
            gain = parent - w
            if gain > best[0]:
                best = (gain, f, t)
    return (best[1], best[2]) if best[1] is not None else None

class Node:
    def __init__(self, feature=None, threshold=None, left=None, right=None, pred=None, n=0):
        self.feature, self.threshold = feature, threshold
        self.left, self.right, self.pred, self.n = left, right, pred, n

def majority(data):
    t = sum(1 for _, y in data if y)
    return t >= len(data) - t

def build(data, criterion, max_depth, min_split, depth=0):
    if not data: return Node(pred=False, n=0)
    if all(y == data[0][1] for _, y in data) or depth >= max_depth or len(data) < min_split:
        return Node(pred=majority(data), n=len(data))
    sp = best_split(data, criterion)
    if sp is None: return Node(pred=majority(data), n=len(data))
    f, t = sp
    left = [(x, y) for x, y in data if fval(x, f) <= t]
    right = [(x, y) for x, y in data if fval(x, f) > t]
    if not left or not right: return Node(pred=majority(data), n=len(data))
    return Node(f, t,
                build(left, criterion, max_depth, min_split, depth+1),
                build(right, criterion, max_depth, min_split, depth+1),
                n=len(data))

def predict(node, x):
    while node.pred is None:
        node = node.left if fval(x, node.feature) <= node.threshold else node.right
    return node.pred

def accuracy(tree, data):
    return sum(1 for x, y in data if predict(tree, x) == y) / len(data) if data else 0.0

def leaves(node):
    return 1 if node.pred is not None else leaves(node.left) + leaves(node.right)

def depth_of(node):
    return 0 if node.pred is not None else 1 + max(depth_of(node.left), depth_of(node.right))

N_TRAIN, N_TEST = 260, 140

print("=== A. ГЛУБИНА: переобучение (шум 10%, мин. для разбиения 4, Джини) ===")
tr = gen(42, N_TRAIN, 0.10); te = gen(777, N_TEST, 0.10)
print("  глубина  обучающая  контрольная  листьев  реальная глубина")
for d in range(1, 13):
    t = build(tr, 'gini', d, 4)
    print(f"  {d:6d}   {accuracy(t,tr):.4f}     {accuracy(t,te):.4f}      {leaves(t):4d}     {depth_of(t)}")
print()

print("=== B. ГЛУБИНА без шума ===")
tr0 = gen(42, N_TRAIN, 0.0); te0 = gen(777, N_TEST, 0.0)
print("  глубина  обучающая  контрольная  листьев")
for d in [1,2,3,4,5,6,8,10]:
    t = build(tr0, 'gini', d, 4)
    print(f"  {d:6d}   {accuracy(t,tr0):.4f}     {accuracy(t,te0):.4f}      {leaves(t):4d}")
print()

print("=== C. мин. число объектов для разбиения (глубина 10, шум 10%) ===")
for ms in [2, 4, 8, 16, 32, 64]:
    t = build(tr, 'gini', 10, ms)
    print(f"  min_split={ms:3d}  обучающая={accuracy(t,tr):.4f}  контрольная={accuracy(t,te):.4f}  листьев={leaves(t):3d}")
print()

print("=== D. критерий (глубина 4, шум 10%) ===")
for c in ['gini', 'entropy']:
    t = build(tr, c, 4, 4)
    print(f"  {c:8s} обучающая={accuracy(t,tr):.4f} контрольная={accuracy(t,te):.4f} листьев={leaves(t)}")
print()

print("=== E. шум разметки (глубина 4 и 12) ===")
for nz in [0.0, 0.05, 0.15, 0.30]:
    a = gen(42, N_TRAIN, nz); b = gen(777, N_TEST, nz)
    t4 = build(a, 'gini', 4, 4); t12 = build(a, 'gini', 12, 4)
    print(f"  шум {nz:4.2f}: глубина4 контр={accuracy(t4,b):.4f}  глубина12 контр={accuracy(t12,b):.4f}")
print()

print("=== F. эталон: глубина 4, мин. 4, Джини, шум 10% ===")
t = build(tr, 'gini', 4, 4)
print(f"  обучающая={accuracy(t,tr):.4f} контрольная={accuracy(t,te):.4f} листьев={leaves(t)} глубина={depth_of(t)}")
def dump(node, ind=0, lab="корень"):
    pad = "  " * ind
    if node.pred is not None:
        print(f"{pad}{lab}: {'ЭСКАЛИРОВАТЬ' if node.pred else 'не эскалировать'} ({node.n} записей)")
    else:
        name = "неудачных входов" if node.feature == 0 else "уровень привилегий"
        print(f"{pad}{lab}: {name} <= {node.threshold:.3f}?")
        dump(node.left, ind+1, "да ")
        dump(node.right, ind+1, "нет")
dump(t)
