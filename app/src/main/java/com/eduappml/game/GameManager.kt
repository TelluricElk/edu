package com.eduappml.game

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.eduappml.data.models.UserProgress
import com.eduappml.managers.SyncManager
import com.eduappml.ui.menu.EdgeSpec

object GameManager {
    private const val PREFS_NAME = "game_prefs"
    private const val KEY_MODE = "game_mode"
    private const val KEY_UNLOCKED_CLASSIC = "unlocked_classic"
    private const val KEY_UNLOCKED_NEURAL = "unlocked_neural"
    private const val TAG = "GameManager"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isGodMode(): Boolean {
        return prefs.getBoolean(KEY_MODE, true)
    }

    fun toggleMode() {
        val current = isGodMode()
        prefs.edit().putBoolean(KEY_MODE, !current).commit()
    }

    fun getUnlockedNodes(screen: String): Set<String> {
        val key = when (screen) {
            "classic" -> KEY_UNLOCKED_CLASSIC
            "neural" -> KEY_UNLOCKED_NEURAL
            else -> return emptySet()
        }
        val str = prefs.getString(key, null)
        return if (str.isNullOrEmpty()) emptySet() else str.split(",").toSet()
    }

    private fun saveUnlockedNodes(screen: String, nodes: Set<String>) {
        val key = when (screen) {
            "classic" -> KEY_UNLOCKED_CLASSIC
            "neural" -> KEY_UNLOCKED_NEURAL
            else -> return
        }
        prefs.edit().putString(key, nodes.joinToString(",")).commit()
    }

    fun updateFromProgress(progress: UserProgress) {
        saveUnlockedNodes("classic", progress.unlockedClassic.toSet())
        saveUnlockedNodes("neural", progress.unlockedNeural.toSet())
    }

    fun unlockNeighbors(screen: String, nodeId: String, edges: List<EdgeSpec>, context: Context): Int {
        Log.d(TAG, "unlockNeighbors called for screen=$screen, nodeId=$nodeId")

        val current = getUnlockedNodes(screen).toMutableSet()
        val neighbors = edges.filter { it.fromId == nodeId || it.toId == nodeId }
            .flatMap { listOf(it.fromId, it.toId) }
            .filter { it != nodeId }
        var addedCount = 0
        neighbors.forEach {
            if (current.add(it)) addedCount++
        }
        if (addedCount > 0) {
            saveUnlockedNodes(screen, current)
            val classic = getUnlockedNodes("classic")
            val neural = getUnlockedNodes("neural")
            Log.d(TAG, "unlockNeighbors: added $addedCount nodes, syncing...")
            SyncManager.syncProgress(context, classic, neural)
        } else {
            Log.d(TAG, "unlockNeighbors: no new nodes added")
        }
        return addedCount
    }

    fun initDefaultUnlocked(context: Context) {
        init(context)
        if (!prefs.contains(KEY_UNLOCKED_CLASSIC)) {
            saveUnlockedNodes("classic", setOf("lr"))
        }
        if (!prefs.contains(KEY_UNLOCKED_NEURAL)) {
            saveUnlockedNodes("neural", setOf("FC"))
        }
    }

    fun resetProgress(context: Context) {
        saveUnlockedNodes("classic", setOf("lr"))
        saveUnlockedNodes("neural", setOf("FC"))
        val classic = getUnlockedNodes("classic")
        val neural = getUnlockedNodes("neural")
        SyncManager.syncProgress(context, classic, neural)
    }
}