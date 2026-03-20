package com.sanship.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BackupRestoreScreen() {
    val scope = rememberCoroutineScope()
    var backupDir by remember { mutableStateOf(System.getProperty("user.home") + "/Downloads/Sanship/Backups") }
    var restoreFile by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun showMessage(msg: String, error: Boolean = false) {
        message = msg
        isError = error
    }

    fun backupDatabase() {
        scope.launch {
            try {
                val dbFile = File("sanship.db")
                if (!dbFile.exists()) {
                    showMessage("Database file 'sanship.db' not found!", true)
                    return@launch
                }

                val dir = File(backupDir)
                if (!dir.exists()) dir.mkdirs()

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                val destFile = File(dir, "sanship_backup_$timestamp.db")
                
                dbFile.copyTo(destFile, overwrite = true)
                showMessage("Backup successful: ${destFile.absolutePath}", false)
            } catch (e: Exception) {
                showMessage("Backup failed: ${e.message}", true)
            }
        }
    }

    fun restoreDatabase() {
        scope.launch {
            try {
                val srcFile = File(restoreFile)
                if (!srcFile.exists() || !srcFile.isFile || !srcFile.name.endsWith(".db")) {
                    showMessage("Invalid backup file selected.", true)
                    return@launch
                }

                val destFile = File("sanship.db")
                srcFile.copyTo(destFile, overwrite = true)
                
                showMessage("Restore successful from: ${srcFile.name}. Please restart the application.", false)
            } catch (e: Exception) {
                showMessage("Restore failed: ${e.message}", true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)) {
        Text("Backup & Restore", style = MaterialTheme.typography.h4)
        Spacer(Modifier.height(24.dp))

        if (message.isNotBlank()) {
            Text(
                text = message,
                color = if (isError) MaterialTheme.colors.error else Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // --- BACKUP ---
        Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Create Backup", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.h6)
                Spacer(Modifier.height(8.dp))
                Text("Save a copy of the current database.", color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = backupDir,
                    onValueChange = { backupDir = it },
                    label = { Text("Backup Directory Path") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { backupDatabase() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1976D2), contentColor = Color.White)
                ) {
                    Text("Backup Now")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- RESTORE ---
        Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Restore Backup", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.h6)
                Spacer(Modifier.height(8.dp))
                Text("Replace the current database with a backup file. WARNING: Current data will be lost!", color = MaterialTheme.colors.error)
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = restoreFile,
                    onValueChange = { restoreFile = it },
                    label = { Text("Full Path to Backup File (e.g., C:/Backups/data_backup.db)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { restoreDatabase() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error, contentColor = Color.White)
                ) {
                    Text("Restore Database")
                }
            }
        }
    }
}
