package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.TriageActionDto
import com.example.data.api.TriageApiClient
import com.example.data.api.TriageApiService
import com.example.data.api.TriageEngine
import com.example.data.api.TriageRequest
import com.example.data.api.TriageResponse
import com.example.data.local.CaseConverters
import com.example.data.local.CaseEntity
import com.example.model.AnalysisSource
import com.example.model.AppLanguage
import com.example.model.IncidentStage
import com.example.model.InvestigationResult
import com.example.model.PrioritizedAction
import com.example.model.RiskLevel
import com.example.util.Localization
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FraudShield", appName)
    }

    @Test
    fun `verify sent money stage immediately triggers critical risk and emergency actions`() {
        val result = TriageEngine.analyze(
            text = "Sent 10,000 via PhonePe for customs clearance",
            stage = IncidentStage.SENT_MONEY
        )

        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
        assertTrue(result.emergencyTriggered)
        assertTrue(result.prioritizedActions.isNotEmpty())
        assertEquals("act_call_1930", result.prioritizedActions.first().id)
        assertTrue(result.prioritizedActions.first().isUrgent)
    }

    @Test
    fun `verify case converters serializes and deserializes correctly`() {
        val converters = CaseConverters()
        val actions = listOf(
            PrioritizedAction(id = "1", title = "Block card", description = "Immediate", isUrgent = true),
            PrioritizedAction(id = "2", title = "Report cybercrime", description = "Next step", isUrgent = false)
        )

        val json = converters.fromActionList(actions)
        val deserialized = converters.toActionList(json)

        assertEquals(2, deserialized.size)
        assertEquals("1", deserialized[0].id)
        assertTrue(deserialized[0].isUrgent)
        assertEquals("2", deserialized[1].id)
        assertFalse(deserialized[1].isUrgent)
    }

    @Test
    fun `verify localization dictionary supports english, tamil, and hindi`() {
        assertEquals("Call 1930", Localization.tr("call_1930_button", AppLanguage.ENGLISH))
        assertEquals("1930 அழைக்க", Localization.tr("call_1930_button", AppLanguage.TAMIL))
        assertEquals("1930 पर कॉल करें", Localization.tr("call_1930_button", AppLanguage.HINDI))
    }

    @Test
    fun `verify case entity preserves analysis source mapping`() {
        val original = CaseEntity(
            riskLevel = RiskLevel.CRITICAL,
            headline = "Electricity Bill Urgent",
            summary = "Phishing APK message detected",
            stage = IncidentStage.RECEIVED,
            rawInputSnippet = "Dear consumer your power will be disconnected",
            hasScreenshot = false,
            screenshotUri = null,
            verifiedObservations = listOf("Urgent disconnected notice"),
            aiInferences = listOf("Electricity power scam signature"),
            unknowns = emptyList(),
            prioritizedActions = listOf(
                PrioritizedAction(id = "a1", title = "Do not pay", description = "", isUrgent = true)
            ),
            emergencyTriggered = false,
            isDemoResult = false,
            analysisSource = AnalysisSource.REMOTE_AI,
            userNotes = "",
            timestamp = 1000L
        )

        val domain = original.toDomain()
        assertEquals(AnalysisSource.REMOTE_AI, domain.analysisSource)
        assertFalse(domain.isDemoResult)

        val reconstructed = CaseEntity.fromDomain(domain)
        assertEquals(AnalysisSource.REMOTE_AI, reconstructed.analysisSource)
        assertFalse(reconstructed.isDemoResult)
    }

    @Test
    fun `verify entered otp stage flags high risk`() {
        val result = TriageEngine.analyze(
            text = "I shared OTP 482910 on the call",
            stage = IncidentStage.ENTERED_OTP
        )

        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.prioritizedActions.any { it.id == "act_change_passwords" })
    }
}
