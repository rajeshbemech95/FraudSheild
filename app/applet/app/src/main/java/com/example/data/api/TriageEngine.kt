package com.example.data.api

import com.example.model.AnalysisSource
import com.example.model.IncidentStage
import com.example.model.InvestigationResult
import com.example.model.PrioritizedAction
import com.example.model.RiskLevel

object TriageEngine {

    fun analyze(
        text: String,
        stage: IncidentStage,
        hasScreenshot: Boolean = false,
        screenshotUri: String? = null
    ): InvestigationResult {
        val lowerText = text.lowercase()

        // 1. Mandatory rule: SENT_MONEY immediately triggers CRITICAL emergency
        if (stage == IncidentStage.SENT_MONEY) {
            return InvestigationResult(
                riskLevel = RiskLevel.CRITICAL,
                headline = "Financial Loss Imminent: Immediate Action Required",
                summary = "Money was already transferred or approved. The golden hour window is active to freeze recipient beneficiary accounts.",
                stage = stage,
                rawInputSnippet = text.take(150),
                hasScreenshot = hasScreenshot,
                screenshotUri = screenshotUri,
                verifiedObservations = listOf(
                    "User indicated a financial transaction occurred (SENT_MONEY stage)",
                    "Potential illegal fund routing underway"
                ),
                aiInferences = listOf(
                    "Account freeze probability drops exponentially after initial 2-3 hours"
                ),
                unknowns = listOf(
                    "Recipient bank account/UPI ID details",
                    "Transaction reference number (UTR/Ref)"
                ),
                prioritizedActions = listOf(
                    PrioritizedAction(
                        id = "act_call_1930",
                        title = "Call 1930 Cyber Fraud Helpline",
                        description = "Dial 1930 immediately to report the transaction ID and trigger beneficiary bank hold.",
                        isUrgent = true
                    ),
                    PrioritizedAction(
                        id = "act_call_bank",
                        title = "Contact Bank Fraud Helpline",
                        description = "Request your bank to initiate a recall and freeze the transaction reference.",
                        isUrgent = true
                    ),
                    PrioritizedAction(
                        id = "act_file_cybercrime",
                        title = "File report on cybercrime.gov.in",
                        description = "Register formal acknowledgement on the National Cyber Crime Reporting Portal.",
                        isUrgent = false
                    )
                ),
                emergencyTriggered = true,
                isDemoResult = false,
                analysisSource = AnalysisSource.LOCAL_RULE
            )
        }

        // 2. Stage ENTERED_OTP or credential compromise
        if (stage == IncidentStage.ENTERED_OTP || lowerText.contains("otp") && (lowerText.contains("share") || lowerText.contains("verify"))) {
            return InvestigationResult(
                riskLevel = RiskLevel.HIGH,
                headline = "Potential Account Takeover / Unauthorized Access",
                summary = "Credentials or One-Time Passwords may have been compromised. Unauthorized sessions or debit attempts may occur.",
                stage = stage,
                rawInputSnippet = text.take(150),
                hasScreenshot = hasScreenshot,
                screenshotUri = screenshotUri,
                verifiedObservations = listOf(
                    "User reported entering OTP or credentials",
                    "High susceptibility to credential harvesting"
                ),
                aiInferences = listOf(
                    "Perpetrator attempting SIM swap, UPI linking, or net banking takeover"
                ),
                unknowns = listOf(
                    "Whether subsequent 2FA requests were triggered"
                ),
                prioritizedActions = listOf(
                    PrioritizedAction(
                        id = "act_change_passwords",
                        title = "Change Banking & Account Passwords",
                        description = "Immediately update passwords on an alternate verified device.",
                        isUrgent = true
                    ),
                    PrioritizedAction(
                        id = "act_block_cards",
                        title = "Temporarily Block Cards / Netbanking",
                        description = "Toggle card lock or internet transactions via your bank's official app.",
                        isUrgent = true
                    )
                ),
                emergencyTriggered = false,
                isDemoResult = false,
                analysisSource = AnalysisSource.LOCAL_RULE
            )
        }

        // 3. Phishing keywords detection
        val isImpersonation = lowerText.contains("customs") || lowerText.contains("police") ||
                lowerText.contains("cbi") || lowerText.contains("arrest") || lowerText.contains("fedex")
        val isElectricity = lowerText.contains("electricity") || lowerText.contains("power") ||
                lowerText.contains("disconnected") || lowerText.contains("bill")
        val isLotteryOrJob = lowerText.contains("winner") || lowerText.contains("lottery") ||
                lowerText.contains("part-time job") || lowerText.contains("telegram task")

        val riskLevel = when {
            isImpersonation -> RiskLevel.CRITICAL
            isElectricity -> RiskLevel.HIGH
            isLotteryOrJob -> RiskLevel.SUSPICIOUS
            stage == IncidentStage.CLICKED_LINK -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.LOW
        }

        val headline = when {
            isImpersonation -> "Law Enforcement / Delivery Impersonation Scam Detected"
            isElectricity -> "Utility Disconnection Phishing Pattern"
            isLotteryOrJob -> "Unsolicited Reward / Task Employment Scam"
            stage == IncidentStage.CLICKED_LINK -> "Suspicious Link Tapped"
            else -> "Routine Message Analysis Completed"
        }

        val summary = when {
            isImpersonation -> "The message mimics government, law enforcement, or courier officials using urgency and coercion to extort funds."
            isElectricity -> "Fake utility notices threaten immediate disconnection to force downloading malicious APKs or making payments."
            isLotteryOrJob -> "Promises of unrealistic returns or task-based earnings intended to solicit initial deposit sums."
            else -> "No definitive high-threat signatures found, but caution is recommended before sharing any personal data."
        }

        return InvestigationResult(
            riskLevel = riskLevel,
            headline = headline,
            summary = summary,
            stage = stage,
            rawInputSnippet = text.take(150),
            hasScreenshot = hasScreenshot,
            screenshotUri = screenshotUri,
            verifiedObservations = listOf(
                "Evaluated input text with local deterministic pattern matcher",
                "Verified stage: ${stage.label}"
            ),
            aiInferences = listOf(
                if (riskLevel >= RiskLevel.HIGH) "Pattern matches recognized threat campaign" else "Low indicator correlation"
            ),
            unknowns = listOf("Authenticity of sender phone number/email"),
            prioritizedActions = listOf(
                PrioritizedAction(
                    id = "act_do_not_pay",
                    title = "Do Not Transfer Funds or Share OTP",
                    description = "Legitimate agencies never demand immediate wire or UPI transfers over phone/chat.",
                    isUrgent = riskLevel >= RiskLevel.HIGH
                ),
                PrioritizedAction(
                    id = "act_verify_official",
                    title = "Verify via Official Channels Only",
                    description = "Search for official contact numbers independently; do not dial numbers in the message.",
                    isUrgent = false
                )
            ),
            emergencyTriggered = (riskLevel == RiskLevel.CRITICAL),
            isDemoResult = false,
            analysisSource = AnalysisSource.LOCAL_RULE
        )
    }
}
