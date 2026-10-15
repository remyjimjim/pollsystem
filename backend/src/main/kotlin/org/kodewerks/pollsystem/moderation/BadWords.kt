package org.kodewerks.pollsystem.moderation

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.regex.Pattern

/** One entry of moderation/bad-words.json. */
data class BadWord(
    @JsonProperty("bad-word") val word: String,
    @JsonProperty("replace") val replace: String
)

/**
 * The words a public comment may not contain (moderation/bad-words.json).
 * Matching is whole-word and ignores case: "Darn" matches "darn", but
 * "darned" only if it's listed itself. The page offers each replacement;
 * the server rechecks, so a comment still containing one stays private.
 */
@Component
class BadWords(objectMapper: ObjectMapper) {
    val list: List<BadWord> = ClassPathResource("moderation/bad-words.json").inputStream.use {
        objectMapper.readValue<List<BadWord>>(it)
    }.filter { it.word.isNotBlank() }

    private val patterns = list.map {
        it to Pattern.compile(
            "(?<![\\p{L}\\p{N}])" + Pattern.quote(it.word.trim()) + "(?![\\p{L}\\p{N}])",
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

/** The list, for the response forms' "Can I replace … with …?" check (public: the page needs it anyway). */
@RestController
class BadWordsController(private val badWords: BadWords) {
    @GetMapping("/api/moderation/bad-words")
    fun get(): List<Map<String, String>> = badWords.list.map { mapOf("word" to it.word, "replace" to it.replace) }
}
