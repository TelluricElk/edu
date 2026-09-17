"""
Калибровка интерактива logr (ИБ-тематика): детект фишинга в почтовом шлюзе.

Воспроизводит РОВНО тот алгоритм, что пойдёт в Kotlin:
 - генерация датасета через LCG (тот же, что kotlin.random.Random? нет —
   поэтому в Kotlin берём свой детерминированный LCG, здесь его и моделируем)
 - логистическая регрессия на 5 признаках, полный батч-градиент
 - взвешивание положительного класса (cost-sensitive learning)
 - L2-регуляризация (bias не регуляризуется)
Цель: убедиться, что КАЖДЫЙ слайдер даёт заметный и объяснимый эффект.
"""
import math

# ---------- детерминированный LCG, идентичный будущему Kotlin-коду ----------
class Lcg:
    def __init__(self, seed):
        self.s = seed & 0xFFFFFFFFFFFF
    def next_float(self):
        # параметры java.util.Random
        self.s = (self.s * 0x5DEECE66D + 0xB) & 0xFFFFFFFFFFFF
        return (self.s >> 24) / float(1 << 24)
    def bern(self, p):
        return 1.0 if self.next_float() < p else 0.0
    def gauss(self, mu, sd):
        # сумма 6 равномерных ~ нормальное (аппроксимация Ирвина-Холла),
        # дешевле Бокса-Мюллера и не тянет log/cos в Kotlin
        s = sum(self.next_float() for _ in range(6)) - 3.0
        return mu + sd * s / math.sqrt(0.5)

N_FEAT = 5
FEATURE_NAMES = ["SPF/DKIM провален", "возраст домена", "число ссылок",
                 "тональность срочности", "риск вложения"]

def clamp01(v):
    return 0.0 if v < 0 else (1.0 if v > 1 else v)

def gen(seed, n, phish_rate=0.30):
    rnd = Lcg(seed)
    data = []
    for _ in range(n):
        is_phish = 1.0 if rnd.next_float() < phish_rate else 0.0
        if is_phish:
            spf   = rnd.bern(0.62)
            age   = clamp01(rnd.gauss(0.28, 0.20))   # молодой домен
            links = clamp01(rnd.gauss(0.58, 0.22))
            urg   = clamp01(rnd.gauss(0.63, 0.22))
            att   = rnd.bern(0.34)
        else:
            spf   = rnd.bern(0.16)
            age   = clamp01(rnd.gauss(0.68, 0.22))   # старый домен
            links = clamp01(rnd.gauss(0.34, 0.20))
            urg   = clamp01(rnd.gauss(0.34, 0.21))
            att   = rnd.bern(0.08)
        data.append(([spf, age, links, urg, att], is_phish))
    return data

def sigmoid(z):
    if z < -30: return 1e-13
    if z > 30:  return 1.0 - 1e-13
    return 1.0 / (1.0 + math.exp(-z))

def fit(data, lr, epochs, lam, w_pos, ret_hist=False):
    w = [0.0] * N_FEAT
    b = 0.0
    hist = []
    n = len(data)
    for e in range(epochs):
        gw = [0.0] * N_FEAT
        gb = 0.0
        wsum = 0.0
        loss = 0.0
        for x, y in data:
            z = sum(w[j] * x[j] for j in range(N_FEAT)) + b
            p = sigmoid(z)
            cw = w_pos if y == 1.0 else 1.0
            wsum += cw
            err = cw * (p - y)
            for j in range(N_FEAT):
                gw[j] += err * x[j]
            gb += err
            loss += -cw * (y * math.log(max(p, 1e-13)) + (1 - y) * math.log(max(1 - p, 1e-13)))
        for j in range(N_FEAT):
            gw[j] = gw[j] / wsum + lam * w[j]
            w[j] -= lr * gw[j]
        b -= lr * (gb / wsum)
        if ret_hist:
            hist.append(loss / wsum)
        if any(math.isnan(v) or abs(v) > 1e4 for v in w) or math.isnan(b):
            return w, b, True, hist
    return w, b, False, hist

def proba(x, w, b):
    return sigmoid(sum(w[j] * x[j] for j in range(N_FEAT)) + b)

def confusion(data, w, b, thr):
    tp = fp = tn = fn = 0
    for x, y in data:
        pred = 1.0 if proba(x, w, b) >= thr else 0.0
        if pred == 1 and y == 1: tp += 1
        elif pred == 1 and y == 0: fp += 1
        elif pred == 0 and y == 0: tn += 1
        else: fn += 1
    return tp, fp, tn, fn

def metrics(cm):
    tp, fp, tn, fn = cm
    prec = tp / (tp + fp) if tp + fp else 0.0
    rec = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0.0
    acc = (tp + tn) / max(1, tp + fp + tn + fn)
    return prec, rec, f1, acc

def total_cost(cm, cost_fn):
    tp, fp, tn, fn = cm
    return fp * 1.0 + fn * cost_fn

def best_threshold(data, w, b, cost_fn):
    best_t, best_c = 0.5, float("inf")
    t = 0.05
    while t <= 0.951:
        c = total_cost(confusion(data, w, b, t), cost_fn)
        if c < best_c:
            best_c, best_t = c, t
        t += 0.01
    return best_t, best_c

def auc(data, w, b):
    scored = sorted(((proba(x, w, b), y) for x, y in data), reverse=True)
    pos = sum(1 for _, y in scored if y == 1)
    neg = len(scored) - pos
    if pos == 0 or neg == 0: return 0.5
    tp = fp = 0
    prev_tp = prev_fp = 0
    area = 0.0
    for _, y in scored:
        if y == 1: tp += 1
        else: fp += 1
        area += (fp - prev_fp) * (tp + prev_tp) / 2.0
        prev_tp, prev_fp = tp, fp
    return area / (pos * neg)


train = gen(42, 300)
test = gen(777, 200)
print("train: phish =", sum(y for _, y in train), "/", len(train))
print("test:  phish =", sum(y for _, y in test), "/", len(test))
print()

print("=== 1. чувствительность к скорости обучения (epochs=200, lam=0, w_pos=1) ===")
for lr in [0.05, 0.2, 0.5, 1.0, 2.0, 4.0, 8.0, 15.0]:
    w, b, div, _ = fit(train, lr, 200, 0.0, 1.0)
    if div:
        print(f"  lr={lr:5.2f}  РАСХОДИТСЯ")
        continue
    cm = confusion(test, w, b, 0.5)
    p, r, f, a = metrics(cm)
    print(f"  lr={lr:5.2f}  acc={a:.3f} prec={p:.3f} rec={r:.3f} f1={f:.3f} auc={auc(test,w,b):.3f}  |w|={max(abs(v) for v in w):5.2f}")
print()

print("=== 2. чувствительность к числу эпох (lr=2.0) ===")
for ep in [1, 5, 15, 40, 100, 200, 350, 500]:
    w, b, div, _ = fit(train, 2.0, ep, 0.0, 1.0)
    cm = confusion(test, w, b, 0.5)
    p, r, f, a = metrics(cm)
    print(f"  epochs={ep:4d}  acc={a:.3f} prec={p:.3f} rec={r:.3f} f1={f:.3f} auc={auc(test,w,b):.3f}")
print()

print("=== 3. чувствительность к порогу (lr=2.0, epochs=300) ===")
w, b, _, _ = fit(train, 2.0, 300, 0.0, 1.0)
for thr in [0.05, 0.15, 0.3, 0.5, 0.7, 0.85, 0.95]:
    cm = confusion(test, w, b, thr)
    p, r, f, a = metrics(cm)
    print(f"  thr={thr:.2f}  TP={cm[0]:3d} FP={cm[1]:3d} TN={cm[2]:3d} FN={cm[3]:3d}  prec={p:.3f} rec={r:.3f} f1={f:.3f} cost(x10)={total_cost(cm,10):6.0f}")
print()

print("=== 4. чувствительность к L2 (lr=2.0, epochs=300) ===")
for lam in [0.0, 0.01, 0.05, 0.2, 0.5, 1.0, 2.0]:
    w, b, div, _ = fit(train, 2.0, 300, lam, 1.0)
    cm = confusion(test, w, b, 0.5)
    p, r, f, a = metrics(cm)
    print(f"  lam={lam:4.2f}  acc={a:.3f} f1={f:.3f} auc={auc(test,w,b):.3f}  w={[round(v,2) for v in w]}")
print()

print("=== 5. чувствительность к весу класса (lr=2.0, epochs=300, thr=0.5) ===")
for wp in [1, 2, 5, 10, 20, 50]:
    w, b, div, _ = fit(train, 2.0, 300, 0.0, float(wp))
    cm = confusion(test, w, b, 0.5)
    p, r, f, a = metrics(cm)
    bt, bc = best_threshold(test, w, b, float(wp))
    print(f"  w_pos={wp:3d}  TP={cm[0]:3d} FP={cm[1]:3d} FN={cm[3]:3d}  prec={p:.3f} rec={r:.3f}  опт.порог={bt:.2f}")
