package com.example.data.local

import androidx.room.TypeConverter
import com.example.model.AnalysisSource
import com.example.model.IncidentStage
import com.example.model.PrioritizedAction
import com.example.model.RiskLevel
import org.json.JSONArray
import org.json.JSONObject

class CaseConverters {

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        if (list == null) return "[]"
        val array = JSONArray()
        list.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<String>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list
    }

    @TypeConverter
    fun fromActionList(actions: List<PrioritizedAction>?): String {
        if (actions == null) return "[]"
        val array = JSONArray()
        actions.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("title", it.title)
            obj.put("description", it.description)
            obj.put("isUrgent", it.isUrgent)
            obj.put("isCompleted", it.isCompleted)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toActionList(json: String?): List<PrioritizedAction> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<PrioritizedAction>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                PrioritizedAction(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    description = obj.optString("description", ""),
                    isUrgent = obj.optBoolean("isUrgent", false),
                    isCompleted = obj.optBoolean("isCompleted", false)
                )
            )
        }
        return list
    }

    @TypeConverter
    fun fromRiskLevel(risk: RiskLevel?): String = risk?.name ?: RiskLevel.LOW.name

    @TypeConverter
    fun toRiskLevel(name: String?): RiskLevel = try {
        RiskLevel.valueOf(name ?: RiskLevel.LOW.name)
    } catch (e: Exception) {
        RiskLevel.LOW
    }

    @TypeConverter
    fun fromIncidentStage(stage: IncidentStage?): String = stage?.name ?: IncidentStage.RECEIVED.name

    @TypeConverter
    fun toIncidentStage(name: String?): IncidentStage = try {
        IncidentStage.valueOf(name ?: IncidentStage.RECEIVED.name)
    } catch (e: Exception) {
        IncidentStage.RECEIVED
    }

    @TypeConverter
    fun fromAnalysisSource(src: AnalysisSource?): String = src?.name ?: AnalysisSource.LOCAL_RULE.name

    @TypeConverter
    fun toAnalysisSource(name: String?): AnalysisSource = try {
        AnalysisSource.valueOf(name ?: AnalysisSource.LOCAL_RULE.name)
    } catch (e: Exception) {
        AnalysisSource.LOCAL_RULE
    }
}
