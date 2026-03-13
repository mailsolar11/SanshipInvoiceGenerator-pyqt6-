package com.sanship.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sanship.repositories.UserRepository

@Composable
fun LoginScreen(onLoginSuccess: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFEEEEEE)),
        contentAlignment = Alignment.Center
    ) {
        Card(elevation = 8.dp, modifier = Modifier.width(400.dp)) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Sanship Login", style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(32.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = MaterialTheme.colors.error)
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        if (UserRepository.authenticate(username, password)) {
                            errorMsg = ""
                            val role = UserRepository.getRole(username)
                            onLoginSuccess(username, role)
                        } else {
                            errorMsg = "Invalid credentials"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Login")
                }
                
                Spacer(Modifier.height(16.dp))
                Text("Admin: admin/admin123 | User: user/user123", color = Color.Gray, style = MaterialTheme.typography.caption)
            }
        }
    }
}
