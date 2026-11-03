package dev.anujyadav.harmoniq.services.globalsearch.download

import dev.anujyadav.harmoniq.services.globalsearch.ServerConfig
import dev.anujyadav.harmoniq.services.globalsearch.api.ErrorCodes
import dev.anujyadav.harmoniq.services.globalsearch.api.JobStatus
import dev.anujyadav.harmoniq.services.globalsearch.download.DownloadErrorPolicy.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadErrorPolicyTest {
    @Test
    fun planSection12() {
        listOf(
            ErrorCodes.UPSTREAM_BLOCKED, ErrorCodes.UPSTREAM_ERROR, ErrorCodes.UPSTREAM_TIMEOUT,
            ErrorCodes.PROCESSING_FAILED, ErrorCodes.QUEUE_FULL, ErrorCodes.RATE_LIMITED,
            ErrorCodes.TOO_MANY_ACTIVE_JOBS,
        ).forEach { assertEquals(Action.Retry, DownloadErrorPolicy.classify(it, true), it) }
        listOf(
            ErrorCodes.VIDEO_UNAVAILABLE, ErrorCodes.AGE_RESTRICTED, ErrorCodes.LIVE_NOT_SUPPORTED,
            ErrorCodes.DURATION_LIMIT_EXCEEDED, ErrorCodes.UNAUTHORIZED,
        ).forEach { assertEquals(Action.Fail, DownloadErrorPolicy.classify(it, false), it) }
        listOf(ErrorCodes.JOB_NOT_FOUND, ErrorCodes.JOB_EXPIRED)
            .forEach { assertEquals(Action.Resubmit, DownloadErrorPolicy.classify(it, false), it) }
    }

    @Test
    fun unknownCodesFollowTheRetryableFlag() {
        assertEquals(Action.Retry, DownloadErrorPolicy.classify("brand_new_code", true))
        assertEquals(Action.Fail, DownloadErrorPolicy.classify("brand_new_code", false))
    }
}

class FileNamesTest {
    @Test
    fun collisionSuffix() {
        assertEquals("Coldplay - Yellow.m4a", FileNames.withCollisionSuffix("Coldplay - Yellow.m4a", 1))
        assertEquals("Coldplay - Yellow (2).m4a", FileNames.withCollisionSuffix("Coldplay - Yellow.m4a", 2))
        assertEquals("Mr. Brightside (3).opus", FileNames.withCollisionSuffix("Mr. Brightside.opus", 3))
        assertEquals("noext (2)", FileNames.withCollisionSuffix("noext", 2))
    }

    @Test
    fun nonLatinNamesSurvive() {
        assertEquals("अरिजीत सिंह - तुम ही हो (2).opus", FileNames.withCollisionSuffix("अरिजीत सिंह - तुम ही हो.opus", 2))
        assertEquals("米津玄師 - Lemon.lrc", FileNames.withExtension("米津玄師 - Lemon.opus", "lrc"))
    }

    @Test
    fun sha256() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.of(ByteArray(0).inputStream()),
        )
    }
}

class LibraryLinkTest {
    private val target = "primary:Download/Harmoniq"

    @Test
    fun parentsAndTheFolderItselfCover() {
        listOf("primary:", "primary:Download", "primary:Download/", "primary:Download/Harmoniq", "primary:download")
            .forEach { assertTrue(LibraryLink.covers(it, target), it) }
    }

    @Test
    fun siblingsChildrenAndOtherVolumesDoNot() {
        listOf(
            "primary:Music", "primary:Download/Harmoniq/Sub", "primary:Download/HarmoniqX",
            "1A2B-3C4D:Download/Harmoniq", "garbage",
        ).forEach { assertFalse(LibraryLink.covers(it, target), it) }
    }

    @Test
    fun anyFolderIsEnough() {
        assertTrue(LibraryLink.isCoveredByDocumentIds(listOf("primary:Music", "primary:Download"), target))
        assertFalse(LibraryLink.isCoveredByDocumentIds(emptyList(), target))
    }
}

class DownloadRecordsTest {
    @Test
    fun persistsAndReloads() {
        var stored: String? = null
        val records = DownloadRecords(load = { stored }, save = { stored = it }, clock = { 42L })
        records.update("a:opus") { DownloadRecord("a", "opus", "Song", state = DownloadRecord.State.Done, sha256 = "s", savedUri = "u") }

        val reloaded = DownloadRecords(load = { stored }, save = {})
        assertEquals("Song", reloaded.get("a:opus")!!.title)
        assertEquals(42L, reloaded.get("a:opus")!!.updatedAt)
        assertEquals("a", reloaded.findDone("s")!!.videoId)
        assertNull(reloaded.findDone("other"))
    }

    @Test
    fun corruptStorageStartsEmpty() {
        assertTrue(DownloadRecords(load = { "{not json" }, save = {}).records.value.isEmpty())
    }

    @Test
    fun trimsOldestButKeepsActive() {
        var now = 0L
        val records = DownloadRecords(load = { null }, save = {}, clock = { now++ })
        records.update("active:opus") { DownloadRecord("active", "opus", "A", state = DownloadRecord.State.Active) }
        repeat(ServerConfig.MAX_DOWNLOAD_RECORDS + 5) { i ->
            records.update("v$i:opus") { DownloadRecord("v$i", "opus", "S", state = DownloadRecord.State.Done) }
        }
        assertEquals(ServerConfig.MAX_DOWNLOAD_RECORDS, records.records.value.size)
        assertTrue(records.records.value.containsKey("active:opus"))
        assertFalse(records.records.value.containsKey("v0:opus"))
    }
}

class DownloadProgressTextTest {
    @Test
    fun stagesMatchPlan() {
        assertNull(DownloadProgressData.fraction(DownloadProgress.Waiting))
        assertEquals(0.42f, DownloadProgressData.fraction(DownloadProgress.OnServer(JobStatus.Downloading, 0.42f)))
        assertEquals(0.5f, DownloadProgressData.fraction(DownloadProgress.Saving(50, 100)))
        assertNull(DownloadProgressData.fraction(DownloadProgress.Saving(50, 0)))
        assertEquals(
            DownloadProgressData.text(DownloadProgress.OnServer(JobStatus.Tagging, 0.9f)),
            DownloadProgressData.text(DownloadProgress.OnServer(JobStatus.Verifying, 0.95f)),
        )
    }
}
