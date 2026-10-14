package com.example.util

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.data.local.entity.ApprovedFileEntity
import com.example.security.SecurityManager
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class FileReadResult(
    val success: Boolean,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isText: Boolean,
    val textContent: String? = null,
    val base64Content: String? = null,
    val error: String? = null
)

data class ResolvedFileInfo(
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isDirectory: Boolean
)

class StorageAccessHelper(
    private val context: Context,
    private val securityManager: SecurityManager
) {

    fun persistUriPermission(uri: Uri) {
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                (Intent.FLAG_GRANT_WRITE_URI_PERMISSION and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            // Some URIs or providers may only allow read or not support persistable flags
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (ignored: Exception) {}
        }
    }

    fun releaseUriPermission(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (ignored: Exception) {}
    }

    fun resolveFileInfo(uri: Uri, isTreeUri: Boolean = false): ResolvedFileInfo {
        var name = uri.lastPathSegment ?: "unknown_document"
        var size = 0L
        var mime = context.contentResolver.getType(uri) ?: "application/octet-stream"

        if (isTreeUri) {
            return ResolvedFileInfo(
                uriString = uri.toString(),
                displayName = name,
                mimeType = "vnd.android.document/directory",
                sizeBytes = 0L,
                isDirectory = true
            )
        }

        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex) ?: name
                }
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && !it.isNull(sizeIndex)) {
                    size = it.getLong(sizeIndex)
                }
            }
        }

        return ResolvedFileInfo(
            uriString = uri.toString(),
            displayName = name,
            mimeType = mime,
            sizeBytes = size,
            isDirectory = false
        )
    }

    fun readFileSafely(approvedFile: ApprovedFileEntity): FileReadResult {
        val uri = Uri.parse(approvedFile.uriString)

        // 1. Security Check against sensitive names and large sizes
        val securityCheck = securityManager.isFileSafeToExpose(approvedFile.displayName, approvedFile.sizeBytes)
        if (securityCheck is SecurityManager.SecurityCheckResult.Blocked) {
            return FileReadResult(
                success = false,
                fileName = approvedFile.displayName,
                mimeType = approvedFile.mimeType,
                sizeBytes = approvedFile.sizeBytes,
                isText = false,
                error = "Security Policy Violation: ${securityCheck.reason}"
            )
        }

        return try {
            val rawStream = context.contentResolver.openInputStream(uri)
                ?: return FileReadResult(
                    success = false,
                    fileName = approvedFile.displayName,
                    mimeType = approvedFile.mimeType,
                    sizeBytes = approvedFile.sizeBytes,
                    isText = false,
                    error = "Unable to open input stream for URI. File may have been moved or permission revoked."
                )

            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            var totalRead = 0
            val maxBytes = 10 * 1024 * 1024 // 10MB limit

            rawStream.use { stream: InputStream ->
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    totalRead += bytesRead
                    if (totalRead > maxBytes) {
                        return FileReadResult(
                            success = false,
                            fileName = approvedFile.displayName,
                            mimeType = approvedFile.mimeType,
                            sizeBytes = totalRead.toLong(),
                            isText = false,
                            error = "File exceeded maximum allowed safety read limit of 10MB"
                        )
                    }
                    output.write(buffer, 0, bytesRead)
                }
            }

            val bytes = output.toByteArray()
            val mime = approvedFile.mimeType.lowercase()
            val isText = mime.startsWith("text/") ||
                    mime.contains("json") ||
                    mime.contains("xml") ||
                    mime.contains("yaml") ||
                    mime.contains("markdown") ||
                    approvedFile.displayName.endsWith(".txt", ignoreCase = true) ||
                    approvedFile.displayName.endsWith(".md", ignoreCase = true) ||
                    approvedFile.displayName.endsWith(".json", ignoreCase = true) ||
                    approvedFile.displayName.endsWith(".csv", ignoreCase = true)

            if (isText) {
                val text = String(bytes, Charsets.UTF_8)
                FileReadResult(
                    success = true,
                    fileName = approvedFile.displayName,
                    mimeType = approvedFile.mimeType,
                    sizeBytes = bytes.size.toLong(),
                    isText = true,
                    textContent = text
                )
            } else {
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                FileReadResult(
                    success = true,
                    fileName = approvedFile.displayName,
                    mimeType = approvedFile.mimeType,
                    sizeBytes = bytes.size.toLong(),
                    isText = false,
                    base64Content = b64
                )
            }
        } catch (e: Exception) {
            FileReadResult(
                success = false,
                fileName = approvedFile.displayName,
                mimeType = approvedFile.mimeType,
                sizeBytes = approvedFile.sizeBytes,
                isText = false,
                error = "Exception reading file: ${e.message}"
            )
        }
    }
}
