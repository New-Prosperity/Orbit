package me.nebula.orbit.utils.customcontent.pack

import io.minio.MinioClient
import io.minio.PutObjectArgs
import me.nebula.ether.utils.environment.environment
import me.nebula.ether.utils.http.httpClient
import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.resourcepack.ResourcePackData
import me.nebula.gravity.resourcepack.ResourcePackStore
import me.nebula.orbit.utils.customcontent.CustomContentRegistry
import java.io.ByteArrayInputStream
import kotlin.time.Duration.Companion.seconds

object PackUploader {

    private val log = logger("PackUploader")

    private val env = environment {
        optional("R2_ENDPOINT")
        optional("R2_ACCESS_KEY")
        optional("R2_SECRET_KEY")
        optional("R2_BUCKET")
        optional("R2_PACK_KEY")
        optional("R2_PUBLIC_URL")
        optional("CF_ZONE_ID")
        optional("CF_API_TOKEN")
    }

    private val r2Endpoint = env.all["R2_ENDPOINT"]?.ifEmpty { null }
    private val r2AccessKey = env.all["R2_ACCESS_KEY"]?.ifEmpty { null }
    private val r2SecretKey = env.all["R2_SECRET_KEY"]?.ifEmpty { null }
    private val r2Bucket = env.all["R2_BUCKET"]?.ifEmpty { null } ?: "cdn"
    private val r2PackKey = env.all["R2_PACK_KEY"]?.ifEmpty { null } ?: "pack.zip"
    private val r2PublicUrl = env.all["R2_PUBLIC_URL"]?.ifEmpty { null }
    private val cfZoneId = env.all["CF_ZONE_ID"]?.ifEmpty { null }
    private val cfApiToken = env.all["CF_API_TOKEN"]?.ifEmpty { null }

    val isConfigured: Boolean get() = r2Endpoint != null && r2AccessKey != null && r2SecretKey != null && r2PublicUrl != null

    private val minio by lazy {
        MinioClient.builder()
            .endpoint(r2Endpoint!!)
            .credentials(r2AccessKey!!, r2SecretKey!!)
            .build()
    }

    data class UploadResult(
        val sizeKb: Int,
        val sha1: String,
        val url: String,
        val cachePurged: Boolean,
    )

    fun upload(packId: String = "default"): UploadResult {
        check(isConfigured) { "R2 not configured — set R2_ENDPOINT, R2_ACCESS_KEY, R2_SECRET_KEY, R2_PUBLIC_URL" }

        val result = CustomContentRegistry.mergePack(forceRegenerate = true)
        val bytes = result.packBytes
        val sha1 = result.sha1

        log.info { "Uploading pack to R2: ${bytes.size / 1024}KB, SHA-1=$sha1" }
        minio.putObject(
            PutObjectArgs.builder()
                .bucket(r2Bucket)
                .`object`(r2PackKey)
                .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                .contentType("application/zip")
                .build()
        )
        log.info { "Pack uploaded to R2: $r2Bucket/$r2PackKey" }

        val publicUrl = "${r2PublicUrl!!.trimEnd('/')}/$r2PackKey"

        val existing = ResourcePackStore.load(packId)
        val updated = existing?.copy(url = publicUrl, hash = sha1)
            ?: ResourcePackData(id = packId, url = publicUrl, hash = sha1, servers = listOf("orbit-"), exactMatch = false)
        ResourcePackStore.save(packId, updated)
        log.info { "ResourcePackStore updated: id=$packId, url=$publicUrl, sha1=${sha1.take(8)}..." }

        val cachePurged = purgeCloudflareCache(publicUrl)

        return UploadResult(
            sizeKb = bytes.size / 1024,
            sha1 = sha1,
            url = publicUrl,
            cachePurged = cachePurged,
        )
    }

    private fun purgeCloudflareCache(url: String): Boolean {
        if (cfZoneId == null || cfApiToken == null) {
            log.info { "Cloudflare cache purge skipped — CF_ZONE_ID or CF_API_TOKEN not set" }
            return false
        }

        val cf = httpClient {
            baseUrl("https://api.cloudflare.com/client/v4")
            defaultBearerAuth(cfApiToken)
            defaultHeader("Content-Type", "application/json")
            defaultTimeout(10.seconds)
        }

        val response = cf.post("/zones/$cfZoneId/purge_cache") {
            body("{\"files\":[\"$url\"]}".toByteArray(), "application/json")
        }

        return if (response.isSuccess) {
            log.info { "Cloudflare cache purged for $url" }
            true
        } else {
            log.warn { "Cloudflare cache purge failed: HTTP ${response.status}" }
            false
        }
    }
}
