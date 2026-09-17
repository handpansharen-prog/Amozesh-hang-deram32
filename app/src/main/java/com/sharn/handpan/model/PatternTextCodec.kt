package com.sharn.handpan.model

/**
 * Compact, deterministic text format for handpan events.
 *
 * Token format: NOTE@POSITION:DURATION#TECHNIQUE^HAND~VELOCITY!
 * Suffixes are optional; ! marks an accent. Tokens are separated by whitespace or commas.
 */
object PatternTextCodec {
    private val tokenPattern = Regex(
        "^([^@:#^~!]+)(?:@([^:#^~!]+))?(?::([^#^~!]+))?(?:#([^@:#^~!]+))?(?:\\^([^@:#^~!]+))?(?:~([^@:#^~!]+))?(!)?$"
    )

    fun parse(
        text: String,
        instrumentProfile: InstrumentProfile = InstrumentProfile.DEFAULT_D_KURD_9
    ): Result<List<NoteEvent>> {
        val tokens = text.split(Regex("[\\s,]+"), limit = 0).filter(String::isNotBlank)
        if (tokens.isEmpty()) return Result.failure(IllegalArgumentException("Pattern text is empty."))

        return runCatching {
            tokens.mapIndexed { index, token -> parseToken(token, index, instrumentProfile) }
        }
    }

    fun serialize(events: List<NoteEvent>): String = events.joinToString(" ") { event ->
        val note = when {
            event.isRest -> "REST"
            event.noteNumber == HandpanNote.DING_NUMBER -> "DING"
            else -> event.noteNumber.toString()
        }
        val technique = event.technique.takeUnless {
            it == defaultTechniqueFor(event.noteNumber, event.isRest)
        }?.let { "#${it.name}" }.orEmpty()
        val hand = event.hand?.let { "^$it" }.orEmpty()
        val velocity = if (event.velocity == 0.85f) "" else "~${format(event.velocity)}"
        val accent = if (event.accent) "!" else ""
        "$note@${format(event.beatPosition)}:${format(event.duration)}$technique$hand$velocity$accent"
    }

    private fun parseToken(token: String, index: Int, instrumentProfile: InstrumentProfile): NoteEvent {
        val match = tokenPattern.matchEntire(token)
            ?: error("Invalid token ${index + 1}: '$token'.")
        val noteToken = match.groupValues[1]
        val position = match.groupValues[2].takeIf(String::isNotEmpty)?.toDoubleOrNull() ?: index.toDouble()
        val duration = match.groupValues[3].takeIf(String::isNotEmpty)?.toDoubleOrNull() ?: 1.0
        val techniqueToken = match.groupValues[4].takeIf(String::isNotEmpty)
        val hand = match.groupValues[5].takeIf(String::isNotEmpty)
        val velocity = match.groupValues[6].takeIf(String::isNotEmpty)?.toFloatOrNull() ?: 0.85f
        val accent = match.groupValues[7] == "!"
        require(position >= 0.0) { "Beat position must be non-negative in token ${index + 1}." }
        require(duration > 0.0) { "Duration must be positive in token ${index + 1}." }
        require(velocity in 0.0f..1.0f) { "Velocity must be between 0 and 1 in token ${index + 1}." }
        require(hand == null || hand == "R" || hand == "L" || hand == "E") {
            "Hand must be R, L, or E in token ${index + 1}."
        }

        val note = resolveNote(noteToken, instrumentProfile)
        val isRest = noteToken.equals("REST", true) || noteToken == "𝄽"
        val technique = techniqueToken?.let {
            runCatching { HandpanTechnique.valueOf(it.uppercase()) }
                .getOrElse { error("Unknown technique '$it' in token ${index + 1}.") }
        } ?: defaultTechniqueFor(note, isRest)
        require(isRest == (technique == HandpanTechnique.REST)) {
            "REST must use REST technique in token ${index + 1}."
        }
        return NoteEvent(
            noteNumber = note,
            beatPosition = position,
            duration = duration,
            velocity = velocity,
            accent = accent,
            isRest = isRest,
            hand = hand,
            technique = technique
        )
    }

    private fun resolveNote(token: String, profile: InstrumentProfile): Int {
        if (token.equals("REST", true) || token == "𝄽") return 0
        if (token.equals("DING", true)) return 0
        require(!token.equals("D", true)) { "Use DING for the central Ding; D is ambiguous." }
        token.toIntOrNull()?.let {
            require(it in 0..9) { "Note number must be between 0 and 9." }
            return it
        }
        profile.fields.firstOrNull { it.scientificPitch.equals(token, true) }?.let { return it.displayNumber }
        profile.fields.firstOrNull { it.pitchName.equals(token, true) }?.let { return it.displayNumber }
        profile.fields.firstOrNull { it.solfegeName == token }?.let { return it.displayNumber }
        error("Unknown note '$token'.")
    }

    private fun defaultTechniqueFor(note: Int, isRest: Boolean): HandpanTechnique = when {
        isRest -> HandpanTechnique.REST
        note == 9 -> HandpanTechnique.SLAP
        note == 0 -> HandpanTechnique.DING
        else -> HandpanTechnique.TONE
    }

    private fun format(value: Double): String = value.toString().trimEnd('0').trimEnd('.')
    private fun format(value: Float): String = format(value.toDouble())
}
