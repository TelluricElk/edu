package com.eduappml.game

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.eduappml.data.models.UserProgress
import com.eduappml.managers.SyncManager
import com.eduappml.ui.menu.EdgeSpec
import com.eduappml.ui.menu.defaultEdges
import com.eduappml.ui.menu.defaultNodes
import com.eduappml.ui.third.thirdEdges
import com.eduappml.ui.third.thirdNodes

/**
 * Прогресс прохождения карты пузырей.
 *
 * Обычный режим («не god») — это прохождение локаций: изначально открыт ровно
 * один пузырь в каждом разделе (lr — классика, fc — нейросети). Когда тест на
 * экране «Решение задачи» решён на 4 из 4, соседи пройденного узла по рёбрам
 * графа становятся доступны. Так волна открытий расходится по графу, пока не
 * будут открыты все пузыри раздела (достижимость всех узлов из стартового
 * гарантирована схемой рёбер в NodeModels.kt и ThirdScreen.kt).
 *
 * Состояние хранится и в SharedPreferences (для перезапуска приложения), и в
 * Compose-состоянии [State] — чтобы карта пузырей перерисовывалась сразу после
 * возврата с экрана теста, без пересоздания Activity.
 */
object GameManager {
    private const val PREFS_NAME = "game_prefs"
    private const val KEY_MODE = "game_mode"
    private const val KEY_UNLOCKED_CLASSIC = "unlocked_classic"
    private const val KEY_UNLOCKED_NEURAL = "unlocked_neural"
    private const val TAG = "GameManager"

    const val SECTION_CLASSIC = "classic"
    const val SECTION_NEURAL = "neural"

    /** Стартовые пузыри разделов — всегда открыты. */
    const val START_CLASSIC = "lr"
    const val START_NEURAL = "fc"

    private lateinit var prefs: SharedPreferences

    // Compose-состояние: карта пузырей подписывается на него и обновляется
    // сразу после разблокировки, без recreate() у Activity.
    private val classicState = mutableStateOf<Set<String>>(setOf(START_CLASSIC))
    private val neuralState = mutableStateOf<Set<String>>(setOf(START_NEURAL))
    private val godState = mutableStateOf(true)

    // Справочники узлов графов — по ним определяем раздел узла и его подпись.
    private val classicIds: Map<String, String> by lazy {
        defaultNodes().associate { it.id.lowercase() to it.label }
    }
    private val neuralIds: Map<String, String> by lazy {
        thirdNodes().associate { it.id.lowercase() to it.label }
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        godState.value = prefs.getBoolean(KEY_MODE, true)
        classicState.value = readUnlocked(SECTION_CLASSIC)
        neuralState.value = readUnlocked(SECTION_NEURAL)
    }

    private fun isInitialized(): Boolean = this::prefs.isInitialized

    // ---------- Режим ----------

    fun isGodMode(): Boolean {
        if (!isInitialized()) return godState.value
        return prefs.getBoolean(KEY_MODE, true)
    }

    /** Реактивная версия [isGodMode] для Compose. */
    fun godModeState(): State<Boolean> = godState

    fun toggleMode() {
        val next = !isGodMode()
        prefs.edit().putBoolean(KEY_MODE, next).commit()
        godState.value = next
    }

    // ---------- Раздел узла ----------

    /** По id узла определяет раздел: id классики и нейросетей не пересекаются. */
    fun sectionOf(nodeId: String): String? {
        val id = nodeId.lowercase()
        return when {
            classicIds.containsKey(id) -> SECTION_CLASSIC
            neuralIds.containsKey(id) -> SECTION_NEURAL
            else -> null
        }
    }

    fun labelOf(nodeId: String): String {
        val id = nodeId.lowercase()
        return classicIds[id] ?: neuralIds[id] ?: nodeId
    }

    private fun edgesOf(section: String): List<EdgeSpec> = when (section) {
        SECTION_CLASSIC -> defaultEdges()
        SECTION_NEURAL -> thirdEdges()
        else -> emptyList()
    }

    private fun startNodeOf(section: String): String = when (section) {
        SECTION_NEURAL -> START_NEURAL
        else -> START_CLASSIC
    }

    private fun knownIdsOf(section: String): Set<String> = when (section) {
        SECTION_NEURAL -> neuralIds.keys
        else -> classicIds.keys
    }

    // ---------- Чтение / запись ----------

    private fun keyOf(section: String): String? = when (section) {
        SECTION_CLASSIC -> KEY_UNLOCKED_CLASSIC
        SECTION_NEURAL -> KEY_UNLOCKED_NEURAL
        else -> null
    }

    /**
     * Нормализация набора: приводим к нижнему регистру (в старых сборках
     * нейросетевой раздел стартовал с "FC", а узел в графе — "fc", из-за чего
     * стартовый пузырь не открывался), отбрасываем неизвестные id и всегда
     * добавляем стартовый узел раздела.
     */
    private fun normalize(section: String, raw: Collection<String>): Set<String> {
        val known = knownIdsOf(section)
        val result = raw.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && known.contains(it) }
            .toMutableSet()
        result.add(startNodeOf(section))
        return result
    }

    private fun readUnlocked(section: String): Set<String> {
        val key = keyOf(section) ?: return emptySet()
        if (!isInitialized()) return setOf(startNodeOf(section))
        val str = prefs.getString(key, null)
        val raw = if (str.isNullOrEmpty()) emptyList() else str.split(",")
        return normalize(section, raw)
    }

    fun getUnlockedNodes(screen: String): Set<String> = when (screen) {
        SECTION_CLASSIC -> classicState.value
        SECTION_NEURAL -> neuralState.value
        else -> emptySet()
    }

    /** Реактивный набор открытых узлов раздела — для BubbleGraph. */
    fun unlockedState(section: String): State<Set<String>> = when (section) {
        SECTION_NEURAL -> neuralState
        else -> classicState
    }

    fun isUnlocked(nodeId: String): Boolean {
        val section = sectionOf(nodeId) ?: return false
        return getUnlockedNodes(section).contains(nodeId.lowercase())
    }

    /** Все ли пузыри раздела уже открыты. */
    fun isSectionComplete(section: String): Boolean =
        getUnlockedNodes(section).containsAll(knownIdsOf(section))

    private fun saveUnlockedNodes(section: String, nodes: Set<String>) {
        val key = keyOf(section) ?: return
        val normalized = normalize(section, nodes)
        if (isInitialized()) {
            prefs.edit().putString(key, normalized.joinToString(",")).commit()
        }
        when (section) {
            SECTION_CLASSIC -> classicState.value = normalized
            SECTION_NEURAL -> neuralState.value = normalized
        }
    }

    fun updateFromProgress(progress: UserProgress) {
        saveUnlockedNodes(SECTION_CLASSIC, progress.unlockedClassic.toSet())
        saveUnlockedNodes(SECTION_NEURAL, progress.unlockedNeural.toSet())
    }

    // ---------- Разблокировка ----------

    /**
     * Открывает соседей узла [nodeId] по рёбрам его раздела.
     * Раздел и список рёбер определяются автоматически по id узла.
     *
     * @return подписи пузырей, открывшихся именно этим вызовом (пусто, если
     *         все соседи уже были открыты).
     */
    fun unlockNeighborsFor(nodeId: String, context: Context): List<String> {
        if (!isInitialized()) init(context)

        val id = nodeId.lowercase()
        val section = sectionOf(id)
        if (section == null) {
            Log.w(TAG, "unlockNeighborsFor: неизвестный узел '$nodeId'")
            return emptyList()
        }

        val current = getUnlockedNodes(section).toMutableSet()
        // Сам пройденный узел тоже фиксируем как открытый — на случай, если
        // прогресс пришёл с сервера в урезанном виде.
        val selfAdded = current.add(id)

        val neighbors = edgesOf(section)
            .filter { it.fromId.lowercase() == id || it.toId.lowercase() == id }
            .flatMap { listOf(it.fromId.lowercase(), it.toId.lowercase()) }
            .filter { it != id }
            .distinct()

        val added = neighbors.filter { current.add(it) }

        if (added.isNotEmpty() || selfAdded) {
            saveUnlockedNodes(section, current)
            Log.d(TAG, "unlockNeighborsFor($id): открыто ${added.size} узлов: $added")
            SyncManager.syncProgress(
                context,
                getUnlockedNodes(SECTION_CLASSIC),
                getUnlockedNodes(SECTION_NEURAL)
            )
        } else {
            Log.d(TAG, "unlockNeighborsFor($id): новых узлов нет")
        }

        return added.map { labelOf(it) }
    }

    /** Старая сигнатура — оставлена для совместимости с внешними вызовами. */
    fun unlockNeighbors(screen: String, nodeId: String, edges: List<EdgeSpec>, context: Context): Int =
        unlockNeighborsFor(nodeId, context).size

    // ---------- Инициализация и сброс ----------

    fun initDefaultUnlocked(context: Context) {
        if (!isInitialized()) init(context)
        // saveUnlockedNodes сам нормализует набор и добавит стартовый узел —
        // это чинит и старые установки, где в neural лежал "FC" вместо "fc".
        saveUnlockedNodes(SECTION_CLASSIC, readUnlocked(SECTION_CLASSIC))
        saveUnlockedNodes(SECTION_NEURAL, readUnlocked(SECTION_NEURAL))
    }

    fun resetProgress(context: Context) {
        if (!isInitialized()) init(context)
        saveUnlockedNodes(SECTION_CLASSIC, setOf(START_CLASSIC))
        saveUnlockedNodes(SECTION_NEURAL, setOf(START_NEURAL))
        SyncManager.syncProgress(
            context,
            getUnlockedNodes(SECTION_CLASSIC),
            getUnlockedNodes(SECTION_NEURAL)
        )
    }
}
