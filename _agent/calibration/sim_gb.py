# -*- coding: utf-8 -*-
"""Калибровка темы `gb` — градиентный бустинг на прогнозе ущерба от инцидента
по времени до обнаружения. LCG совпадает с java.util.Random без скремблирования."""
import math

MASK = (1 << 48) - 1

class Lcg:
    def __init__(self, seed):
        self.s = seed & MASK
    def next_double(self):
        self.s = (self.s * 0x5DEECE66D + 0xB) & MASK
        return (self.s >> 24) / float(1 << 24)

DWELL_MAX = 120.0

def true_damage(d):
    """Ущерб в млн руб. Три слагаемых:
       быстрый рост в первые дни — закрепление и кража учёток;
       линейный рост — продолжающаяся эксфильтрация;
       СТУПЕНЬКА на 60 днях — сработала шифровальная нагрузка, ущерб скачком растёт."""
    base = 0.8 + 6.5 * (1.0 - math.exp(-d / 18.0)) + 0.02 * d
    if d > 60.0:
        base += 3.5
    return base

def generate(seed, n, noise):
    rnd = Lcg(seed)
    out = []
    for _ in range(n):
        d = rnd.next_double() * DWELL_MAX
        y = true_damage(d) + (rnd.next_double() * 2.0 - 1.0) * noise
        out.append((d, y))
    return out

# ---------- регрессионное дерево по одному признаку ----------
class Node:
    def __init__(self, t=0.0, l=None, r=None, v=None):
        self.t, self.l, self.r, self.v = t, l, r, v
    @property
    def leaf(self):
        return self.v is not None

def build(xs, res, idx, depth, min_leaf):
    if depth == 0 or len(idx) < 2 * min_leaf:
        return Node(v=sum(res[i] for i in idx) / len(idx))
    order = sorted(idx, key=lambda i: xs[i])
    n = len(order)
    total = sum(res[i] for i in order)
    best_gain, best_t, best_k = -1.0, 0.0, -1
    ls = 0.0
    for k in range(n - 1):
        ls += res[order[k]]
        if xs[order[k]] == xs[order[k + 1]]:
            continue
        nl = k + 1
        nr = n - nl
        if nl < min_leaf or nr < min_leaf:
            continue
        gain = ls * ls / nl + (total - ls) ** 2 / nr
        if gain > best_gain:
            best_gain, best_k = gain, k
            best_t = (xs[order[k]] + xs[order[k + 1]]) / 2.0
    if best_k < 0:
        return Node(v=total / n)
    left = [i for i in order if xs[i] <= best_t]
    right = [i for i in order if xs[i] > best_t]
    return Node(best_t,
                build(xs, res, left, depth - 1, min_leaf),
                build(xs, res, right, depth - 1, min_leaf))

def tree_predict(node, x):
    while not node.leaf:
        node = node.l if x <= node.t else node.r
    return node.v

# ---------- бустинг ----------
def train(data, n_est, lr, depth, min_leaf, test):
    xs = [p[0] for p in data]
    ys = [p[1] for p in data]
    f0 = sum(ys) / len(ys)
    pred = [f0] * len(data)
    tpred = [f0] * len(test)
    trees = []
    hist_tr, hist_te = [], []
    idx = list(range(len(data)))
    for _ in range(n_est):
        res = [ys[i] - pred[i] for i in idx]
        t = build(xs, res, idx, depth, min_leaf)
        trees.append(t)
        for i in idx:
            pred[i] += lr * tree_predict(t, xs[i])
        for i in range(len(test)):
            tpred[i] += lr * tree_predict(t, test[i][0])
        hist_tr.append(sum((ys[i] - pred[i]) ** 2 for i in idx) / len(idx))
        hist_te.append(sum((test[i][1] - tpred[i]) ** 2 for i in range(len(test))) / len(test))
    return f0, trees, hist_tr, hist_te

def predict(f0, trees, lr, x):
    p = f0
    for t in trees:
        p += lr * tree_predict(t, x)
    return p

if __name__ == '__main__':
    TR, TE, NOISE = 60, 40, 1.8
    train_set = generate(42, TR, NOISE)
    test_set = generate(909, TE, NOISE)
    print("диапазон ущерба: %.2f .. %.2f млн" % (min(y for _, y in train_set), max(y for _, y in train_set)))
    print("дисперсия контрольной (MSE константы) %.4f"
          % (sum((y - sum(b for _, b in test_set) / TE) ** 2 for _, y in test_set) / TE))
    print()
    print("lr    | итераций до минимума | минимум MSE(контр) | MSE на 120 итер")
    for lr in (1.0, 0.5, 0.3, 0.1, 0.05):
        f0, trees, htr, hte = train(train_set, 120, lr, 1, 3, test_set)
        best = min(range(len(hte)), key=lambda i: hte[i])
        print("%.2f  |        %3d           |      %.4f        |   %.4f"
              % (lr, best + 1, hte[best], hte[-1]))
