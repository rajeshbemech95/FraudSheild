package com.example.data.local

import com.example.data.repository.FirestoreCaseRepository
import com.example.model.InvestigationResult
import com.example.model.PrioritizedAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CaseRepository(
    private val caseDao: CaseDao,
    private val firestoreRepo: FirestoreCaseRepository? = null
) {

    val localCases: Flow<List<InvestigationResult>> = caseDao.getAllCases().map { entities ->
        entities.map { it.toDomain() }
    }

    fun observeCloudCases(userId: String): Flow<List<InvestigationResult>>? {
        if (userId.isBlank() || firestoreRepo == null) return null
        return firestoreRepo.observeCases(userId)
    }

    suspend fun saveCase(result: InvestigationResult, userId: String? = null): InvestigationResult {
        var cloudDocId = result.firestoreId
        if (!userId.isNullOrBlank() && firestoreRepo != null) {
            try {
                cloudDocId = firestoreRepo.saveCase(userId, result)
            } catch (e: Exception) {
                // If offline, save locally
            }
        }

        val updatedResult = result.copy(firestoreId = cloudDocId)
        val insertedLocalId = caseDao.insertCase(CaseEntity.fromDomain(updatedResult))
        return updatedResult.copy(id = insertedLocalId)
    }

    suspend fun updateCase(result: InvestigationResult, userId: String? = null) {
        if (!userId.isNullOrBlank() && result.firestoreId.isNotBlank() && firestoreRepo != null) {
            try {
                firestoreRepo.updateCaseNotes(userId, result.firestoreId, result.userNotes)
            } catch (e: Exception) {
                // fallback
            }
        }
        caseDao.updateCase(CaseEntity.fromDomain(result))
    }

    suspend fun toggleActionCompletion(
        case: InvestigationResult,
        actionId: String,
        userId: String? = null
    ): InvestigationResult {
        val updatedActions = case.prioritizedActions.map {
            if (it.id == actionId) it.copy(isCompleted = !it.isCompleted) else it
        }
        val updatedResult = case.copy(prioritizedActions = updatedActions)

        if (!userId.isNullOrBlank() && case.firestoreId.isNotBlank() && firestoreRepo != null) {
            try {
                firestoreRepo.toggleActionCompletion(userId, case.firestoreId, actionId, case.prioritizedActions)
            } catch (e: Exception) {
                // fallback
            }
        }

        caseDao.updateCase(CaseEntity.fromDomain(updatedResult))
        return updatedResult
    }

    suspend fun deleteCase(case: InvestigationResult, userId: String? = null) {
        if (!userId.isNullOrBlank() && case.firestoreId.isNotBlank() && firestoreRepo != null) {
            try {
                firestoreRepo.deleteCase(userId, case.firestoreId, case.id)
            } catch (e: Exception) {
                // fallback
            }
        }
        if (case.id > 0) {
            caseDao.deleteCaseById(case.id)
        }
    }

    suspend fun deleteAllCases(userId: String? = null) {
        if (!userId.isNullOrBlank() && firestoreRepo != null) {
            try {
                firestoreRepo.deleteAllCases(userId)
            } catch (e: Exception) {
                // fallback
            }
        }
        caseDao.deleteAllCases()
    }
}
