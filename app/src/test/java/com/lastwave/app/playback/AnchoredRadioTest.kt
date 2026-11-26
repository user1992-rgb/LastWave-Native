package com.lastwave.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchoredRadioTest {

    private fun track(id: String, artist: String) = PlayableTrack(title = "Song $id", artist = artist, videoId = id)

    private val seed = track("seed", "Metallica")
    private val firstBatch = listOf(
        track("1", "Megadeth"),
        track("2", "Slayer"),
        track("3", "Iron Maiden"),
        track("4", "Anthrax"),
    )

    @Test
    fun `artist keys split collaborations and ignore the article`() {
        assertEquals(setOf("metallica", "san francisco symphony"), AnchoredRadio.artistKeys("Metallica & San Francisco Symphony"))
        assertEquals(setOf("slipknot", "corey taylor"), AnchoredRadio.artistKeys("Slipknot feat. Corey Taylor"))
        assertEquals(setOf("offspring"), AnchoredRadio.artistKeys("The Offspring"))
    }

    @Test
    fun `refill drops artists outside the first batch and mixes in the band`() {
        val radio = AnchoredRadio(seed, firstBatch, bandTracks = listOf(track("b1", "Metallica")))
        val refill = listOf(
            track("5", "Megadeth"), track("6", "Lofi Girl"), track("7", "Slayer"), track("8", "Drake"),
            track("9", "Iron Maiden"), track("10", "Anthrax"), track("11", "Slayer"),
        )

        val shaped = radio.shape(refill, filterToPool = true) { false }

        assertEquals(listOf("5", "7", "9", "10", "b1", "11"), shaped.map { it.videoId })
    }

    @Test
    fun `band songs skip tribute acts and already known tracks`() {
        val band = listOf(track("b1", "Metallica Tribute Band"), track("b2", "Metallica"), track("b3", "Metallica & Lou Reed"))
        val radio = AnchoredRadio(seed, firstBatch, band)

        val shaped = radio.shape(firstBatch, filterToPool = false) { it.videoId == "b2" }

        assertEquals(listOf("1", "2", "3", "4", "b3"), shaped.map { it.videoId })
    }

    @Test
    fun `re-seeding starts from the picked song`() {
        val radio = AnchoredRadio(seed, firstBatch, emptyList())
        assertEquals("seed", radio.anchorSeeds.first().videoId)
        assertTrue(radio.shape(listOf(track("x", "Bad Bunny")), filterToPool = true) { false }.isEmpty())
    }
}
