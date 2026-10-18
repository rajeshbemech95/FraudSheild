package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AnalysisSource
import com.example.model.IncidentStage
import com.example.model.InvestigationResult
import com.example.model.PrioritizedAction
import com.example.model.RiskLevel

@Entity(tableName = "cases")
data class CaseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val firestoreId: String = "",
    val riskLevel: RiskLevel,
    val headline: String,
    val summary: String,
    val stage: IncidentStage,
    val rawInputSnippet: String = "",
    val hasScreenshot: Boolean = false,
    val screenshotUri: String? = null,
    val verifiedObservations: List<String> = emptyList(),
    val aiInferences: List<String> = emptyList(),
    val unknowns: List<String> = emptyList(),
    val prioritizedActions: List<PrioritizedAction> = emptyList(),
    val emergencyTriggered: Boolean = false,
    val isDemoResult: Boolean = false,
    val analysisSource: AnalysisSource = AnalysisSource.LOCAL_RULE,
    val userNotes: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): InvestigationResult = InvestigationResult(
        id = id,
        firestoreId = firestoreId,
        riskLevel = riskLevel,
        headline = headline,
        summary = summary,
        stage = stage,
        rawInputSnippet = rawInputSnippet,
        hasScreenshot = hasScreenshot,
        screenshotUri = screenshotUri,
        verifiedObservations = verifiedObservations,
        aiInferences = aiInferences,
        unknowns = unknowns,
        prioritizedActions = prioritizedActions,
        emergencyTriggered = emergencyTriggered,
        isDemoResult = isDemoResult,
        analysisSource = analysisSource,
        userNotes = userNotes,
        timestamp = timestamp
    )

    companion object {
        fun fromDomain(domain: InvestigationResult): CaseEntity = CaseEntity(
            id = domain.id,
            firestoreId = domain.firestoreId,
            riskLevel = domain.riskLevel,
            headline = domain.headline,
            summary = domain.summary,
            stage = domain.stage,
            rawInputSnippet = domain.rawInputSnippet,
            hasScreenshot = domain.hasScreenshot,
            screenshotUri = domain.screenshotUri,
            verifiedObservations = domain.verifiedObservations,
            aiInferences = domain.aiInferences,
            unknowns = domain.unknowns,
            prioritizedActions = domain.prioritizedActions,
            emergencyTriggered = domain.emergencyTriggered,
            isDemoResult = domain.isDemoResult,
            analysisSource = domain.analysisSource,
            userNotes = domain.userNotes,
            timestamp = domain.timestamp
        )
    }
}
