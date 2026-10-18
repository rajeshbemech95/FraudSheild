package com.example.data.api

import android.content.Context
import android.net.Uri
import com.example.model.AnalysisSource
import com.example.model.IncidentStage
import com.example.model.InvestigationResult
import com.example.model.PrioritizedAction
import com.example.model.RiskLevel
import com.example.util.ImageStreamHelper
import com.example.util.ImageValidationResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

sealed class BackendStatus {
    object Idle : BackendStatus()
    object Checking : BackendStatus()
    data class Connected(val statusCode: Int, val latencyMs: Long, val message: String) : BackendStatus()
    data class Offline(val reason: String, val statusCode: Int? = null) : BackendStatus()
}

interface TriageApiService {
    @POST("api/triage")
    suspend fun triageMessage(@Body request: TriageRequest): Response<TriageResponse>

    @Multipart
    @POST("api/triage/upload")
    suspend fun triageImage(
        @Part image: MultipartBody.Part,
        @Part("incidentStage") stage: RequestBody,
        @Part("language") language: RequestBody,
        @Part("messageText") messageText: RequestBody? = null
    ): Response<TriageResponse>

    @GET("api/health")
    suspend fun checkHealth(): Response<Map<String, Any>>
}

class TriageApiClient(private var baseUrl: String = "http://10.0.2.2:8000/") {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var apiService: TriageApiService = createService(baseUrl)

    private fun createService(url: String): TriageApiService {
        val formattedUrl = if (url.endsWith("/")) url else "$url/"
        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TriageApiService::class.java)
    }

    fun updateBaseUrl(newUrl: String) {
        baseUrl = newUrl
        apiService = createService(newUrl)
    }

    fun getBaseUrl(): String = baseUrl

    suspend fun checkBackendHealth(): BackendStatus {
        val start = System.currentTimeMillis()
        return try {
            val response = apiService.checkHealth()
            val latency = System.currentTimeMillis() - start
            if (response.isSuccessful) {
                BackendStatus.Connected(response.code(), latency, "Connected to $baseUrl")
            } else {
                BackendStatus.Offline("HTTP ${response.code()} from backend", response.code())
            }
        } catch (e: Exception) {
            BackendStatus.Offline("Could not reach backend: ${e.message}")
        }
    }

    suspend fun analyzeText(
        text: String,
        stage: IncidentStage,
        language: String = "en"
    ): Result<InvestigationResult> {
        return try {
            val req = TriageRequest(
                messageText = text,
                incidentStage = stage.name,
                hasScreenshot = false,
                language = language
            )
            val response = apiService.triageMessage(req)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(mapResponseToDomain(body, stage, text, false, null))
            } else {
                Result.failure(Exception("Remote error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeImage(
        context: Context,
        imageUri: Uri,
        stage: IncidentStage,
        language: String = "en",
        optionalText: String? = null
    ): Result<InvestigationResult> {
        val validation = ImageStreamHelper.readAndValidateImageUri(context, imageUri)
        if (validation !is ImageValidationResult.Valid) {
            return Result.failure(Exception("Image validation failed"))
        }

        return try {
            val requestBody = validation.bytes.toRequestBody(validation.mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("image", "screenshot.jpg", requestBody)
            val stageBody = stage.name.toRequestBody("text/plain".toMediaType())
            val langBody = language.toRequestBody("text/plain".toMediaType())
            val textBody = optionalText?.toRequestBody("text/plain".toMediaType())

            val response = apiService.triageImage(part, stageBody, langBody, textBody)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(mapResponseToDomain(body, stage, optionalText ?: "", true, imageUri.toString()))
            } else {
                Result.failure(Exception("Remote image error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapResponseToDomain(
        dto: TriageResponse,
        stage: IncidentStage,
        snippet: String,
        hasScreenshot: Boolean,
        screenshotUri: String?
    ): InvestigationResult {
        val risk = try {
            RiskLevel.valueOf(dto.riskLevel.uppercase())
        } catch (e: Exception) {
            RiskLevel.SUSPICIOUS
        }

        // Safety enforcement: SENT_MONEY must never be downgraded
        val finalRisk = if (stage == IncidentStage.SENT_MONEY) RiskLevel.CRITICAL else risk

        val actions = dto.prioritizedActions.map {
            PrioritizedAction(
                id = it.id,
                title = it.title,
                description = it.description,
                isUrgent = it.isUrgent
            )
        }

        return InvestigationResult(
            riskLevel = finalRisk,
            headline = dto.headline,
            summary = dto.summary,
            stage = stage,
            rawInputSnippet = snippet.take(150),
            hasScreenshot = hasScreenshot,
            screenshotUri = screenshotUri,
            verifiedObservations = dto.verifiedObservations,
            aiInferences = dto.aiInferences,
            unknowns = dto.unknowns,
            prioritizedActions = actions,
            emergencyTriggered = (finalRisk == RiskLevel.CRITICAL || dto.emergencyTriggered),
            isDemoResult = false,
            analysisSource = AnalysisSource.REMOTE_AI
        )
    }
}
