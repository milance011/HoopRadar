package com.example.hoopradar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.hoopradar.model.UserProfile


/* ---------- screen ---------- */
@Composable
fun ProfileScreen(
    navController: NavController,
    onLogout: () -> Unit
) {
    /* 1️⃣  username који је Login сачувао у SavedStateHandle-у */
    val loginEntry = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry("login")
    }
    val username  = loginEntry.savedStateHandle.get<String>("username")

    /* 2️⃣ Firestore fetch */
    val userState = produceState<Result<UserProfile>?>(null) {
        if (username == null) {
            value = Result.failure(IllegalStateException("No user logged in"))
            return@produceState
        }
        try {
            val snap = FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .await()
                .documents
                .first()

            value = Result.success(
                UserProfile(
                    photoUrl = snap.getString("photoUrl") ?: "",
                    username = snap.getString("username") ?: "",
                    fullName = snap.getString("fullName") ?: "",
                    phone    = snap.getString("phone")    ?: "",
                    points   = snap.getLong("points")     ?: 0
                )
            )
        } catch (e: Exception) {
            value = Result.failure(e)
        }
    }

    /* 3️⃣  UI */
    when (val res = userState.value) {
        null -> Box(
            Modifier.fillMaxSize().systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        else -> if (res.isSuccess) {
            val p = res.getOrThrow()

            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {

                /* -------- Header (gornja trećina) -------- */
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(1f / 3f)
                        .shadow(
                            elevation = 10.dp,
                            shape      = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                            clip = false
                        )
                        .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                        .background(Color(0xFF01579B)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .border(2.dp, Color.Gray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(p.photoUrl),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }

                /* -------- Подаци о кориснику -------- */
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    LabeledRow(label = "USERNAME",     value = "@${p.username}")
                    LabeledRow(label = "FULL NAME",    value = p.fullName)
                    LabeledRow(label = "PHONE NUMBER", value = p.phone)
                    PointsBadge(points = p.points)         // посебан изглед за поене
                }

                /* -------- Logout dugme -------- */
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(46.dp)
                    ) {
                        Text("Logout", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Box(
                Modifier.fillMaxSize().systemBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Greška: ${res.exceptionOrNull()?.localizedMessage}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* -------------------- Helper composables -------------------------- */
/* ------------------------------------------------------------------ */

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text  = label,
            color = Color(0xFF555555),
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(Modifier.width(32.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PointsBadge(points: Long) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF01579B))
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "POINTS:    $points",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
