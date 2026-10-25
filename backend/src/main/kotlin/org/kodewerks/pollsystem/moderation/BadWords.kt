package org.kodewerks.pollsystem.moderation

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.regex.Pattern

/** One entry of moderation/bad-words.json. */
data class BadWord(
    @JsonProperty("bad-word") val word: String,
    @JsonProperty("replace") val replace: String
)

/**
 * The words a public comment may not contain (moderation/bad-words.json).
 * Matching is whole-word and ignores case: "Darn" matches "darn". A plural
 * or possessive ending (-s, -es, -'s) is caught too, so each word is listed
 * once (decided 2026-10-25); other forms ("darned") only if listed themselves. The list never leaves the server: the
 * page sends its public comments to /api/moderation/check and gets back only
 * the words found. Saving rechecks, so a comment still containing one stays private.
 */
@Component
class BadWords(objectMapper: ObjectMapper) {
    val list: List<BadWord> = ClassPathResource("moderation/bad-words.json").inputStream.use {
        objectMapper.readValue<List<BadWord>>(it)
    }.filter { it.word.isNotBlank() }

    private val patterns = list.map {
        it to Pattern.compile(
            "(?<![\\p{L}\\p{N}])" + Pattern.quote(it.word.trim()) + "(?:s|es|['’]s)?(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        )
    }

    /** The listed words found in [text], in list order. */
    fun foundIn(text: String?): List<BadWord> =
        if (text.isNullOrBlank()) emptyList() else patterns.filter { (_, p) -> p.matcher(text).find() }.map { it.first }

    /** Whether a comment may be stored as public: asked for, not blank, and clean. */
    fun publishable(comment: String?, wantsPublic: Boolean): Boolean =
        wantsPublic && !comment.isNullOrBlank() && foundIn(comment).isEmpty()
}

/** Public comments to check, in the form's order. */
data class CheckCommentsRequest(
    @field:Size(max = 200) val texts: List<@Size(max = 5000) String>
)

/** One listed word found, with its replacement. */
data class FoundWord(val word: String, val replace: String)

/** Per text (same order), the listed words found in it. */
data class CheckCommentsResponse(val found: List<List<FoundWord>>)

/**
 * The answer forms' "Can I replace … with …?" check. Signed-in users only, and
 * it returns just the words found in what was sent, never the list itself.
 */
@RestController
class BadWordsController(private val badWords: BadWords) {
    @PostMapping("/api/moderation/check")
    fun check(@Valid @RequestBody body: CheckCommentsRequest): CheckCommentsResponse =
        CheckCommentsResponse(body.texts.map { t -> badWords.foundIn(t).map { FoundWord(it.word, it.replace) } })
}
