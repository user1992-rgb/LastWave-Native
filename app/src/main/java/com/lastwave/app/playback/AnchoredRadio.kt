package com.lastwave.app.playback

/**
 * Keeps endless radio in the style of the song the user picked.
 *
 * Upstream radio re-seeds from the *last* song in the queue, so every refill
 * drifts one hop further from the seed (metal → rock → pop → lo-fi). Here the
 * first YouTube Music radio batch for the user's song defines the session:
 * its artists form the allowed [artistPool], its tracks are the [anchorSeeds]
 * later refills re-seed from, and songs by the seed's own band are mixed in
 * every [BAND_EVERY] tracks.
 */
class AnchoredRadio(
    seed: PlayableTrack,
    firstBatch: List<PlayableTrack>,
    bandTracks: List<PlayableTrack>,
) {
    private val seedArtists = artistKeys(seed.artist)
    val artistPool: Set<String> = seedArtists + firstBatch.flatMap { artistKeys(it.artist) }
    val anchorSeeds: List<PlayableTrack> = listOf(seed) + firstBatch.take(MAX_ANCHOR_SEEDS)
    private val bandQueue = ArrayDeque(
        bandTracks.filter { !it.videoId.isNullOrBlank() && artistKeys(it.artist).any(seedArtists::contains) },
    )

    fun inPool(track: PlayableTrack): Boolean = artistKeys(track.artist).any(artistPool::contains)

    /**
     * Drops tracks outside the pool and slots one band song in after every
     * [BAND_EVERY] pool tracks. [isKnown] rejects band songs already queued or
     * recently played.
     */
    fun shape(
        batch: List<PlayableTrack>,
        filterToPool: Boolean,
        isKnown: (PlayableTrack) -> Boolean,
    ): List<PlayableTrack> {
        val kept = if (filterToPool) batch.filter(::inPool) else batch
        val out = ArrayList<PlayableTrack>(kept.size + kept.size / BAND_EVERY + 1)
        val added = mutableSetOf<String>()
        kept.forEachIndexed { index, track ->
            if (track.videoId?.let(added::add) != false) out += track
            if ((index + 1) % BAND_EVERY == 0) {
                nextBandTrack { isKnown(it) || it.videoId in added }?.let {
                    added += it.videoId.orEmpty()
                    out += it
                }
            }
        }
        return out
    }

    private fun nextBandTrack(skip: (PlayableTrack) -> Boolean): PlayableTrack? {
        while (bandQueue.isNotEmpty()) {
            val track = bandQueue.removeFirst()
            if (!skip(track)) return track
        }
        return null
    }

    companion object {
        /** One song by the picked band after this many radio songs. */
        const val BAND_EVERY = 4
        private const val MAX_ANCHOR_SEEDS = 12
        private val SEPARATORS = Regex(
            """\s*(?:,|&|\+|/|;|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+|\s+featuring\s+|\s+vs\.?\s+)\s*""",
            RegexOption.IGNORE_CASE,
        )

        /** "Metallica & San Francisco Symphony" → {"metallica", "san francisco symphony"}. */
        fun artistKeys(artist: String): Set<String> =
            artist.split(SEPARATORS)
                .map { it.trim().lowercase().removePrefix("the ") }
                .filter { it.isNotEmpty() && it != "various artists" }
                .toSet()
    }
}
