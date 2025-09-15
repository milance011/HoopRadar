package com.example.hoopradar

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun LoginScreen(navController: NavHostController) {

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context  = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {

        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Text("Login", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val u = username.trim()
                    val p = password.trim()

                    if (u.isBlank() || p.isBlank()) {
                        Toast.makeText(context, "Unesi username i lozinku", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true

                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .whereEqualTo("username", u)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { docs ->
                            if (docs.isEmpty) {
                                isLoading = false
                                Toast.makeText(context, "Nepostojeći korisnik", Toast.LENGTH_SHORT).show()
                                return@addOnSuccessListener
                            }

                            val storedPassword = docs.first().getString("password")
                            if (storedPassword == p) {
                                // ❶ zapamti username da ga ProfileScreen kasnije pročita
                                navController.currentBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("username", u)

                                // ❷ prelazak na glavni graf
                                Toast.makeText(context, "Uspešna prijava", Toast.LENGTH_SHORT).show()
                                isLoading = false
                                navController.navigate("main") {
                                    popUpTo("login") { inclusive = false }
                                    launchSingleTop = true
                                }
                            } else {
                                isLoading = false
                                Toast.makeText(context, "Pogrešna lozinka", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            Toast.makeText(context, "Greška: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Login") }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = {
                    if (!isLoading) {
                        navController.navigate("signup") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                }
            ) { Text("Don't have an account? Sign up") }
        }

        // Loading overlay (između login i home)
        if (isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Signing in…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
