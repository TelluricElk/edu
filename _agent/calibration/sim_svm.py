"""
Калибровка интерактива svm (ИБ): отделение аномального сетевого трафика
от легитимного. Линейное и RBF-ядро, мягкий зазор, обучение Pegasos.

Два признака:
  x1 — средний размер пакета в сессии, нормализован в [0,1]
  x2 — доля SYN-пакетов без ответа, [0,1]

Главный сюжет — параметр C: цена нарушения зазора. Малое C даёт широкий
зазор и устойчивость к выбросам, большое — узкий зазор и подгонку под них.
Выбросы моделируют ночной бэкап: легитимные сессии с профилем, похожим
на эксфильтрацию.
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

# легитимный трафик: маленькие пакеты, мало «висящих» SYN
LEGIT = (0.35, 0.13, 0.18, 0.11)
# аномальный: крупные пакеты (эксфильтрация) и/или много SYN без ответа
ANOM  = (0.66, 0.14, 0.62, 0.15)

def gen(seed, n, outlier_rate):
    """outlier_rate — доля легитимных сессий, выглядящих как аномалия
    (ночной бэкап: большой объём, но SYN в порядке)."""
    rnd = Lcg(seed)
    data = []
    for _ in range(n):
        is_anom = 1 if rnd.nf() < 0.5 else -1
        m1, s1, m2, s2 = ANOM if is_anom == 1 else LEGIT
        x1 = clamp01(rnd.gauss(m1, s1))
        x2 = clamp01(rnd.gauss(m2, s2))
        if is_anom == -1 and rnd.nf() < outlier_rate:
            x1 = clamp01(rnd.gauss(0.80, 0.08))   # крупные пакеты
            x2 = clamp01(rnd.gauss(0.70, 0.10))   # и похоже на скан
        data.append(((x1, x2), is_anom))
    return data

def kernel(a, b, kind, gamma):
    # Слагаемое +1 — это augmentation постоянным признаком, дающая модели
    # свободный член. Без него разделяющая гиперплоскость обязана проходить
    # через начало координат, а оба наших класса лежат в положительном
    # квадранте — разделить их было бы невозможно. Сумма двух ядер — снова
    # корректное ядро, так что приём законный.
    if kind == 'linear':
        return a[0]*b[0] + a[1]*b[1] + 1.0
    dx, dy = a[0]-b[0], a[1]-b[1]
    return math.exp(-gamma * (dx*dx + dy*dy)) + 1.0

def train(data, C, kind, gamma, iterations):
    """Ядровой Pegasos. lam = 1/(C*n): большое C -> малая регуляризация."""
    n = len(data)
    lam = 1.0 / max(C * n, 1e-9)
    alpha = [0.0]*n
    rnd = Lcg(12345)
    for t in range(1, iterations+1):
        i = int(rnd.nf() * n)
        if i >= n: i = n-1
        xi, yi = data[i]
        s = 0.0
        for j in range(n):
            if alpha[j] != 0.0:
                s += alpha[j] * data[j][1] * kernel(data[j][0], xi, kind, gamma)
        if yi * s / (lam * t) < 1.0:
            alpha[i] += 1.0
    return alpha, lam, iterations

def decide(x, data, alpha, lam, T, kind, gamma):
    s = 0.0
    for j in range(len(data)):
        if alpha[j] != 0.0:
            s += alpha[j] * data[j][1] * kernel(data[j][0], x, kind, gamma)
    return s / (lam * T)

def weights(data, alpha, lam, T):
    """Для линейного ядра — явные веса и свободный член (он берётся из той
    самой константной компоненты ядра)."""
    w0 = w1 = b = 0.0
    for j in range(len(data)):
        if alpha[j] != 0.0:
            w0 += alpha[j]*data[j][1]*data[j][0][0]
            w1 += alpha[j]*data[j][1]*data[j][0][1]
            b  += alpha[j]*data[j][1]
    return w0/(lam*T), w1/(lam*T), b/(lam*T)

def accuracy(test, data, alpha, lam, T, kind, gamma):
    ok = 0
    for x, y in test:
        if (1 if decide(x, data, alpha, lam, T, kind, gamma) >= 0 else -1) == y:
            ok += 1
    return ok/len(test)

def sv_count(alpha): return sum(1 for a in alpha if a > 0)

def margin_width(w0, w1):
    nrm = math.sqrt(w0*w0 + w1*w1)
    return 2.0/nrm if nrm > 1e-9 else 0.0

N = 120
ITER = 3000

print("=== A. параметр C (линейное ядро, без выбросов) ===")
tr = gen(42, N, 0.0); te = gen(777, 80, 0.0)
print("     C    точность  опорных  ширина зазора")
for C in [0.01, 0.05, 0.2, 1.0, 5.0, 25.0]:
    a, lam, T = train(tr, C, 'linear', 0.0, ITER)
    w0, w1, bb = weights(tr, a, lam, T)
    print(f"  {C:6.2f}   {accuracy(te,tr,a,lam,T,'linear',0.0):.4f}    {sv_count(a):3d}     {margin_width(w0,w1):.4f}")
print()

print("=== B. параметр C при 25% выбросов — главный сюжет ===")
tro = gen(42, N, 0.25); teo = gen(777, 80, 0.25)
print("     C    точность  опорных  ширина зазора")
for C in [0.01, 0.05, 0.2, 1.0, 5.0, 25.0]:
    a, lam, T = train(tro, C, 'linear', 0.0, ITER)
    w0, w1, bb = weights(tro, a, lam, T)
    print(f"  {C:6.2f}   {accuracy(teo,tro,a,lam,T,'linear',0.0):.4f}    {sv_count(a):3d}     {margin_width(w0,w1):.4f}")
print()

print("=== C. ядро и gamma (C=1, выбросы 25%) ===")
for kind, g in [('linear',0.0), ('rbf',0.5), ('rbf',2.0), ('rbf',8.0), ('rbf',30.0)]:
    a, lam, T = train(tro, 1.0, kind, g, ITER)
    name = kind if kind=='linear' else f"rbf g={g}"
    print(f"  {name:12s} точность={accuracy(teo,tro,a,lam,T,kind,g):.4f}  опорных={sv_count(a):3d}")
print()

print("=== D. число итераций (C=1, линейное, выбросы 25%) ===")
for it in [50, 200, 600, 1500, 3000, 6000]:
    a, lam, T = train(tro, 1.0, 'linear', 0.0, it)
    print(f"  итераций={it:5d}  точность={accuracy(teo,tro,a,lam,T,'linear',0.0):.4f}  опорных={sv_count(a):3d}")
print()

print("=== E. доля выбросов (C=1, линейное) ===")
for r in [0.0, 0.1, 0.25, 0.4]:
    d = gen(42, N, r); t2 = gen(777, 80, r)
    a, lam, T = train(d, 1.0, 'linear', 0.0, ITER)
    w0, w1, bb = weights(d, a, lam, T)
    print(f"  выбросов {r:4.2f}: точность={accuracy(t2,d,a,lam,T,'linear',0.0):.4f} "
          f"опорных={sv_count(a):3d} зазор={margin_width(w0,w1):.4f}")
print()

print("=== F. эталон: C=1, линейное, 3000 итераций, выбросов 10% ===")
d = gen(42, N, 0.10); t2 = gen(777, 80, 0.10)
a, lam, T = train(d, 1.0, 'linear', 0.0, ITER)
w0, w1, bb = weights(d, a, lam, T)
print(f"  точность = {accuracy(t2,d,a,lam,T,'linear',0.0):.4f}")
print(f"  опорных векторов = {sv_count(a)} из {N}")
print(f"  w = ({w0:.4f}, {w1:.4f}), ширина зазора = {margin_width(w0,w1):.4f}")
