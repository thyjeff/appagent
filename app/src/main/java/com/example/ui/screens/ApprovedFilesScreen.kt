package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ApprovedFileEntity
import com.example.ui.FocusPilotViewModel
import com.example.util.FileReadResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ApprovedFilesScreen(viewModel: FocusPilotViewModel) {
    val context = LocalContext.current
    val approvedFiles by viewModel.approvedFiles.collectAsState()

    var previewResult by remember { mutableStateOf<FileReadResult?>(null) }
    var fileToRevoke by remember { mutableStateOf<ApprovedFileEntity?>(null) }

    // SAF Document Picker
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                viewModel.approveFileUri(uri, isDirectory = false)
                Toast.makeText(context, "Document approved for Hermes access", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // SAF Directory Picker
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                viewModel.approveFileUri(uri, isDirectory = true)
                Toast.makeText(context, "Directory approved for Hermes access", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("approve_file_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Approve File", fontSize = 13.sp)
            }

            FilledTonalButton(
                onClick = { openDocumentTreeLauncher.launch(null) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("approve_folder_button")
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Approve Folder", fontSize = 13.sp)
            }
        }

        // Security notice banner
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Storage Access Framework enforces explicit consent. Sensitive tokens, keys (.pem, .env, id_rsa), and files >10MB are blocked by policy.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "Permitted Locations (${approvedFiles.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        if (approvedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = "No files or folders approved yet",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap '+ Approve File' to grant Hermes read access to specific notes, checklists, or project documents.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(approvedFiles, key = { it.id }) { file ->
                    ApprovedFileItemCard(
                        file = file,
                        onInspect = {
                            viewModel.readFileContent(file) { result ->
                                previewResult = result
                            }
                        },
                        onRevoke = { fileToRevoke = file }
                    )
                }
            }
        }
    }

    // Inspect preview modal
    previewResult?.let { result ->
        AlertDialog(
            onDismissRequest = { previewResult = null },
            title = {
                Text(
                    text = if (result.success) "Preview: ${result.fileName}" else "Inspection Blocked",
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (result.success) {
                        Text(
                            text = "MIME: ${result.mimeType} | Size: ${result.sizeBytes} bytes",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (result.isText) {
                            Text(
                                text = result.textContent ?: "(empty)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        } else {
                            Text(
                                text = "Binary File [Base64 Preview: ${(result.base64Content ?: "").take(200)}...]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        Text(
                            text = result.error ?: "Security inspection denied reading this file.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { previewResult = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Revoke Confirmation Dialog
    fileToRevoke?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToRevoke = null },
            title = { Text("Revoke Access?") },
            text = { Text("Revoking access will remove persistable URI permissions for '${file.displayName}'. Hermes will no longer be able to read this file.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.revokeApprovedFile(file)
                        fileToRevoke = null
                        Toast.makeText(context, "Permission revoked", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Revoke")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRevoke = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ApprovedFileItemCard(
    file: ApprovedFileEntity,
    onInspect: () -> Unit,
    onRevoke: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    contentDescription = null,
                    tint = if (file.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = file.displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (file.isDirectory) "Directory Location" else formatFileSize(file.sizeBytes),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row {
                IconButton(onClick = onInspect, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = "Inspect File",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onRevoke, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Revoke Permission",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
