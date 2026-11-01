package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.security.AppUserDetails
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/polls/questionnaires")
class QuestionnaireController(
    private val service: QuestionnaireService,
    private val badWords: org.kodewerks.pollsystem.moderation.BadWords
) {

    @PostMapping
    fun saveDraft(
        @AuthenticationPrincipal principal: AppUserDetails,
        @Valid @RequestBody body: QuestionnaireDraftRequest
    ): ResponseEntity<QuestionnaireDto> {
        requireCleanText(body)
        val saved = service.saveDraft(principal.user, body)
        return ResponseEntity.status(HttpStatus.CREATED).body(service.toDto(saved))
    }

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable id: Long,
        @Valid @RequestBody body: QuestionnaireDraftRequest
    ): QuestionnaireDto {
        requireCleanText(body)
        return service.toDto(service.update(id, principal.user, body))
    }

    private fun requireCleanText(body: QuestionnaireDraftRequest) = badWords.requireClean(
        "the title" to body.title,
        "the summary" to body.summary,
        *body.questions.mapIndexed { i, q -> "question ${i + 1}" to q.text }.toTypedArray()
    )

    @PostMapping("/{id}/publish")
    fun publish(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable id: Long,
        @RequestParam(defaultValue = "false") confirmed: Boolean
    ): QuestionnaireDto = service.toDto(service.publish(id, principal.user, confirmed))

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): QuestionnaireDto =
        service.toDto(service.get(id))
}
