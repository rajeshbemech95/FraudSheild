package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.api.TriageEngine
import com.example.model.AnalysisSource
import com.example.model.IncidentStage
import com.example.model.InvestigationResult
import com.example.model.PrioritizedAction
import com.example.model.RiskLevel
import com.example.util.ImageStreamHelper
import com.example.util.ImageValidationResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class FirebaseAiInvestigationRepository {

    private val generativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3-flash-preview",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            }
        )
    }

    suspend fun investigate(
        context: Context,
        text: String,
        stage: IncidentStage,
        imageUri: Uri? = null,
        language: String = "en"
    ): InvestigationResult = withContext(Dispatchers.IO) {
        // Enforce deterministic emergency rule: SENT_MONEY immediately triggers Critical Risk
        if (stage == IncidentStage.SENT_MONEY) {
            return@withContext TriageEngine.analyze(
                text = text,
                stage = stage,
                hasScreenshot = imageUri != null,
                screenshotUri = imageUri?.toString()
            )
        }

        var validatedBitmap: Bitmap? = null
        if (imageUri != null) {
            val validation = ImageStreamHelper.readAndValidateImageUri(context, imageUri)
            if (validation is ImageValidationResult.Valid) {
                validatedBitmap = validation.bitmap
            }
        }

        try {
            val systemPrompt = """
                You are FraudShield AI, an authoritative fraud triage engine.
                Analyze the provided communication or screenshot carefully.
                Classify the threat and return a strict JSON object with:
                - riskLevel: one of "CRITICAL", "HIGH", "SUSPICIOUS", "LOW", "SAFE"
                - headline: concise factual threat summary (under 120 chars)
                - summary: clear explanation of threat mechanism and safety advice
                - verifiedObservations: list of strictly observable facts from the text/image
                - aiInferences: list of logical deductions/hypotheses clearly marked as inferences
                - unknowns: list of unverified details (e.g. sender identity, authenticity)
                - prioritizedActions: list of objects with "id", "title", "description", "isUrgent" (boolean)
                - emergencyTriggered: boolean (true if emergency police/bank contact is needed)
                - imageAnalyzed: boolean
                
                Incident Stage context: ${stage.name} - ${stage.description}
                User Language: $language
            """.trimIndent()

            val contentPayload = content {
                text(systemPrompt)
                if (text.isNotBlank()) {
                    text("User message / context:\n$text")
                }
                validatedBitmap?.let {
                    image(it)
                }
            }

            val response = generativeModel.generateContent(contentPayload)
            val jsonText = response.text

            if (jsonText.isNullOrBlank()) {
                return@withContext fallback(text, stage, imageUri)
            }

            parseStructuredResponse(jsonText, text, stage, imageUri)
        } catch (e: Exception) {
            // Local deterministic fallback engine
            fallback(text, stage, imageUri)
        }
    }

    private fun parseStructuredResponse(
        jsonString: String,
        originalText: String,
        stage: IncidentStage,
        imageUri: Uri?
    ): InvestigationResult {
        return try {
            val cleanJson = jsonString.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = JSONObject(cleanJson)

            val rawRisk = obj.optString("riskLevel", "SUSPICIOUS").uppercase()
            val parsedRisk = try {
                RiskLevel.valueOf(rawRisk)
            } catch (e: Exception) {
                RiskLevel.SUSPICIOUS
            }

            // Safety rule: SENT_MONEY must never be downgraded
            val riskLevel = if (stage == IncidentStage.SENT_MONEY) RiskLevel.CRITICAL else parsedRisk
            val headline = obj.optString("headline", "Investigation Completed")
            val summary = obj.optString("summary", "Analysis completed based on submitted artifacts.")

            val observations = mutableListOf<String>()
            val obsArray = obj.optJSONArray("verifiedObservations")
            if (obsArray != null) {
                for (i in 0 until obsArray.length()) observations.add(obsArray.getString(i))
            }

            val inferences = mutableListOf<String>()
            val infArray = obj.optJSONArray("aiInferences")
            if (infArray != null) {
                for (i in 0 until infArray.length()) inferences.add(infArray.getString(i))
            }

            val unknowns = mutableListOf<String>()
            val unkArray = obj.optJSONArray("unknowns")
            if (unkArray != null) {
                for (i in 0 until unkArray.length()) unknowns.add(unkArray.getString(i))
            }

            val actions = mutableListOf<PrioritizedAction>()
            val actArray = obj.optJSONArray("prioritizedActions")
            if (actArray != null) {
                for (i in 0 until actArray.length()) {
                    val aObj = actArray.getJSONObject(i)
                    actions.add(
                        PrioritizedAction(
                            id = aObj.optString("id", "act_$i"),
                            title = aObj.optString("title", "Action"),
                            description = aObj.optString("description", ""),
                            isUrgent = aObj.optBoolean("isUrgent", false)
                        )
                    )
                }
            }

            val emergencyTriggered = (riskLevel == RiskLevel.CRITICAL || obj.optBoolean("emergencyTriggered", false))

            InvestigationResult(
                riskLevel = riskLevel,
                headline = headline,
                summary = summary,
                stage = stage,
                rawInputSnippet = originalText.take(150),
                hasScreenshot = imageUri != null,
                screenshotUri = imageUri?.toString(),
                verifiedObservations = observations,
                aiInferences = inferences,
                unknowns = unknowns,
                prioritizedActions = actions,
                emergencyTriggered = emergencyTriggered,
                isDemoResult = false,
                analysisSource = AnalysisSource.REMOTE_AI
            )
        } catch (e: Exception) {
            fallback(originalText, stage, imageUri)
        }
    }

    private fun fallback(
        text: String,
        stage: IncidentStage,
        imageUri: Uri?
    ): InvestigationResult {
        val base = TriageEngine.analyze(
            text = text,
            stage = stage,
            hasScreenshot = imageUri != null,
            screenshotUri = imageUri?.toString()
        )
        return base.copy(analysisSource = AnalysisSource.OFFLINE_FALLBACK)
    }
}
