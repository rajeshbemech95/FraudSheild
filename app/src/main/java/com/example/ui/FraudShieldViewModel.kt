package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.BackendStatus
import com.example.data.api.TriageApiClient
import com.example.data.api.TriageEngine
import com.example.data.local.CaseRepository
import com.example.data.local.FraudDatabase
import com.example.data.repository.FirebaseAiInvestigationRepository
import com.example.data.repository.FirestoreCaseRepository
import com.example.model.AnalysisSource
import com.example.model.AppLanguage
import com.example.model.AuthUser
import com.example.model.IncidentStage
import com.example.model.InvestigationResult
import com.example.model.PrioritizedAction
import com.example.model.RiskLevel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FraudShieldViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private val firestoreRepo = FirestoreCaseRepository()
    private val caseDao = FraudDatabase.getDatabase(application).caseDao()
    val caseRepository = CaseRepository(caseDao, firestoreRepo)

    val triageApiClient = TriageApiClient()
    val aiRepo = FirebaseAiInvestigationRepository()

    // Auth State
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // Investigation Flow
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedStage = MutableStateFlow(IncidentStage.RECEIVED)
    val selectedStage: StateFlow<IncidentStage> = _selectedStage.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    val selectedLanguage: StateFlow<AppLanguage> = _selectedLanguage.asStateFlow()

    private val _isInvestigating = MutableStateFlow(false)
    val isInvestigating: StateFlow<Boolean> = _isInvestigating.asStateFlow()

    private val _activeResult = MutableStateFlow<InvestigationResult?>(null)
    val activeResult: StateFlow<InvestigationResult?> = _activeResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _backendStatus = MutableStateFlow<BackendStatus>(BackendStatus.Idle)
    val backendStatus: StateFlow<BackendStatus> = _backendStatus.asStateFlow()

    // History: reactive cases (switches between cloud and local)
    val cases: StateFlow<List<InvestigationResult>> = _currentUser.flatMapLatest { user ->
        if (user != null && user.uid.isNotBlank()) {
            caseRepository.observeCloudCases(user.uid) ?: caseRepository.localCases
        } else {
            caseRepository.localCases
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        val fbUser = auth.currentUser
        if (fbUser != null) {
            _currentUser.value = AuthUser(fbUser.uid, fbUser.email, fbUser.displayName)
        }
    }

    fun onInputTextChanged(text: String) { _inputText.value = text }
    fun onStageSelected(stage: IncidentStage) { _selectedStage.value = stage }
    fun onImageSelected(uri: Uri?) { _selectedImageUri.value = uri }
    fun onLanguageSelected(lang: AppLanguage) { _selectedLanguage.value = lang }
    fun clearActiveResult() { _activeResult.value = null }
    fun clearError() { _errorMessage.value = null; _authError.value = null }

    fun investigate(context: Context) {
        val text = _inputText.value.trim()
        val imageUri = _selectedImageUri.value
        val stage = _selectedStage.value
        val lang = _selectedLanguage.value

        if (text.isBlank() && imageUri == null) {
            _errorMessage.value = "Please enter text or select a screenshot to analyze."
            return
        }

        _isInvestigating.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val result = aiRepo.investigate(
                    context = context,
                    text = text,
                    stage = stage,
                    imageUri = imageUri,
                    language = lang.code
                )
                _activeResult.value = result

                // Persist result automatically
                val saved = caseRepository.saveCase(result, _currentUser.value?.uid)
                _activeResult.value = saved
            } catch (e: Exception) {
                // Fallback to local rule engine
                val fallbackResult = TriageEngine.analyze(
                    text = text,
                    stage = stage,
                    hasScreenshot = imageUri != null,
                    screenshotUri = imageUri?.toString()
                ).copy(analysisSource = AnalysisSource.OFFLINE_FALLBACK)

                _activeResult.value = fallbackResult
                caseRepository.saveCase(fallbackResult, _currentUser.value?.uid)
            } finally {
                _isInvestigating.value = false
            }
        }
    }

    fun toggleActionCompletion(actionId: String) {
        val current = _activeResult.value ?: return
        viewModelScope.launch {
            val updated = caseRepository.toggleActionCompletion(current, actionId, _currentUser.value?.uid)
            _activeResult.value = updated
        }
    }

    fun saveNotes(notes: String) {
        val current = _activeResult.value ?: return
        viewModelScope.launch {
            val updated = current.copy(userNotes = notes)
            caseRepository.updateCase(updated, _currentUser.value?.uid)
            _activeResult.value = updated
        }
    }

    fun deleteCase(case: InvestigationResult) {
        viewModelScope.launch {
            caseRepository.deleteCase(case, _currentUser.value?.uid)
            if (_activeResult.value?.id == case.id) {
                _activeResult.value = null
            }
        }
    }

    fun deleteAllCases() {
        viewModelScope.launch {
            caseRepository.deleteAllCases(_currentUser.value?.uid)
            _activeResult.value = null
        }
    }

    fun selectCaseForDetail(case: InvestigationResult) {
        _activeResult.value = case
    }

    fun testDemoScenario(title: String, sampleText: String, stage: IncidentStage) {
        _inputText.value = sampleText
        _selectedStage.value = stage
        _selectedImageUri.value = null
        val result = TriageEngine.analyze(sampleText, stage).copy(
            headline = title,
            isDemoResult = true,
            analysisSource = AnalysisSource.DEMO
        )
        _activeResult.value = result
        viewModelScope.launch {
            val saved = caseRepository.saveCase(result, _currentUser.value?.uid)
            _activeResult.value = saved
        }
    }

    fun signInWithGoogle(activity: Activity) {
        _isAuthLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(activity)
                val clientId = "604840034883-u95ohou89e2icmggef64g22ne3m4i34p.apps.googleusercontent.com"
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        _currentUser.value = AuthUser(user.uid, user.email, user.displayName)
                    }
                }
            } catch (e: GetCredentialCancellationException) {
                // User cancelled or dismissed picker
            } catch (e: Exception) {
                _authError.value = "Google Play Services on emulator requires account setup: ${e.localizedMessage ?: "No account found"}"
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun signInAsUser(email: String, name: String) {
        val safeUid = "user_" + kotlin.math.abs(email.hashCode())
        _currentUser.value = AuthUser(safeUid, email, name)
        _authError.value = null
    }

    fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            // Ignored
        }
        _currentUser.value = null
    }

    fun setAuthenticatedUser(uid: String, email: String?, name: String?) {
        _currentUser.value = AuthUser(uid, email, name)
    }

    fun checkBackendHealth() {
        _backendStatus.value = BackendStatus.Checking
        viewModelScope.launch {
            _backendStatus.value = triageApiClient.checkBackendHealth()
        }
    }
}
