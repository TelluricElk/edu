# -*- coding: utf-8 -*-
"""Калибровка темы `km` — k-means на кластеризации потока алертов SIEM."""
import math

MASK = (1 << 48) - 1

class Lcg:
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

# (имя, хосты от, хосты до, минуты от, минуты до, сколько)
GROUPS = [
    ("массовое обновление ПО", 34.0, 56.0,  10.0,  90.0, 90),
    ("сканирование сети",      18.0, 52.0, 520.0, 980.0, 25),
    ("подбор пароля",           1.0,  6.0, 300.0,1320.0, 35),
    ("боковое смещение",        3.0, 13.0, 110.0, 380.0, 20),
]

def generate(seed=42):
    rnd = Lcg(seed)
    pts, truth = [], []
    for gi, (_, h0, h1, m0, m1, n) in enumerate(GROUPS):
        for _ in range(n):
            h = h0 + rnd.next_double() * (h1 - h0)
            m = m0 + rnd.next_double() * (m1 - m0)
            pts.append((h, m))
            truth.append(gi)
    return pts, truth

def scaled(pts, normalize):
    if not normalize:
        return [list(p) for p in pts]
    return [[p[0] / HOSTS_MAX, p[1] / MINUTES_MAX] for p in pts]

def d2(a, b):
    dx = a[0] - b[0]; dy = a[1] - b[1]
    return dx * dx + dy * dy

def init_random(xs, k, rnd):
    idx = []
    pool = list(range(len(xs)))
    for _ in range(k):
        idx.append(pool.pop(rnd.next_int(len(pool))))
    return [list(xs[i]) for i in idx]

def init_pp(xs, k, rnd):
    """k-means++: первый центр случайно, каждый следующий — с вероятностью,
    пропорциональной квадрату расстояния до ближайшего уже выбранного."""
    cs = [list(xs[rnd.next_int(len(xs))])]
    while len(cs) < k:
        dd = [min(d2(x, c) for c in cs) for x in xs]
        total = sum(dd)
        if total <= 0:
            cs.append(list(xs[rnd.next_int(len(xs))])); continue
        t = rnd.next_double() * total
        acc = 0.0
        for i, v in enumerate(dd):
            acc += v
            if acc >= t:
                cs.append(list(xs[i])); break
        else:
            cs.append(list(xs[-1]))
    return cs

def run(xs, k, iters, seed, pp):
    rnd = Lcg(seed)
    cs = init_pp(xs, k, rnd) if pp else init_random(xs, k, rnd)
    assign = [0] * len(xs)
    moved_last = 0
    for _ in range(iters):
        moved_last = 0
        for i, x in enumerate(xs):
            b, bd = 0, d2(x, cs[0])
            for j in range(1, k):
                dj = d2(x, cs[j])
                if dj < bd:
                    b, bd = j, dj
            if assign[i] != b:
                moved_last += 1
            assign[i] = b
        for j in range(k):
            sx = sy = 0.0; c = 0
            for i, x in enumerate(xs):
                if assign[i] == j:
                    sx += x[0]; sy += x[1]; c += 1
            if c:
                cs[j] = [sx / c, sy / c]
    inertia = sum(d2(xs[i], cs[assign[i]]) for i in range(len(xs)))
    return cs, assign, inertia, moved_last

def silhouette(xs, assign, k):
    if k < 2:
        return 0.0
    groups = [[i for i in range(len(xs)) if assign[i] == j] for j in range(k)]
    total, cnt = 0.0, 0
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

if __name__ == '__main__':
    pts, truth = generate()
    print("алертов всего:", len(pts))
    for norm in (True, False):
        xs = scaled(pts, norm)
        print()
        print("нормализация", "ВКЛ" if norm else "ВЫКЛ")
        for k in range(2, 9):
            cs, a, inert, mv = run(xs, k, 20, 7, True)
            sil = silhouette(xs, a, k)
            sizes = sorted((a.count(j) for j in range(k)), reverse=True)
            print("  k=%d инерция %10.4f силуэт %.4f размеры %s"
                  % (k, inert, sil, sizes))
