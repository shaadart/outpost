package com.bitchat.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(onComplete: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B0F14)) {
        Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Text(
                    text = "Outpost",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3B82F6)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                    text = "Decentralized Off-Grid Messaging",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                    text = "What should people call you?",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFF3F4F6),
                    fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isError = false
                    },
                    placeholder = { Text("Your display name", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    isError = isError,
                    supportingText =
                            if (isError) {
                                { Text("Name cannot be empty", color = Color(0xFFEF5350)) }
                            } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions =
                            KeyboardActions(
                                    onDone = {
                                        if (name.isNotBlank()) onComplete(name.trim())
                                        else isError = true
                                    }
                            ),
                    colors =
                            OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFFF3F4F6),
                                    unfocusedTextColor = Color(0xFFF3F4F6),
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFF131A22),
                                    cursorColor = Color(0xFF3B82F6),
                                    focusedContainerColor = Color(0xFF131A22),
                                    unfocusedContainerColor = Color(0xFF131A22)
                            ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                    onClick = {
                        if (name.isNotBlank()) onComplete(name.trim()) else isError = true
                    },
                    enabled = name.isNotBlank(),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF3B82F6),
                                    contentColor = Color.Black,
                                    disabledContainerColor = Color(0xFF131A22),
                                    disabledContentColor = Color(0xFF94A3B8)
                            ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(text = "Continue", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
        }
    }
}
