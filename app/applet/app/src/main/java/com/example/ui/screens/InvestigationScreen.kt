package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AppLanguage
import com.example.model.IncidentStage
import com.example.ui.FraudShieldViewModel
import com.example.util.Localization

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvestigationScreen(
    viewModel: FraudShieldViewModel,
    onNavigateEmergency: () -> Unit,
    onResultAvailable: () -> Unit
) {
    val context = LocalContext.current
    val inputText by viewModel.inputText.collectAsState()
    val selectedStage by viewModel.selectedStage.collectAsState()
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val isInvestigating by viewModel.isInvestigating.collectAsState()
    val activeResult by viewModel.activeResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        viewModel.onImageSelected(uri)
    }

    if (activeResult != null) {
        onResultAvailable()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Emergency Callout Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emergency_banner"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFDC2626), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Emergency,
                            contentDescription = "Emergency",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Lost Money in Fraud?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Call Cyber Helpline 1930 immediately",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                Button(
                    onClick = onNavigateEmergency,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("emergency_quick_call_btn")
                ) {
                    Text(
                        Localization.tr("call_1930_button", selectedLanguage),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Language Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "App Language:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.values().forEach { lang ->
                    FilterChip(
                        selected = (selectedLanguage == lang),
                        onClick = { viewModel.onLanguageSelected(lang) },
                        label = { Text(lang.displayName) }
                    )
                }
            }
        }

        // Incident Stage Selection
        Text(
            "Incident Progress Stage:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IncidentStage.values().forEach { stage ->
                val isSelected = (selectedStage == stage)
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.onStageSelected(stage) },
                    label = {
                        Text(
                            stage.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (stage == IncidentStage.SENT_MONEY) Color(0xFFDC2626) else MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = if (stage == IncidentStage.SENT_MONEY) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Message Input Box
        OutlinedTextField(
            value = inputText,
            onValueChange = { viewModel.onInputTextChanged(it) },
            label = { Text("Paste suspicious SMS, WhatsApp, or email text") },
            placeholder = { Text("e.g. Electricity bill unpaid, electricity will be disconnected tonight. Call officer at...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .testTag("investigation_input_field"),
            shape = RoundedCornerShape(12.dp),
            maxLines = 6
        )

        // Screenshot Attachment Box
        if (selectedImageUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = "Selected Screenshot",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = { viewModel.onImageSelected(null) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove Screenshot", tint = Color.White)
                }
            }
        } else {
            OutlinedButton(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("attach_screenshot_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Attach Screenshot for Multimodal Inspection")
            }
        }

        // Error message if any
        if (errorMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Action Button
        Button(
            onClick = { viewModel.investigate(context) },
            enabled = !isInvestigating && (inputText.isNotBlank() || selectedImageUri != null),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("analyze_threat_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isInvestigating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Investigating Threat Intelligence...", fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Investigate Threat", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        // Demo Presets
        Text(
            "Demo Threat Scenarios:",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    viewModel.testDemoScenario(
                        title = "Customs Digital Arrest Scam",
                        sampleText = "FedEx notice: Your package has illegal contraband narcotics. Mumbai cyber crime department issued arrest warrant. Transfer penalty to verify.",
                        stage = IncidentStage.RECEIVED
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Customs Arrest", fontSize = 11.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    viewModel.testDemoScenario(
                        title = "Electricity Disconnection Phishing",
                        sampleText = "Dear consumer, electricity will be disconnected tonight at 9:30 PM due to unpaid bill update. Contact power officer immediately.",
                        stage = IncidentStage.CLICKED_LINK
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Electricity Bill", fontSize = 11.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    viewModel.testDemoScenario(
                        title = "Fund Transfer Emergency Recovery",
                        sampleText = "I transferred 15,000 via UPI on WhatsApp instructions for job deposit.",
                        stage = IncidentStage.SENT_MONEY
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Transferred Money", fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
