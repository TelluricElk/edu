package com.eduappml.data

import android.content.res.AssetManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

data class InfoContent(
    val general: String?,
    val math: String?,
    val impl: String?
)

object InfoRepository {

    private fun loadSection(
        assets: AssetManager,
        rawId: String,
        section: String,
        preferredLang: String? = "ru"
    ): String? {
        // Убираем lowercase – теперь регистр имеет значение
        val id = rawId.trim()

        val bases = listOf(
            "info/classic/$id",
            "info/$id"
        )

        val langs = buildList {
            if (preferredLang != null) add(preferredLang)
            add("ru")
            add("en")
            add(null)
        }.distinct()

        val candidatesBySection = langs.flatMap { lang ->
            if (lang == null) {
                bases.map { base -> "$base/$section.md" }
            } else {
                bases.map { base -> "$base/$section.$lang.md" }
            }
        }

        val legacyCandidates = langs.mapNotNull { lang ->
            when (lang) {
                null -> "info/classic/$id.md"
                else -> "info/classic/$id.$lang.md"
            }
        }

        val candidates = candidatesBySection + legacyCandidates

        for (path in candidates) {
            readAsset(assets, path)?.let { return it }
        }
        return null
    }

    private fun readAsset(assets: AssetManager, path: String): String? {
        return try {
            assets.open(path).use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun loadAll(assets: AssetManager, id: String, preferredLang: String? = "ru"): InfoContent {
        val general = loadSection(assets, id, "general", preferredLang)
        val math = loadSection(assets, id, "math", preferredLang)
        val impl = loadSection(assets, id, "impl", preferredLang)
        return InfoContent(general, math, impl)
    }
}