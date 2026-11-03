package dev.anujyadav.harmoniq.services.globalsearch.api

import dev.anujyadav.harmoniq.services.globalsearch.Fixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DtoParsingTest {
    private inline fun <reified T> parse(name: String): T =
        ServerJson.decodeFromString<T>(Fixtures.read(name))

    @Test
    fun meta() {
        val meta = parse<MetaDto>("meta.json")
        assertEquals("1.0.0", meta.apiVersion)
        assertEquals("0.2.0", meta.serverVersion)
        assertEquals(listOf("m4a", "opus", "mp3"), meta.formats)
        assertEquals(900, meta.maxDurationSeconds)
        assertEquals(30, meta.limits?.searchPerMinute)
        assertEquals(3, meta.limits?.maxActiveJobs)
        assertEquals(YoutubeStatus.Ok, meta.youtubeStatus)
        assertNull(meta.status?.lastCanaryOk)
    }

    @Test
    fun songSearch() {
        val search = parse<SearchResponseDto>("search_song.json")
        assertEquals("song", search.type)
        assertEquals("youtube_music", search.source)
        val first = search.results.first()
        assertEquals("4D7u5KF7SP8", first.videoId)
        assertEquals("Get Lucky (feat. Pharrell Williams and Nile Rodgers)", first.title)
        assertEquals(listOf("Daft Punk", "Pharrell Williams", "Nile Rodgers"), first.displayArtists)
        assertEquals("Random Access Memories", first.album?.name)
        assertEquals(370, first.duration)
        assertEquals(false, first.isExplicit)
        // the largest thumbnail wins
        assertTrue(first.bestThumbnail()!!.contains("w120-h120"))
    }

    @Test
    fun videoSearchUsesChannelAsArtist() {
        val search = parse<SearchResponseDto>("search_video.json")
        assertEquals("video", search.type)
        val zoo = search.results.first()
        assertEquals("jNQXAC9IVRw", zoo.videoId)
        assertNull(zoo.album)
        assertNull(zoo.isExplicit)
        assertEquals(listOf("jawed"), zoo.displayArtists)
        assertNotNull(zoo.viewCount)
        // a channel without an id or url still parses
        assertNull(search.results[1].channel?.id)
    }

    @Test
    fun track() {
        val track = parse<SongMetadataDto>("track.json")
        assertEquals("4D7u5KF7SP8", track.videoId)
        assertEquals("Random Access Memories", track.album)
        assertEquals(8, track.trackNumber)
        assertEquals(13, track.trackTotal)
        assertEquals(1, track.discNumber)
        assertEquals("2013-05-17", track.date)
        assertEquals(2013, track.year)
        assertEquals(listOf("Electronic", "Disco", "Funk"), track.genres)
        assertEquals("MUSIC_VIDEO_TYPE_ATV", track.videoType)
        assertTrue(track.lyrics!!.available)
        assertEquals(
            "Daft Punk - Get Lucky (feat. Pharrell Williams and Nile Rodgers).opus",
            track.suggestedFilename["opus"],
        )
    }

    @Test
    fun preview() {
        val preview = parse<PreviewDto>("preview.json")
        assertTrue(preview.embeddable)
        assertFalse(preview.ageRestricted)
        assertFalse(preview.isLive)
        assertEquals(370, preview.duration)
        assertTrue(preview.embedUrl!!.startsWith("https://www.youtube-nocookie.com/embed/4D7u5KF7SP8"))
    }

    @Test
    fun submit() {
        val submit = parse<SubmitResponseDto>("submit.json")
        assertTrue(submit.jobId.startsWith("dl_"))
        assertEquals(JobStatus.Queued, JobStatus.from(submit.status))
        assertFalse(submit.cacheHit)
    }

    @Test
    fun runningStatuses() {
        val resolving = parse<JobStatusDto>("status_running.json")
        assertEquals(JobStatus.Resolving, resolving.jobStatus)
        assertFalse(resolving.jobStatus.isTerminal)
        assertNull(resolving.file)

        val downloading = parse<JobStatusDto>("status_downloading.json")
        assertEquals(JobStatus.Downloading, downloading.jobStatus)
        assertEquals(0.3619, downloading.progress, 1e-9)
        assertEquals(5203271L, downloading.stage?.totalBytes)
    }

    @Test
    fun completedOpus() {
        val status = parse<JobStatusDto>("status_completed.json")
        assertEquals(JobStatus.Completed, status.jobStatus)
        val file = status.file!!
        assertEquals("Daft Punk - Get Lucky (feat. Pharrell Williams and Nile Rodgers).opus", file.filename)
        assertEquals("audio/ogg", file.mimeType)
        assertEquals(6286631L, file.sizeBytes)
        assertEquals("d9215c111228d22d9eaf9f9491570530371c95a412a7d9a089ef3ef8e4756e49", file.sha256)
        val metadata = status.metadata!!
        assertNull(metadata.videoId)
        assertEquals("Daft Punk", metadata.albumArtist)
        assertEquals(2013, metadata.year)
        assertTrue(metadata.lyrics!!.synced)
        assertEquals(109, metadata.lyrics!!.lines)
    }

    @Test
    fun completedM4a() {
        val status = parse<JobStatusDto>("status_completed_m4a.json")
        assertEquals("audio/mp4", status.file!!.mimeType)
        assertEquals("Daft Punk - One More Time.m4a", status.file!!.filename)
    }

    @Test
    fun failed() {
        val status = parse<JobStatusDto>("status_failed.json")
        assertEquals(JobStatus.Failed, status.jobStatus)
        assertEquals(ErrorCodes.DURATION_LIMIT_EXCEEDED, status.error!!.code)
        assertFalse(status.error!!.retryable)
    }

    @Test
    fun problems() {
        listOf(
            "error_unauthorized.json" to (401 to ErrorCodes.UNAUTHORIZED),
            "error_job_not_found.json" to (404 to ErrorCodes.JOB_NOT_FOUND),
            "error_invalid_video_id.json" to (422 to "invalid_video_id"),
        ).forEach { (name, expected) ->
            val problem = parse<ProblemDto>(name)
            assertEquals(expected.first, problem.status, name)
            assertEquals(expected.second, problem.code, name)
            assertFalse(problem.retryable, name)
            assertNotNull(problem.requestId, name)
        }
    }

    @Test
    fun unknownStatusAndExtraFieldsAreTolerated() {
        val status = ServerJson.decodeFromString<JobStatusDto>(
            """{"job_id":"dl_x","status":"warp_speed","progress":0.5,"brand_new_field":{"a":1}}"""
        )
        assertEquals(JobStatus.Unknown, status.jobStatus)
    }
}
