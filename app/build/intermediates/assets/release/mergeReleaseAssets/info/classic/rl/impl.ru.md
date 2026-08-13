## Программная реализация Q-обучения

### Реализация на Python

```python
import random


class GridWorld:
    def __init__(self, size=8, walls=None, goal=(7, 7)):
        self.size = size
        self.walls = walls or set()
        self.goal = goal
        self.actions = [(0, 1), (0, -1), (1, 0), (-1, 0)]  # вверх, вниз, вправо, влево

    def step(self, pos, action_idx):
        dx, dy = self.actions[action_idx]
        nx, ny = pos[0] + dx, pos[1] + dy
        if not (0 <= nx < self.size and 0 <= ny < self.size) or (nx, ny) in self.walls:
            return pos, -1.0, False              # врезался в стену/границу — остаётся на месте
        if (nx, ny) == self.goal:
            return (nx, ny), 50.0, True
        return (nx, ny), -1.0, False


def q_learning(env, episodes, alpha, epsilon, gamma=0.9, max_steps=200, seed=1):
    rng = random.Random(seed)
    Q = {}  # (state) -> [Q_up, Q_down, Q_right, Q_left]

    def get_q(s):
        return Q.setdefault(s, [0.0, 0.0, 0.0, 0.0])

    steps_per_episode = []
    for _ in range(episodes):
        pos = (0, 0)
        for step_count in range(max_steps):
            q = get_q(pos)
            if rng.random() < epsilon:
                a = rng.randrange(4)
            else:
                a = q.index(max(q))                       # жадный выбор

            new_pos, reward, done = env.step(pos, a)
            q_new = get_q(new_pos)
            q[a] += alpha * (reward + gamma * max(q_new) - q[a])   # правило Q-обучения
            pos = new_pos
            if done:
                break
        steps_per_episode.append(step_count + 1)
    return Q, steps_per_episode
```

### Готовое решение: Gymnasium + stable-baselines3

```python
import gymnasium as gym
from stable_baselines3 import DQN

env = gym.make("FrozenLake-v1")
model = DQN("MlpPolicy", env, verbose=0)
model.learn(total_timesteps=10_000)
```

### А что реально считает интерактив в этом приложении

Тот же табличный Q-learning на Kotlin — та же формула обновления, тот же ε-жадный выбор действия:

```kotlin
fun trainEpisode(q: MutableMap<Pair<Int, Int>, FloatArray>, alpha: Float, epsilon: Float, gamma: Float, rnd: Random): Int {
    var pos = START
    var steps = 0
    repeat(MAX_STEPS) {
        steps++
        val qValues = q.getOrPut(pos) { FloatArray(4) }
        val action = if (rnd.nextFloat() < epsilon) {
            rnd.nextInt(4)
        } else {
            qValues.indices.maxByOrNull { qValues[it] }!!
        }
        val (newPos, reward, done) = envStep(pos, action)
        val qNew = q.getOrPut(newPos) { FloatArray(4) }
        qValues[action] += alpha * (reward + gamma * qNew.max() - qValues[action])
        pos = newPos
        if (done) return steps
    }
    return steps
}
```

Функция вызывается `episodes` раз подряд — ровно столько, сколько выбрано ползунком, и на каждом эпизоде реально обновляет Q-таблицу, а не просто рисует заранее просчитанную анимацию.

### Важная оговорка

Табличный Q-learning хранит отдельную оценку для каждой пары состояние-действие — это работает для маленьких дискретных пространств вроде лабиринта 8×8 (256 пар), но не масштабируется на большие или непрерывные пространства состояний (например, кадры видеоигры). Там на смену табличному Q приходит **Deep Q-Network (DQN)** — та же идея, но Q-значения предсказывает нейросеть, а не таблица.
