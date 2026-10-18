package com.example.data.repository

import com.example.model.AnalysisSource
import com.example.model.IncidentStage
import com.example.model.InvestigationResult
import com.example.model.PrioritizedAction
import com.example.model.RiskLevel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreCaseRepository(
    private val firestore: FirebaseFirestore = try {
        FirebaseFirestore.getInstance("ai-studio-android-fraudshi-8e7fb439-1613-40af-a6e1-158a0ea67553")
    } catch (e: Exception) {
        FirebaseFirestore.getInstance()
    }
) {

    fun observeCases(userId: String): Flow<List<InvestigationResult>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener: ListenerRegistration = firestore
            .collection("users")
            .document(userId)
            .collection("cases")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val cases = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        try {
                            val riskStr = data["riskLevel"] as? String ?: "LOW"
                            val stageStr = data["stage"] as? String ?: "RECEIVED"
                            val srcStr = data["analysisSource"] as? String ?: "LOCAL_RULE"

                            @Suppress("UNCHECKED_CAST")
                            val rawActions = data["prioritizedActions"] as? List<Map<String, Any>> ?: emptyList()
                            val actions = rawActions.map { map ->
                                PrioritizedAction(
                                    id = map["id"] as? String ?: "",
                                    title = map["title"] as? String ?: "",
                                    description = map["description"] as? String ?: "",
                                    isUrgent = map["isUrgent"] as? Boolean ?: false,
                                    isCompleted = map["isCompleted"] as? Boolean ?: false
                                )
                            }

                            @Suppress("UNCHECKED_CAST")
                            val observations = data["verifiedObservations"] as? List<String> ?: emptyList()
                            @Suppress("UNCHECKED_CAST")
                            val inferences = data["aiInferences"] as? List<String> ?: emptyList()
                            @Suppress("UNCHECKED_CAST")
                            val unknowns = data["unknowns"] as? List<String> ?: emptyList()

                            InvestigationResult(
                                firestoreId = doc.id,
                                riskLevel = try { RiskLevel.valueOf(riskStr) } catch (e: Exception) { RiskLevel.LOW },
                                headline = data["headline"] as? String ?: "",
                                summary = data["summary"] as? String ?: "",
                                stage = try { IncidentStage.valueOf(stageStr) } catch (e: Exception) { IncidentStage.RECEIVED },
                                rawInputSnippet = data["rawInputSnippet"] as? String ?: "",
                                hasScreenshot = data["hasScreenshot"] as? Boolean ?: false,
                                screenshotUri = data["screenshotUri"] as? String,
                                verifiedObservations = observations,
                                aiInferences = inferences,
                                unknowns = unknowns,
                                prioritizedActions = actions,
                                emergencyTriggered = data["emergencyTriggered"] as? Boolean ?: false,
                                isDemoResult = data["isDemoResult"] as? Boolean ?: false,
                                analysisSource = try { AnalysisSource.valueOf(srcStr) } catch (e: Exception) { AnalysisSource.LOCAL_RULE },
                                userNotes = data["userNotes"] as? String ?: "",
                                timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    trySend(cases)
                }
            }

        awaitClose { listener.remove() }
    }

    suspend fun saveCase(userId: String, result: InvestigationResult): String {
        val actionsPayload = result.prioritizedActions.map {
            mapOf(
                "id" to it.id,
                "title" to it.title,
                "description" to it.description,
                "isUrgent" to it.isUrgent,
                "isCompleted" to it.isCompleted
            )
        }

        val caseMap = hashMapOf<String, Any>(
            "userId" to userId,
            "riskLevel" to result.riskLevel.name,
            "headline" to result.headline,
            "summary" to result.summary,
            "stage" to result.stage.name,
            "rawInputSnippet" to result.rawInputSnippet,
            "hasScreenshot" to result.hasScreenshot,
            "verifiedObservations" to result.verifiedObservations,
            "aiInferences" to result.aiInferences,
            "unknowns" to result.unknowns,
            "prioritizedActions" to actionsPayload,
            "emergencyTriggered" to result.emergencyTriggered,
            "isDemoResult" to result.isDemoResult,
            "analysisSource" to result.analysisSource.name,
            "userNotes" to result.userNotes,
            "timestamp" to result.timestamp
        )
        result.screenshotUri?.let { caseMap["screenshotUri"] = it }

        val ref = if (result.firestoreId.isNotBlank()) {
            firestore.collection("users").document(userId).collection("cases").document(result.firestoreId)
        } else {
            firestore.collection("users").document(userId).collection("cases").document()
        }

        ref.set(caseMap).await()
        return ref.id
    }

    suspend fun updateCaseNotes(userId: String, caseDocId: String, notes: String) {
        if (caseDocId.isBlank()) return
        firestore.collection("users")
            .document(userId)
            .collection("cases")
            .document(caseDocId)
            .update("userNotes", notes)
            .await()
    }

    suspend fun toggleActionCompletion(
        userId: String,
        caseDocId: String,
        actionId: String,
        actions: List<PrioritizedAction>
    ) {
        if (caseDocId.isBlank()) return
        val actionsPayload = actions.map {
            mapOf(
                "id" to it.id,
                "title" to it.title,
                "description" to it.description,
                "isUrgent" to it.isUrgent,
                "isCompleted" to if (it.id == actionId) !it.isCompleted else it.isCompleted
            )
        }
        firestore.collection("users")
            .document(userId)
            .collection("cases")
            .document(caseDocId)
            .update("prioritizedActions", actionsPayload)
            .await()
    }

    suspend fun deleteCase(userId: String, caseDocId: String, localId: Long) {
        if (caseDocId.isNotBlank()) {
            firestore.collection("users")
                .document(userId)
                .collection("cases")
                .document(caseDocId)
                .delete()
                .await()
        }
    }

    suspend fun deleteAllCases(userId: String) {
        val snapshot = firestore.collection("users").document(userId).collection("cases").get().await()
        val batch = firestore.batch()
        snapshot.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }
}
