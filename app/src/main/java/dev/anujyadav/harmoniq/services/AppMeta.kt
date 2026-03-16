package dev.anujyadav.harmoniq.services

import dev.anujyadav.harmoniq.BuildConfig
import dev.anujyadav.harmoniq.utils.HttpClient
import dev.anujyadav.harmoniq.utils.Logger
import okhttp3.CacheControl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

@Suppress("ConstPropertyName")
object AppMeta {
    const val appName = "Harmoniq"
    const val appDescription = "Harmoniq - A clean, fast, and privacy-focused offline music player built for a modern listening experience."
    const val author = "Anuj Yadav"
    const val githubRepositoryOwner = "AnujYadav-Dev"
    const val githubRepositoryName = "Harmoniq"
    const val githubProfileUrl = "https://github.com/$githubRepositoryOwner"
    const val githubRepositoryUrl =
        "https://github.com/$githubRepositoryOwner/$githubRepositoryName"

    const val version = "v${BuildConfig.VERSION_NAME}"
    var latestVersion: String? = null
    const val githubLatestReleaseUrl = "$githubRepositoryUrl/releases/latest"
    const val githubIssuesUrl = "$githubRepositoryUrl/issues"
    const val discordUrl = ""
    const val redditUrl = ""
    const val contributingUrl = "$githubRepositoryUrl#contributing"

    const val packageName = "dev.anujyadav.harmoniq"
    const val izzyOnDroidUrl = ""
    const val fdroidUrl = ""
    const val playStoreUrl = ""

    fun isNightlyBuild() = version.contains("-nightly")

    fun fetchLatestVersion() = when {
        isNightlyBuild() -> fetchLatestNightlyVersion()
        else -> fetchLatestStableVersion()
    }

    fun fetchLatestStableVersion(): String? {
        try {
            val latestReleaseUrl =
                "https://api.github.com/repos/$githubRepositoryOwner/$githubRepositoryName/releases/latest"
            val req = Request.Builder()
                .url(latestReleaseUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            val res = HttpClient.newCall(req).execute()
            val content = res.body?.string() ?: ""
            val json = JSONObject(content)
            val tagName = json.getString("tag_name")
            val draft = json.getBoolean("draft")
            if (!draft) {
                latestVersion = tagName
            }
        } catch (err: Exception) {
            Logger.warn("AppMeta", "version check failed: $err")
        }
        return latestVersion
    }

    fun fetchLatestNightlyVersion(): String? {
        try {
            val latestReleaseUrl =
                "https://api.github.com/repos/$githubRepositoryOwner/$githubRepositoryName/releases"
            val req = Request.Builder()
                .url(latestReleaseUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            val res = HttpClient.newCall(req).execute()
            val content = res.body?.string() ?: ""
            val json = JSONArray(content)
            for (i in 0 until json.length()) {
                val x = json.getJSONObject(i)
                val tagName = x.getString("tag_name")
                val prerelease = x.getBoolean("prerelease")
                if (prerelease) {
                    latestVersion = tagName
                    break
                }
            }
        } catch (err: Exception) {
            Logger.warn("AppMeta", "nightly version check failed: $err")
        }
        return latestVersion
    }
}
