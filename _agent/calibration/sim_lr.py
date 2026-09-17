"""
Калибровка интерактива lr (ИБ): прогноз пиковой полосы DDoS-атаки
по числу узлов ботнета. Ключевой урок темы — нормализация признака:
на «сырых» тысячах узлов та же скорость обучения разносит модель,
на нормализованных — работает. Плюс выбросы-амплификации, на которых
видно, что MSE не робастна.
"""
import math

class Lcg:
    def __init__(self, seed): self.s = seed & 0xFFFFFFFFFFFF
    def nf(self):
        self.s = (self.s * 0x5DEECE66D + 0xB) & 0xFFFFFFFFFFFF
        return (self.s >> 24) / float(1 << 24)
    def gauss(self, mu, sd):
        return mu + sd * (sum(self.nf() for _ in range(6)) - 3.0) / math.sqrt(0.5)

# истинная зависимость: 0.42 Гбит/с на тысячу узлов + базовые 3.5
TRUE_K, TRUE_B = 0.42, 3.5
NODES_MAX = 120.0   # тысяч узлов

def gen(seed, n, amp_rate):
    """amp_rate — доля атак с амплификацией (DNS/NTP): мало узлов, огромная полоса."""
    rnd = Lcg(seed)
    data = []
    for _ in range(n):
        nodes = 4.0 + rnd.nf() * (NODES_MAX - 4.0)
        bw = TRUE_K * nodes + TRUE_B + rnd.gauss(0.0, 3.2)
        if rnd.nf() < amp_rate:
            bw += 25.0 + rnd.nf() * 45.0      # коэффициент усиления
        data.append((nodes, max(0.1, bw)))
    return data

def fit(data, lr, epochs, normalize, lam):
    xs = [d[0] for d in data]
    mu = sum(xs) / len(xs)
    sd = math.sqrt(sum((x - mu) ** 2 for x in xs) / len(xs))
    if not normalize:
        mu, sd = 0.0, 1.0
    k, b = 0.0, 0.0
    hist = []
    for e in range(epochs):
        gk = gb = 0.0
        loss = 0.0
        for x, y in data:
            xn = (x - mu) / sd
            pred = k * xn + b
            err = pred - y
            gk += err * xn
            gb += err
            loss += err * err
        n = len(data)
        k -= lr * (gk / n + lam * k)
        b -= lr * (gb / n)
        hist.append(loss / n)
        if math.isnan(k) or abs(k) > 1e9:
            return k, b, mu, sd, True, hist
    return k, b, mu, sd, False, hist

def metrics(data, k, b, mu, sd):
    n = len(data)
    ybar = sum(y for _, y in data) / n
    sse = sae = sst = 0.0
    for x, y in data:
        p = k * ((x - mu) / sd) + b
        sse += (p - y) ** 2
        sae += abs(p - y)
        sst += (y - ybar) ** 2
    return sse / n, sae / n, (1 - sse / sst if sst > 0 else 0.0)

print("=== A. нормализация ВЫКЛ: где ломается ===")
d = gen(42, 160, 0.0)
for lr in [0.00001, 0.0001, 0.0005, 0.001, 0.005, 0.02]:
    k, b, mu, sd, div, h = fit(d, lr, 300, False, 0.0)
    if div:
        print(f"  lr={lr:<9} РАСХОДИТСЯ")
    else:
        mse, mae, r2 = metrics(d, k, b, mu, sd)
        print(f"  lr={lr:<9} k={k:7.3f} b={b:7.3f} MSE={mse:8.2f} R2={r2:6.3f}")
print()

print("=== B. нормализация ВКЛ: тот же диапазон скоростей ===")
for lr in [0.00001, 0.0001, 0.001, 0.01, 0.1, 0.5, 1.0, 1.8]:
    k, b, mu, sd, div, h = fit(d, lr, 300, True, 0.0)
    if div:
        print(f"  lr={lr:<9} РАСХОДИТСЯ")
    else:
        mse, mae, r2 = metrics(d, k, b, mu, sd)
        kreal = k / sd
        print(f"  lr={lr:<9} наклон(реальный)={kreal:6.3f} MSE={mse:7.2f} R2={r2:6.3f}")
print()

print("=== C. эпохи (норм. вкл, lr=0.3) ===")
for ep in [1, 5, 20, 60, 150, 300, 600]:
    k, b, mu, sd, div, h = fit(d, 0.3, ep, True, 0.0)
    mse, mae, r2 = metrics(d, k, b, mu, sd)
    print(f"  epochs={ep:4d}  наклон={k/sd:6.3f} b={b:6.2f} MSE={mse:7.2f} R2={r2:6.3f}")
print()

print("=== D. выбросы-амплификации (норм. вкл, lr=0.3, 300 эпох) ===")
print("   доля   наклон(истина 0.420)   MSE      MAE     R2")
for rate in [0.0, 0.03, 0.06, 0.12, 0.20, 0.30]:
    dd = gen(42, 160, rate)
    k, b, mu, sd, div, h = fit(dd, 0.3, 300, True, 0.0)
    mse, mae, r2 = metrics(dd, k, b, mu, sd)
    print(f"  {rate:5.2f}   {k/sd:8.3f}            {mse:8.2f} {mae:7.2f} {r2:6.3f}")
print()

print("=== E. L2 (норм. вкл, lr=0.3, 300 эпох, без выбросов) ===")
for lam in [0.0, 0.05, 0.2, 0.5, 1.0, 2.0]:
    k, b, mu, sd, div, h = fit(d, 0.3, 300, True, lam)
    mse, mae, r2 = metrics(d, k, b, mu, sd)
    print(f"  lam={lam:4.2f}  наклон={k/sd:6.3f} MSE={mse:7.2f} R2={r2:6.3f}")
print()

print("=== F. эталон: норм. вкл, lr=0.3, 300 эпох, выбросов 6% ===")
dd = gen(42, 160, 0.06)
k, b, mu, sd, div, h = fit(dd, 0.3, 300, True, 0.0)
mse, mae, r2 = metrics(dd, k, b, mu, sd)
print(f"  наклон = {k/sd:.4f} Гбит/с на тысячу узлов (истина {TRUE_K})")
print(f"  свободный член = {b - k*mu/sd:.4f} (истина {TRUE_B})")
print(f"  MSE = {mse:.3f}  MAE = {mae:.3f}  R2 = {r2:.4f}")
print(f"  прогноз для 50 тыс. узлов: {k*((50-mu)/sd)+b:.2f} Гбит/с")
