package com.example.model

import androidx.compose.ui.graphics.Color

enum class RiskLevel(val label: String, val color: Color) {
    CRITICAL("Critical Threat", Color(0xFFDC2626)),
    HIGH("High Risk", Color(0xFFEA580C)),
    SUSPICIOUS("Suspicious", Color(0xFFD97706)),
    LOW("Low Concern", Color(0xFF2563EB)),
    SAFE("Likely Legitimate", Color(0xFF16A34A))
}

enum class IncidentStage(val label: String, val description: String) {
    RECEIVED("Just Received", "I only received this message or offer"),
    CLICKED_LINK("Clicked Link", "I tapped a link or downloaded an attachment"),
    ENTERED_OTP("Entered Details / OTP", "I entered credentials, bank details, or shared an OTP"),
    SENT_MONEY("Transferred Money", "I already sent money or approved a UPI transfer")
}

enum class AnalysisSource {
    LOCAL_RULE,
    REMOTE_AI,
    DEMO,
    OFFLINE_FALLBACK
}

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    TAMIL("ta", "தமிழ்"),
    HINDI("hi", "हिंदी")
}

data class PrioritizedAction(
    val id: String,
    val title: String,
    val description: String,
    val isUrgent: Boolean = false,
    val isCompleted: Boolean = false
)

data class InvestigationResult(
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
)

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?
)
