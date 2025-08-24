package com.example.hoopradar

import android.widget.Toast
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
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Unesi username i lozinku", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .whereEqualTo("username", username.trim())
                        .get()
                        .addOnSuccessListener { docs ->
                            if (docs.isEmpty) {
                                Toast.makeText(context, "Nepostojeći korisnik", Toast.LENGTH_SHORT).show()
                                return@addOnSuccessListener
                            }

                            val storedPassword = docs.first().getString("password")

                            if (storedPassword == password.trim()) {

                                /* ❶  zapamti username da ga ProfileScreen kasnije pročita  */
                                navController.currentBackStackEntry          // ← BackStackEntry za "login"
                                    ?.savedStateHandle
                                    ?.set("username", username.trim())

                                /* ❷  prelazak na glavni graf – NE brišemo login sa steka  */
                                Toast.makeText(context, "Uspešna prijava", Toast.LENGTH_SHORT).show()
                                navController.navigate("main") {
                                    popUpTo("login") { inclusive = false }   // ← login ostaje na steku
                                    launchSingleTop = true                   // (opciono) spreči duplikate
                                }
                            } else {
                                Toast.makeText(context, "Pogrešna lozinka", Toast.LENGTH_SHORT).show()
                            }
                        }

                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Greška: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Login") }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = {
                navController.navigate("signup") {
                    popUpTo("login") { inclusive = true }
                }
            }) { Text("Don't have an account? Sign up") }
        }
    }
}
