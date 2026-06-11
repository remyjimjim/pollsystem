package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.Question
import org.kodewerks.pollsystem.model.Questionnaire
import org.kodewerks.pollsystem.model.QuestionnaireDomain
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.PollTypeRepository
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.repository.QuestionResponseRepository
import org.kodewerks.pollsystem.repository.QuestionnaireDomainRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class QuestionnaireService(
    private val questionnaires: QuestionnaireRepository,
    private val questions: QuestionRepository,
    private val questionResponses: QuestionResponseRepository,
    private val domains: QuestionnaireDomainRepository,
    private val pollTypes: PollTypeRepository,
    private val countyZips: CountyZipsRepository
) {

    @Transactional
    fun saveDraft(creator: User, dto: QuestionnaireDraftRequest): Questionnaire {
        val pt = pollTypes.findById(dto.pollTypeId).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type")
        }
        val saved = questionnaires.save(
            Questionnaire(
                creator = creator,
                pollType = pt,
                title = dto.title.trim(),
                summary = dto.summary.trim(),
                status = PollStatus.DRAFT,
                closeDate = dto.closeDate
            )
        )
        replaceQuestions(saved, dto.questions)
        replaceDomains(saved, dto.zipcodes)
        return saved
    }

    @Transactional
    fun update(id: Long, creator: User, dto: QuestionnaireDraftRequest): Questionnaire {
        val existing = loadOwned(id, creator)
        if (existing.status != PollStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT questionnaires can be edited")
        }
        val pt = pollTypes.findById(dto.pollTypeId).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type")
        }
        val updated = questionnaires.save(
            existing.copy(
                pollType = pt,
                title = dto.title.trim(),
                summary = dto.summary.trim(),
                closeDate = dto.closeDate
            )
        )
        replaceQuestions(updated, dto.questions)
        replaceDomains(updated, dto.zipcodes)
        return updated
    }

    @Transactional
    fun publish(id: Long, creator: User, confirmed: Boolean): Questionnaire {
        val existing = loadOwned(id, creator)
        if (existing.status != PollStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT questionnaires can be published")
        }
        val q = questions.findByQuestionnaireId(existing.id)
        val d = domains.findByQuestionnaireId(existing.id)
        if (q.isEmpty()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one question required")
        if (d.isEmpty()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one zipcode required")

        val close = existing.closeDate
        if (close != null) {
            // submitDate is null until the first publish. On re-publish (after
            // archive + restore) the creator can set a past close date to wind
            // an already-live poll down. The 5-day-out warning still fires so
            // accidental near-now picks still prompt a confirmation.
            val isFirstPublish = existing.submitDate == null
            if (isFirstPublish && close.isBefore(Instant.now())) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Close date must be in the future")
            }
            val daysOut = ChronoUnit.DAYS.between(Instant.now(), close)
            if (daysOut < 5 && !confirmed) {
                // Signal to caller that confirmation is required
                throw ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "close_date_short:$close"
                )
            }
        }

        return questionnaires.save(
            existing.copy(status = PollStatus.PUBLISHED, submitDate = Instant.now())
        )
    }

    @Transactional(readOnly = true)
    fun get(id: Long): Questionnaire =
        questionnaires.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Questionnaire not found")
        }

    @Transactional(readOnly = true)
    fun listForCreator(creatorId: Long): List<Questionnaire> =
        questionnaires.findByCreatorId(creatorId)

    @Transactional(readOnly = true)
    fun toDto(q: Questionnaire): QuestionnaireDto {
        val qList = questions.findByQuestionnaireId(q.id)
        val dList = domains.findByQuestionnaireId(q.id)
        return QuestionnaireDto.from(q, qList, dList)
    }

    private fun loadOwned(id: Long, creator: User): Questionnaire {
        val q = get(id)
        if (q.creator.id != creator.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not your questionnaire")
        }
        return q
    }

    private fun replaceQuestions(q: Questionnaire, inputs: List<QuestionInput>) {
        val existing = questions.findByQuestionnaireId(q.id)
        val existingTexts = existing.map { it.question.trim() }
        val incomingTexts = inputs.map { it.text.trim() }
        // No structural change → nothing to write. This is the path that
        // a close-date-only edit must take when the questionnaire was
        // previously PUBLISHED and has responses tied to its questions;
        // re-running the delete-and-recreate below would trip the
        // question_responses FK.
        if (existingTexts == incomingTexts) return
        if (existing.isNotEmpty() &&
            existing.any { questionResponses.findByQuestionId(it.id).isNotEmpty() }
        ) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Questions cannot be changed because responses have already been cast on this poll. " +
                    "To adjust title, summary, or close date, use Super Admin → Manage All Polls."
            )
        }
        questions.deleteAll(existing)
        questions.saveAll(inputs.map { Question(questionnaire = q, question = it.text.trim()) })
    }

    private fun replaceDomains(q: Questionnaire, zipcodes: List<String>) {
        domains.deleteAll(domains.findByQuestionnaireId(q.id))
        val zipRows = countyZips.findByZipcodeIn(zipcodes.distinct())
        val zipToCounty = zipRows.associateBy { it.zipcode }
        val unknown = zipcodes.distinct().filterNot { it in zipToCounty }
        if (unknown.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown zipcodes: $unknown")
        }
        val newDomains = zipcodes.distinct().map { zip ->
            val cz = zipToCounty.getValue(zip)
            QuestionnaireDomain(
                questionnaire = q,
                state = cz.county.state,
                county = cz.county,
                zipcode = zip
            )
        }
        domains.saveAll(newDomains)
    }
}
