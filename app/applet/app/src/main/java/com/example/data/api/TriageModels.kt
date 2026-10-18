package com.example.data.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TriageRequest(
    val messageText: String,
    val incidentStage: String,
    val hasScreenshot: Boolean = false,
    val language: String = "en"
)

@JsonClass(generateAdapter = true)
data class TriageActionDto(
    val id: String,
    val title: String,
    val description: String,
    val isUrgent: Boolean = false
)

@JsonClass(generateAdapter = true)
data class TriageResponse(
    val riskLevel: String,
    val headline: String,
    val summary: String,
    val verifiedObservations: List<String> = emptyList(),
    val aiInferences: List<String> = emptyList(),
    val unknowns: List<String> = emptyList(),
    val prioritizedActions: List<TriageActionDto> = emptyList(),
    val emergencyTriggered: Boolean = false,
    val imageAnalyzed: Boolean = false
)
