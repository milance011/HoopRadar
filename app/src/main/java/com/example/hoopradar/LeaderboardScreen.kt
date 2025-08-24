package com.example.hoopradar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.hoopradar.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/* --- боје за прва три места --- */
private val Gold   = Color(0xFFFFD700)
private val Silver = Color(0xFFC0C0C0)
private val Bronze = Color(0xFFCD7F32)

/* ---------------------- екран ---------------------- */
@Composable
fun LeaderboardScreen() {

    val usersState by produceState<Result<List<UserProfile>>?>(null) {
        try {
            val snaps = FirebaseFirestore.getInstance()
                .collection("users")
                .orderBy("points", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            value = Result.success(
                snaps.documents.map { d ->
                    UserProfile(
                        photoUrl = d.getString("photoUrl") ?: "",
                        username = d.getString("username") ?: "",
                        points   = d.getLong("points")     ?: 0
                    )
                }
            )
        } catch (e: Exception) { value = Result.failure(e) }
    }

    when (val res = usersState) {
        null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

        else -> if (res.isSuccess) {
            val users = res.getOrThrow()

            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                Text(
                    text       = "LEADERBOARD",
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    textAlign  = TextAlign.Center,
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.Black,
                    color      = Color(0xFF01579B)
                )

                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier            = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(users) { index, user ->
                        LeaderboardRow(position = index + 1, user = user)
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "Greška: ${res.exceptionOrNull()?.localizedMessage}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/* ---------------------- ред = badge + card --------------------------- */
@Composable
private fun LeaderboardRow(position: Int, user: UserProfile) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        /* ---------- BADGE ---------- */
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    when (position) {
                        1 -> Gold
                        2 -> Silver
                        3 -> Bronze
                        else -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = "$position.",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.Black
            )
        }

        Spacer(Modifier.width(8.dp))

        /* ---------- КАРТИЦА ---------- */
        LeaderboardCard(user)
    }
}

/* ---------------------- сама картица ----------------------------- */
@Composable
private fun LeaderboardCard(user: UserProfile) {

    Card(
        colors    = CardDefaults.cardColors(containerColor = Color(0xFF01579B)),
        shape     = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier  = Modifier
            .fillMaxWidth()
            .height(84.dp)
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /* ----- слика ----- */
            Image(
                painter  = rememberAsyncImagePainter(user.photoUrl),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            /* ----- username (центр) ----- */
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text      = user.username,
                    color     = Color.White,
                    fontSize  = 20.sp,
                    textAlign = TextAlign.Center
                )
            }

            /* ----- поени ----- */
            Text(
                text       = user.points.toString(),
                color      = Color.White,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(end = 16.dp)
            )
        }
    }
}
