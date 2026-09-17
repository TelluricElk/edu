"""
Калибровка интерактива knn (ИБ): поиск похожих инцидентов в базе SOC.

Новый алерт классифицируется по k ближайшим историческим инцидентам.
Три класса: ложное срабатывание, подозрительно, подтверждённый инцидент.

Два признака СПЕЦИАЛЬНО в разных единицах:
  events   — число событий в алерте, 0..500
  offHours — доля активности вне рабочего времени, 0..1

Главный сюжет темы — МАСШТАБ ПРИЗНАКОВ. Без нормализации евклидово
расстояние определяется одним лишь числом событий, второй признак не
влияет вообще. Это проверяется отдельным переключателем.
"""
import math

class Lcg:
    def __init__(self, seed): self.s = seed & 0xFFFFFFFFFFFF
    def nf(self):
        self.s = (self.s * 0x5DEECE66D + 0xB) & 0xFFFFFFFFFFFF
        return (self.s >> 24) / float(1 << 24)
    def gauss(self, mu, sd):
        return mu + sd * (sum(self.nf() for _ in range(6)) - 3.0) / math.sqrt(0.5)

EVENTS_MAX = 500.0
# (mu_events, sd_events, mu_off, sd_off)
CLASSES = [
    (70.0,  45.0, 0.22, 0.16),   # 0 ложное срабатывание
    (210.0, 70.0, 0.50, 0.18),   # 1 подозрительно
    (330.0, 80.0, 0.74, 0.16),   # 2 подтверждённый инцидент
]

def clamp(v, lo, hi): return lo if v < lo else (hi if v > hi else v)

def gen(seed, n, noise):
    """noise — доля алертов с испорченной разметкой (аналитик ошибся)."""
    rnd = Lcg(seed)
    data = []
    for _ in range(n):
        c = int(rnd.nf() * 3)
        if c > 2: c = 2
        me, se, mo, so = CLASSES[c]
        ev = clamp(rnd.gauss(me, se), 0.0, EVENTS_MAX)
        oh = clamp(rnd.gauss(mo, so), 0.0, 1.0)
        lab = c
        if rnd.nf() < noise:
            lab = int(rnd.nf() * 3)
            if lab > 2: lab = 2
        data.append(((ev, oh), lab))
    return data

def scales(train, normalize):
    if not normalize:
        return (1.0, 1.0)
    evs = [p[0][0] for p in train]
    ohs = [p[0][1] for p in train]
    def sd(vals):
        m = sum(vals)/len(vals)
        return max(math.sqrt(sum((v-m)**2 for v in vals)/len(vals)), 1e-9)
    return (sd(evs), sd(ohs))

def dist(a, b, sc, metric):
    d0 = (a[0]-b[0])/sc[0]
    d1 = (a[1]-b[1])/sc[1]
    if metric == 'euclid':   return math.sqrt(d0*d0 + d1*d1)
    if metric == 'manhattan': return abs(d0) + abs(d1)
    return max(abs(d0), abs(d1))          # chebyshev

def classify(x, train, k, metric, weighting, sc):
    ds = sorted(((dist(x, p[0], sc, metric), p[1]) for p in train))[:k]
    votes = [0.0, 0.0, 0.0]
    for d, lab in ds:
        w = 1.0 if weighting == 'uniform' else 1.0/(d + 1e-6)
        votes[lab] += w
    return votes.index(max(votes))

def accuracy(test, train, k, metric, weighting, normalize):
    sc = scales(train, normalize)
    ok = sum(1 for x, y in test if classify(x, train, k, metric, weighting, sc) == y)
    return ok / len(test)

train = gen(42, 240, 0.0)
test = gen(777, 150, 0.0)

print("=== A. НОРМАЛИЗАЦИЯ — главный сюжет (k=7, евклид, равномерное) ===")
for nz in (False, True):
    a = accuracy(test, train, 7, 'euclid', 'uniform', nz)
    print(f"  нормализация {'ВКЛ ' if nz else 'ВЫКЛ'}: точность={a:.4f}")
print()

print("=== B. k при нормализации ВКЛ / ВЫКЛ ===")
print("   k    вкл     выкл")
for k in [1, 3, 5, 7, 11, 17, 25, 41, 71]:
    print(f"  {k:3d}  {accuracy(test,train,k,'euclid','uniform',True):.4f}  "
          f"{accuracy(test,train,k,'euclid','uniform',False):.4f}")
print()

print("=== C. метрика (k=7, норм. вкл) ===")
for m in ['euclid','manhattan','chebyshev']:
    print(f"  {m:10s} {accuracy(test,train,7,m,'uniform',True):.4f}")
print()

print("=== D. взвешивание x шум разметки (k=7, норм. вкл) ===")
print("  шум   равномерное  по расстоянию")
for noise in [0.0, 0.1, 0.2, 0.3]:
    tr = gen(42, 240, noise)
    print(f"  {noise:4.2f}   {accuracy(test,tr,7,'euclid','uniform',True):.4f}      "
          f"{accuracy(test,tr,7,'euclid','distance',True):.4f}")
print()

print("=== E. k при шумной разметке 20% (норм. вкл) — почему k=1 плох ===")
tr = gen(42, 240, 0.2)
for k in [1, 3, 5, 9, 15, 25, 41]:
    print(f"  k={k:3d}  точность={accuracy(test,tr,k,'euclid','uniform',True):.4f}")
print()

print("=== F. размер обучающей базы (k=7, норм. вкл, без шума) ===")
for n in [10, 25, 60, 120, 240, 400]:
    tr2 = gen(42, n, 0.0)
    print(f"  n={n:4d}  точность={accuracy(test,tr2,7,'euclid','uniform',True):.4f}")
print()

print("=== G. эталон: k=7, евклид, равномерное, норм. вкл, без шума ===")
print(f"  точность = {accuracy(test,train,7,'euclid','uniform',True):.4f}")
sc = scales(train, True)
print(f"  масштабы: события sd={sc[0]:.3f}, доля нерабочего времени sd={sc[1]:.4f}")
print(f"  отношение масштабов = {sc[0]/sc[1]:.1f} — во столько раз события "
      f"«громче» второго признака без нормализации")
