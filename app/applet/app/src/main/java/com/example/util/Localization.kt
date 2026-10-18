package com.example.util

import com.example.model.AppLanguage

object Localization {
    private val dictionary: Map<String, Map<AppLanguage, String>> = mapOf(
        "call_1930_button" to mapOf(
            AppLanguage.ENGLISH to "Call 1930",
            AppLanguage.TAMIL to "1930 அழைக்க",
            AppLanguage.HINDI to "1930 पर कॉल करें"
        ),
        "emergency_headline" to mapOf(
            AppLanguage.ENGLISH to "Emergency Response Activated",
            AppLanguage.TAMIL to "அவசர உதவி செயல்படுத்தப்பட்டது",
            AppLanguage.HINDI to "आपातकालीन प्रतिक्रिया सक्रिय"
        ),
        "app_title" to mapOf(
            AppLanguage.ENGLISH to "FraudShield",
            AppLanguage.TAMIL to "ஃபிராடுஷீல்ட்",
            AppLanguage.HINDI to "फ्रॉडशील्ड"
        )
    )

    fun tr(key: String, language: AppLanguage): String {
        return dictionary[key]?.get(language) ?: dictionary[key]?.get(AppLanguage.ENGLISH) ?: key
    }
}
