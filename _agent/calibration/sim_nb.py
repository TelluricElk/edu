"""
Калибровка интерактива nb (ИБ): гауссовский наивный Байес на детекте
DGA-доменов (доменов, сгенерированных алгоритмом внутри вредоноса).

Два признака:
  x1 — энтропия Шеннона имени домена, нормализованная в [0,1]
  x2 — доля цифр в имени, [0,1]

Главный учебный сюжет темы — НАИВНОЕ ДОПУЩЕНИЕ. Слайдер корреляции признаков
внутри классов позволяет увидеть, как модель деградирует, когда допущение о
независимости нарушается, — причём уверенность модели при этом не падает.
"""
import math

class Lcg:
    def __init__(self, seed): self.s = seed & 0xFFFFFFFFFFFF
    def nf(self):
        self.s = (self.s * 0x5DEECE66D + 0xB) & 0xFFFFFFFFFFFF
        return (self.s >> 24) / float(1 << 24)
    def gauss(self, mu, sd):
        return mu + sd * (sum(self.nf() for _ in range(6)) - 3.0) / math.sqrt(0.5)

def clamp01(v): return 0.0 if v < 0 else (1.0 if v > 1 else v)

# параметры классов: (mu1, sd1, mu2, sd2)
LEGIT = (0.45, 0.13, 0.07, 0.07)
DGA   = (0.78, 0.11, 0.30, 0.15)

def gen(seed, n, dga_rate, corr):
    """corr — сила корреляции признаков ВНУТРИ класса (общий скрытый фактор).
    При corr=0 признаки независимы и наивное допущение верно."""
    rnd = Lcg(seed)
    data = []
    for _ in range(n):
        is_dga = 1.0 if rnd.nf() < dga_rate else 0.0
        m1, s1, m2, s2 = DGA if is_dga else LEGIT
        shared = rnd.gauss(0.0, 1.0)          # общий фактор
        e1 = rnd.gauss(0.0, 1.0)
        e2 = rnd.gauss(0.0, 1.0)
        k = math.sqrt(corr)
        z1 = k * shared + math.sqrt(1 - corr) * e1
        z2 = k * shared + math.sqrt(1 - corr) * e2
        data.append(([clamp01(m1 + s1 * z1), clamp01(m2 + s2 * z2)], is_dga))
    return data

def fit(data, var_smoothing):
    stats = {}
    for cls in (0.0, 1.0):
        pts = [x for x, y in data if y == cls]
        if len(pts) < 2:
            stats[cls] = None
            continue
        means, vars_ = [], []
        for j in (0, 1):
            vals = [p[j] for p in pts]
            m = sum(vals) / len(vals)
            v = sum((t - m) ** 2 for t in vals) / len(vals)
            means.append(m)
            vars_.append(max(v + var_smoothing, 1e-6))
        stats[cls] = (means, vars_, len(pts) / len(data))
    return stats

def log_gauss(x, m, v):
    return -0.5 * math.log(2 * math.pi * v) - (x - m) ** 2 / (2 * v)

def proba_dga(x, stats, prior_dga):
    """Априорная вероятность задаётся ИЗВНЕ (слайдером), а не берётся
    из выборки: именно так делают в SOC, где базовая частота известна
    из наблюдения потока, а обучающая выборка сбалансирована искусственно."""
    if stats[0.0] is None or stats[1.0] is None:
        return 0.5
    lp = {}
    for cls, prior in ((0.0, 1 - prior_dga), (1.0, prior_dga)):
        m, v, _ = stats[cls]
        lp[cls] = math.log(max(prior, 1e-12)) + sum(log_gauss(x[j], m[j], v[j]) for j in (0, 1))
    mx = max(lp.values())
    a = math.exp(lp[1.0] - mx)
    b = math.exp(lp[0.0] - mx)
    return a / (a + b)

def evaluate(test, stats, prior, thr):
    tp = fp = tn = fn = 0
    for x, y in test:
        p = proba_dga(x, stats, prior)
        pred = 1.0 if p >= thr else 0.0
        if pred == 1 and y == 1: tp += 1
        elif pred == 1 and y == 0: fp += 1
        elif pred == 0 and y == 0: tn += 1
        else: fn += 1
    prec = tp / (tp + fp) if tp + fp else 0.0
    rec = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0.0
    acc = (tp + tn) / max(1, len(test))
    return tp, fp, tn, fn, prec, rec, f1, acc

def mean_conf(test, stats, prior):
    """Средняя уверенность модели в своём ответе — показывает, что при
    нарушении независимости модель ошибается, но НЕ сомневается."""
    s = 0.0
    for x, _ in test:
        p = proba_dga(x, stats, prior)
        s += max(p, 1 - p)
    return s / len(test)

DGA_RATE = 0.35

print("=== A. корреляция признаков: главный сюжет темы ===")
print("  corr   F1     acc    ср.уверенность")
for corr in [0.0, 0.2, 0.4, 0.6, 0.8, 0.95]:
    tr = gen(42, 300, DGA_RATE, corr)
    te = gen(777, 200, DGA_RATE, corr)
    st = fit(tr, 0.0)
    r = evaluate(te, st, DGA_RATE, 0.5)
    print(f"  {corr:4.2f}  {r[6]:.3f}  {r[7]:.3f}   {mean_conf(te, st, DGA_RATE):.3f}")
print()

print("=== B. априорная вероятность DGA (corr=0, thr=0.5) ===")
tr = gen(42, 300, DGA_RATE, 0.0); te = gen(777, 200, DGA_RATE, 0.0); st = fit(tr, 0.0)
for pr in [0.01, 0.05, 0.15, 0.35, 0.5, 0.8]:
    r = evaluate(te, st, pr, 0.5)
    print(f"  prior={pr:4.2f}  TP={r[0]:3d} FP={r[1]:3d} FN={r[3]:3d}  prec={r[4]:.3f} rec={r[5]:.3f} F1={r[6]:.3f}")
print()

print("=== C. размер обучающей выборки (corr=0) ===")
for n in [10, 20, 40, 80, 150, 300, 600]:
    tr2 = gen(42, n, DGA_RATE, 0.0)
    st2 = fit(tr2, 0.0)
    r = evaluate(te, st2, DGA_RATE, 0.5)
    print(f"  n={n:4d}  F1={r[6]:.3f} acc={r[7]:.3f}")
print()

print("=== D. сглаживание дисперсии (corr=0, n=300) ===")
for vs in [0.0, 0.001, 0.005, 0.02, 0.05, 0.15]:
    st2 = fit(tr, vs)
    r = evaluate(te, st2, DGA_RATE, 0.5)
    print(f"  var_smooth={vs:5.3f}  F1={r[6]:.3f} acc={r[7]:.3f}")
print()

print("=== E. порог (corr=0, prior=0.35) ===")
for thr in [0.05, 0.2, 0.5, 0.8, 0.95]:
    r = evaluate(te, st, DGA_RATE, thr)
    print(f"  thr={thr:.2f}  TP={r[0]:3d} FP={r[1]:3d} FN={r[3]:3d}  prec={r[4]:.3f} rec={r[5]:.3f} F1={r[6]:.3f}")
print()

print("=== F. эталон: corr=0, n=300, prior=0.35, thr=0.5, var_smooth=0 ===")
r = evaluate(te, st, DGA_RATE, 0.5)
print(f"  TP={r[0]} FP={r[1]} TN={r[2]} FN={r[3]}")
print(f"  precision={r[4]:.4f} recall={r[5]:.4f} F1={r[6]:.4f} accuracy={r[7]:.4f}")
m0, v0, p0 = st[0.0]; m1, v1, p1 = st[1.0]
print(f"  легитимные: энтропия N({m0[0]:.3f}, {v0[0]:.4f}), цифры N({m0[1]:.3f}, {v0[1]:.4f})")
print(f"  DGA:        энтропия N({m1[0]:.3f}, {v1[0]:.4f}), цифры N({m1[1]:.3f}, {v1[1]:.4f})")
print(f"  доля DGA в обучающей выборке: {p1:.3f}")
