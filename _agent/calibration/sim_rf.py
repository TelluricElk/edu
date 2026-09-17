# -*- coding: utf-8 -*-
"""Калибровка темы `rf` — случайный лес на детекте ВПО по признакам PE-файла.
LCG совпадает с java.util.Random без скремблирования сида — как в Kotlin."""
import math

MASK = (1 << 48) - 1
MUL = 0x5DEECE66D
ADD = 0xB

class Lcg:
    def __init__(self, seed):
        self.s = seed & MASK
    def next_double(self):
        self.s = (self.s * MUL + ADD) & MASK
        return (self.s >> 24) / float(1 << 24)
    def next_int(self, bound):
        return int(self.next_double() * bound) % bound

N_FEAT = 6
NAMES = ["энтропия .text", "доля подозр. импортов", "число секций",
         "оверлей, КБ", "подпись", "доля URL-строк"]

def true_rule(x):
    ent, susp, sec, ovl, sign, urls = x
    score = (3.4 * (ent - 6.25) / 1.75
             + 1.0 * (susp - 0.5)
             + 0.55 * (urls - 0.5)
             + 0.45 * ((sec - 7.0) / 5.0)
             + 0.5 * (ovl / 400.0 - 0.5)
             - 0.9 * (sign - 0.5))
    if ent > 7.0 and susp > 0.6:
        score += 1.3
    if sign > 0.8 and susp < 0.3:
        score -= 1.1
    return score > 0.30

def generate(seed, n, noise):
    """Признаки независимы. Метка — детерминированная функция признаков,
    после чего доля [noise] вердиктов аналитика переворачивается."""
    rnd = Lcg(seed)
    out = []
    for _ in range(n):
        ent = 4.5 + rnd.next_double() * 3.5
        susp = rnd.next_double()
        sec = 2.0 + rnd.next_double() * 10.0
        ovl = rnd.next_double() * 400.0
        sign = rnd.next_double()
        urls = rnd.next_double()
        x = [ent, susp, sec, ovl, sign, urls]
        y = true_rule(x)
        if rnd.next_double() < noise:
            y = not y
        out.append((x, y))
    return out


def forest_no_bootstrap(train, crit, n_trees, max_depth, min_split, mtry):
    """Без бутстрапа: каждое дерево видит всю выборку целиком."""
    trees = []
    for i in range(n_trees):
        rnd = Lcg(1000 + i * 7919)
        trees.append(build(train, crit, max_depth, min_split, mtry, rnd))
    return trees

def impurity(data, crit):
    if not data: return 0.0
    pos = sum(1 for _, y in data if y)
    p = pos / len(data); q = 1 - p
    if crit == 'gini':
        return 1 - (p*p + q*q)
    t = 0.0
    for v in (p, q):
        if v > 0: t += -v * math.log(v, 2)
    return t

def majority(data):
    pos = sum(1 for _, y in data if y)
    return pos >= len(data) - pos

def best_split(data, crit, feats):
    """Один проход по отсортированным значениям с накопительными счётчиками:
    O(n log n) на признак вместо O(n^2). В Kotlin реализовано так же — иначе
    пересчёт леса на каждое движение ползунка не укладывался бы в кадр."""
    n = len(data)
    pos_all = sum(1 for _, y in data if y)
    parent = imp_counts(pos_all, n - pos_all, crit)
    bg, bf, bt = 0.0, -1, 0.0
    for f in feats:
        order = sorted(data, key=lambda d: d[0][f])
        lp = ln = 0
        for i in range(n - 1):
            if order[i][1]: lp += 1
            else: ln += 1
            v, w = order[i][0][f], order[i + 1][0][f]
            if v == w: continue
            left = lp + ln
            right = n - left
            wimp = (left / n) * imp_counts(lp, ln, crit) + \
                   (right / n) * imp_counts(pos_all - lp, (n - pos_all) - ln, crit)
            if parent - wimp > bg:
                bg, bf, bt = parent - wimp, f, (v + w) / 2.0
    return (bf, bt) if bf >= 0 else None

def imp_counts(pos, neg, crit):
    t = pos + neg
    if t == 0: return 0.0
    p = pos / t; q = neg / t
    if crit == 'gini': return 1 - (p * p + q * q)
    r = 0.0
    for v in (p, q):
        if v > 0: r += -v * math.log(v, 2)
    return r

class Node:
    def __init__(self, f=-1, t=0.0, l=None, r=None, p=None, n=0):
        self.f, self.t, self.l, self.r, self.p, self.n = f, t, l, r, p, n
    @property
    def leaf(self): return self.p is not None

def build(data, crit, max_depth, min_split, mtry, rnd, depth=0):
    if not data: return Node(p=False)
    first = data[0][1]
    if all(y == first for _, y in data) or depth >= max_depth or len(data) < min_split:
        return Node(p=majority(data), n=len(data))
    feats = pick_features(mtry, rnd)
    s = best_split(data, crit, feats)
    if s is None: return Node(p=majority(data), n=len(data))
    f, t = s
    left = [d for d in data if d[0][f] <= t]
    right = [d for d in data if d[0][f] > t]
    if not left or not right: return Node(p=majority(data), n=len(data))
    return Node(f, t,
                build(left, crit, max_depth, min_split, mtry, rnd, depth+1),
                build(right, crit, max_depth, min_split, mtry, rnd, depth+1),
                None, len(data))

def pick_features(mtry, rnd):
    if mtry >= N_FEAT: return list(range(N_FEAT))
    pool = list(range(N_FEAT))
    out = []
    for _ in range(mtry):
        j = rnd.next_int(len(pool))
        out.append(pool.pop(j))
    return sorted(out)

def predict(node, x):
    while not node.leaf:
        node = node.l if x[node.f] <= node.t else node.r
    return node.p

def accuracy(tree, data):
    if not data: return 0.0
    return sum(1 for x, y in data if predict(tree, x) == y) / len(data)

def forest(train, crit, n_trees, max_depth, min_split, mtry, bag):
    trees, oob = [], []
    for i in range(n_trees):
        rnd = Lcg(1000 + i * 7919)
        m = int(round(len(train) * bag))
        idx = [rnd.next_int(len(train)) for _ in range(m)]
        sample = [train[j] for j in idx]
        used = set(idx)
        trees.append(build(sample, crit, max_depth, min_split, mtry, rnd))
        oob.append([j for j in range(len(train)) if j not in used])
    return trees, oob

def predict_forest(trees, x):
    v = sum(1 for t in trees if predict(t, x))
    return v >= len(trees) - v

def acc_forest(trees, data):
    if not data: return 0.0
    return sum(1 for x, y in data if predict_forest(trees, x) == y) / len(data)

def oob_accuracy(trees, oob, train):
    ok = tot = 0
    for j, (x, y) in enumerate(train):
        votes = [predict(trees[k], x) for k in range(len(trees)) if j in set(oob[k])]
        if not votes: continue
        tot += 1
        p = sum(1 for v in votes if v)
        if (p >= len(votes) - p) == y: ok += 1
    return ok / tot if tot else 0.0

def importance(trees):
    imp = [0.0] * N_FEAT
    def walk(nd):
        if nd.leaf: return
        imp[nd.f] += nd.n
        walk(nd.l); walk(nd.r)
    for t in trees: walk(t)
    s = sum(imp)
    return [v / s for v in imp] if s > 0 else imp

if __name__ == '__main__':
    TRAIN, TEST, NOISE = 240, 160, 0.12
    train = generate(42, TRAIN, NOISE)
    test = generate(777, TEST, NOISE)
    clean = generate(777, TEST, 0.0)
    print("доля ВПО в обучающей: %.3f" % (sum(1 for _, y in train if y) / len(train)))
    print("доля ВПО в контрольной: %.3f" % (sum(1 for _, y in test if y) / len(test)))
    print()
    print("глубина | одно дерево train/test | лес(25, mtry=2) train/test")
    for d in (2, 4, 6, 8, 10, 14):
        rnd = Lcg(5)
        one = build(train, 'gini', d, 4, N_FEAT, rnd)
        tr, ob = forest(train, 'gini', 25, d, 4, 2, 1.0)
        print("  %2d    |   %.4f / %.4f     |   %.4f / %.4f"
              % (d, accuracy(one, train), accuracy(one, test),
                 acc_forest(tr, train), acc_forest(tr, test)))
