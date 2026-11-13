package it.avanzi.spese.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import it.avanzi.spese.domain.UpdateChannel
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val REPO = "Adrianss31/spese"
private const val API = "https://api.github.com/repos/$REPO"
private const val MAX_APK_SIZE = 150L * 1024 * 1024

@Serializable
data class ReleaseManifest(val versionCode: Long, val versionName: String, val sha256: String, val notes: String = "")

@Serializable
private data class GitHubAsset(val id: Long, val name: String, val size: Long, val browser_download_url: String)

@Serializable
private data class GitHubRelease(
    val tag_name: String, val draft: Boolean, val prerelease: Boolean, val assets: List<GitHubAsset>,
)

data class AvailableUpdate(
    val version: ReleaseManifest,
    val apkId: Long,
    val apkUrl: String,
    val size: Long,
)

class CredentialsRequired : IOException("GitHub authentication required")

@Singleton
class ReleaseApi @Inject constructor(@ApplicationContext private val context: Context) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(channel: UpdateChannel, token: String?): AvailableUpdate? = withContext(Dispatchers.IO) {
        val endpoint = if (channel == UpdateChannel.STABLE) "$API/releases/latest" else "$API/releases?per_page=30"
        val releases = request(endpoint, token).use { response ->
            if (response.code == 404 || response.code == 401 || response.code == 403) throw CredentialsRequired()
            if (!response.isSuccessful) throw IOException("GitHub HTTP ${response.code}")
            val body = requireNotNull(response.body).string()
            if (channel == UpdateChannel.STABLE) listOf(json.decodeFromString<GitHubRelease>(body))
            else json.decodeFromString<List<GitHubRelease>>(body)
        }

        val current = context.packageManager.getPackageInfo(context.packageName, 0).code()
        releases.asSequence().filter { !it.draft && (channel == UpdateChannel.BETA || !it.prerelease) }
            .mapNotNull { release ->
                val manifest = release.assets.singleOrNull { it.name == "version.json" } ?: return@mapNotNull null
                val apk = release.assets.singleOrNull { it.name == "spese.apk" } ?: return@mapNotNull null
                if (manifest.size !in 1..65536 || apk.size !in 1..MAX_APK_SIZE) return@mapNotNull null
                val metadata = asset(manifest.id, manifest.browser_download_url, token).use { response ->
                    if (!response.isSuccessful) throw IOException("Manifest HTTP ${response.code}")
                    val body = requireNotNull(response.body)
                    val bytes = body.source().peek()
                    if (bytes.request(65537)) throw IOException("Unexpected manifest size")
                    json.decodeFromString<ReleaseManifest>(bytes.readUtf8())
                }
                if (metadata.versionCode <= current || metadata.versionName.isBlank() ||
                    !metadata.sha256.matches(Regex("[0-9a-fA-F]{64}"))
                ) null else AvailableUpdate(metadata, apk.id, apk.browser_download_url, apk.size)
            }.maxByOrNull { it.version.versionCode }
    }

    suspend fun download(update: AvailableUpdate, token: String?, onProgress: (Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "spese-update.apk")
            val temporary = File.createTempFile("spese-download-", ".apk", context.cacheDir)
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                asset(update.apkId, update.apkUrl, token).use { response ->
                    if (!response.isSuccessful) throw IOException("APK HTTP ${response.code}")
                    val body = requireNotNull(response.body)
                    temporary.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var transferred = 0L
                            while (true) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                transferred += count
                                if (transferred > update.size) throw IOException("APK size does not match")
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                                onProgress(transferred)
                            }
                            if (transferred != update.size) throw IOException("APK size does not match")
                        }
                    }
                }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (!sha256.equals(update.version.sha256, ignoreCase = true)) throw IOException("APK checksum mismatch")
                val manager = context.packageManager
                val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
                val archive = manager.getPackageArchiveInfo(temporary.absolutePath, flags)
                    ?: throw IOException("Invalid APK")
                val installed = manager.getPackageInfo(context.packageName, flags)
                if (archive.packageName != context.packageName || archive.code() != update.version.versionCode ||
                    archive.signers() != installed.signers() || archive.signers().isEmpty()
                ) throw IOException("APK identity mismatch")
                if (!temporary.renameTo(file)) throw IOException("Unable to retain APK")
                file
            } finally {
                temporary.delete()
            }
        }

    private fun asset(id: Long, publicUrl: String, token: String?): Response {
        if (token != null) return request("$API/releases/assets/$id", token, binary = true)
        val url = publicUrl.toHttpUrl()
        if (!url.isHttps || url.host != "github.com" || !url.encodedPath.startsWith("/$REPO/releases/download/")) {
            throw IOException("Invalid release asset URL")
        }
        return request(publicUrl, null, binary = true)
    }

    private fun request(url: String, token: String?, binary: Boolean = false): Response {
        var current = url.toHttpUrl()
        repeat(4) {
            if (!current.isHttps || current.host !in allowedHosts) throw IOException("Untrusted download host")
            val builder = Request.Builder().url(current)
                .header("Accept", if (binary) "application/octet-stream" else "application/vnd.github+json")
            if (current.host == "api.github.com" && token != null) builder.header("Authorization", "Bearer $token")
            val response = client.newCall(builder.build()).execute()
            if (response.code !in setOf(301, 302, 303, 307, 308)) return response
            current = response.use {
                current.resolve(it.header("Location") ?: throw IOException("Missing redirect"))
                    ?: throw IOException("Invalid redirect")
            }
        }
        throw IOException("Too many redirects")
    }

    private companion object {
        val allowedHosts = setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }
}

@Suppress("DEPRECATION")
private fun PackageInfo.code(): Long = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

@Suppress("DEPRECATION")
private fun PackageInfo.signers(): Set<String> =
    (if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures)
        .orEmpty().map { it.toCharsString() }.toSet()
