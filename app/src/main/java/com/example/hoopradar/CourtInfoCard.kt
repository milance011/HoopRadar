package com.example.hoopradar

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourtInfoCard(
    navController: NavController,
    doc: DocumentSnapshot,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val txtColor  = Color(0xFFCFD8DC)
    val borderCol = txtColor.copy(alpha = 0.7f)
    val ctx       = LocalContext.current
    val scope     = rememberCoroutineScope()

    // --- Login kontekst (Auth UID ili username iz LoginScreen-a) ---
    val auth  = remember { FirebaseAuth.getInstance() }
    val myUid = auth.currentUser?.uid
    val savedUsername = navController.previousBackStackEntry?.savedStateHandle?.get<String>("username")
    val initialMyName = savedUsername ?: auth.currentUser?.displayName ?: "Me"
    val myName by produceState(initialValue = initialMyName, key1 = myUid, key2 = savedUsername) {
        if (!savedUsername.isNullOrBlank()) { value = savedUsername; return@produceState }
        if (myUid != null) {
            val users = FirebaseFirestore.getInstance().collection("users")
            runCatching {
                val u = users.document(myUid).get().await()
                value = u.getString("username")
                    ?: u.getString("nickname")
                            ?: u.getString("name")
                            ?: auth.currentUser?.displayName
                            ?: "Me"
            }
        }
    }
    val isLoggedIn = !savedUsername.isNullOrBlank() || (myUid != null)
    val myIdKey: String? = myUid ?: savedUsername

    // --- Autor terena (UID ili nickname) → autor ne može da komentariše
    val authorId   = doc.getString("authorId") ?: doc.getString("author")
    val authorNick = doc.getString("authorNickname") ?: doc.getString("authorUsername")
    val isAuthor = (myUid != null && authorId == myUid) ||
            (!savedUsername.isNullOrBlank() && authorNick != null && authorNick == savedUsername)

    // --- Komentari realtime (čitamo i id, userId, updatedAt) ---
    data class Comment(
        val id: String,
        val userId: String?,
        val userName: String,
        val rating: Int,
        val text: String,
        val createdAt: Timestamp?,
        val updatedAt: Timestamp?
    )
    val comments by produceState<List<Comment>>(emptyList(), key1 = doc.id) {
        FirebaseFirestore.getInstance()
            .collection("courts").document(doc.id)
            .collection("comments")
            .orderBy("createdAt")
            .addSnapshotListener { s, _ ->
                value = s?.documents?.map { d ->
                    Comment(
                        id        = d.id,
                        userId    = d.getString("userId"),
                        userName  = d.getString("userName") ?: "User",
                        rating    = (d.getLong("rating") ?: 0L).toInt(),
                        text      = d.getString("text") ?: "",
                        createdAt = d.getTimestamp("createdAt"),
                        updatedAt = d.getTimestamp("updatedAt")
                    )
                } ?: emptyList()
            }
    }

    // Moj postojeći komentar (ako postoji)
    val myExisting = remember(comments, myIdKey) {
        comments.firstOrNull { c -> myIdKey != null && c.userId == myIdKey }
    }

    // --- Prosečna ocena: početna + komentari ---
    val baseRating = doc.getDouble("rating") ?: doc.getLong("rating")?.toDouble()
    val avgVal = remember(baseRating, comments) {
        val list = buildList<Double> {
            baseRating?.let { add(it) }
            addAll(comments.map { it.rating.toDouble() })
        }
        if (list.isEmpty()) null else list.average()
    }
    val avgStr = avgVal?.let { "%.1f".format(Locale.US, it) } ?: "–"

    // --- UI state za novi review ili edit postojećeg ---
    var myRating  by rememberSaveable(doc.id) { mutableStateOf(0) }
    var myComment by rememberSaveable(doc.id) { mutableStateOf("") }
    var editMode  by rememberSaveable(doc.id) { mutableStateOf(false) }

    // Ako već imam komentar → pripremi edit state
    LaunchedEffect(myExisting?.id) {
        if (myExisting != null) {
            myRating  = myExisting.rating
            myComment = myExisting.text
            editMode  = false // start bez edit-a, pokaži "Edit"
        } else {
            myRating  = 0
            myComment = ""
            editMode  = false
        }
    }

    val allowNewReview = isLoggedIn && !isAuthor && (myExisting == null)
    val canPostNew     = (myRating in 1..5) && myComment.isNotBlank()
    val canSaveEdit    = (myRating in 1..5) && myComment.isNotBlank()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.4f),
        tonalElevation = 6.dp,
        color = Color(0xFF002D62),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = txtColor)
                }
            }

            // Naslov
            Box(Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)) {
                Surface(
                    color = Color.Transparent,
                    contentColor = txtColor,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, borderCol)
                ) {
                    Text(
                        text = doc.getString("name") ?: "Court",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize   = MaterialTheme.typography.titleLarge.fontSize * 1.1f,
                            letterSpacing = 1.sp,
                            color     = txtColor
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Slika + info
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = doc.getString("photoUrl")),
                    contentDescription = null,
                    modifier = Modifier.size(130.dp).clip(RoundedCornerShape(12.dp))
                )

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val bodyStyle  = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        color = txtColor
                    )
                    val labelStyle = bodyStyle.copy(fontWeight = FontWeight.Bold)
                    val valueStyle = bodyStyle

                    @Composable fun RowItem(label: String, value: String?) {
                        value?.takeIf { it.isNotBlank() }?.let { v ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("$label:", Modifier.weight(1f), style = labelStyle)
                                Text(v, Modifier.weight(2f), style = valueStyle, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    @Composable fun ParagraphItem(label: String, value: String?) {
                        value?.takeIf { it.isNotBlank() }?.let { v ->
                            Text(label, style = labelStyle)
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 10.sp))) { append(v) }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                style = valueStyle
                            )
                        }
                    }

                    RowItem("Address", doc.getString("address"))
                    RowItem("Lights", if (doc.getBoolean("hasLights") == true) "YES" else "NO")
                    RowItem("Surface", doc.getString("surface"))
                    RowItem("Rating", avgStr)
                    ParagraphItem("Description", doc.getString("description"))
                }
            }

            Spacer(Modifier.height(12.dp))
            Divider(color = borderCol)
            Spacer(Modifier.height(12.dp))

            // --- Novi review (samo ako nije autor i nema svoj komentar) ---
            if (allowNewReview) {
                Text("Add your review", color = txtColor, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val nickCaps = (myName.ifBlank { "Me" }).uppercase(Locale.getDefault()) + ":"
                    Text(
                        nickCaps,
                        color = txtColor,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        (1..5).forEach { i ->
                            IconButton(onClick = { myRating = i }) {
                                if (i <= myRating) {
                                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFD54F))
                                } else {
                                    Icon(Icons.Outlined.StarBorder, contentDescription = null, tint = txtColor)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = myComment,
                    onValueChange = { myComment = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = true,
                    label = { Text("Your comment", color = Color(0xFF444444)) },
                    placeholder = { Text("Write what you think about this court…") },
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor   = Color(0xFFF0F0F0),
                        unfocusedContainerColor = Color(0xFFF0F0F0),
                        focusedIndicatorColor   = txtColor,
                        unfocusedIndicatorColor = txtColor.copy(alpha = 0.6f),
                        focusedTextColor        = Color.Black,
                        unfocusedTextColor      = Color.Black,
                        cursorColor             = Color.Black
                    )
                )

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = {
                            if (!isLoggedIn) {
                                Toast.makeText(ctx, "Sign in first", Toast.LENGTH_SHORT).show(); return@Button
                            }
                            if (!canPostNew) {
                                Toast.makeText(ctx, "Select rating and write a comment", Toast.LENGTH_SHORT).show(); return@Button
                            }
                            val courts = FirebaseFirestore.getInstance().collection("courts")
                            val ref = courts.document(doc.id).collection("comments").document()
                            scope.launch {
                                runCatching {
                                    ref.set(
                                        mapOf(
                                            "userId"    to myIdKey,
                                            "userName"  to myName,
                                            "rating"    to myRating,
                                            "text"      to myComment.trim(),
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )
                                    ).await()
                                }.onSuccess {
                                    myRating = 0
                                    myComment = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF90CAF9),
                            contentColor   = Color(0xFF1F2937)
                        )
                    ) { Text("Post") }
                }

                Spacer(Modifier.height(12.dp))
                Divider(color = borderCol)
                Spacer(Modifier.height(8.dp))
            }

            // --- Moj komentar (ako postoji) — IDE PRVI; moguće urediti ---
            if (myExisting != null) {
                Text("Your review", color = txtColor, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))

                if (!editMode) {
                    // pregled
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(myExisting.userName, color = txtColor, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Row {
                            (1..5).forEach { i ->
                                if (i <= myExisting.rating)
                                    Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                                else
                                    Icon(Icons.Outlined.StarBorder, null, tint = txtColor, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        val dateTs = myExisting.updatedAt ?: myExisting.createdAt
                        val dateStr = dateTs?.toDate()?.let {
                            android.text.format.DateFormat.format("dd.MM.yyyy", it).toString()
                        } ?: ""
                        val editedMark = if (myExisting.updatedAt != null) " (edited)" else ""
                        Text("$dateStr$editedMark", color = txtColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    }
                    if (myExisting.text.isNotBlank()) {
                        Text(myExisting.text, color = txtColor, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            myRating  = myExisting.rating
                            myComment = myExisting.text
                            editMode  = true
                        }) { Text("Edit") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Divider(color = borderCol)
                    Spacer(Modifier.height(8.dp))
                } else {
                    // edit UI
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val nickCaps = (myName.ifBlank { "Me" }).uppercase(Locale.getDefault()) + ":"
                        Text(
                            nickCaps,
                            color = txtColor,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        Row {
                            (1..5).forEach { i ->
                                IconButton(onClick = { myRating = i }) {
                                    if (i <= myRating) {
                                        Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD54F))
                                    } else {
                                        Icon(Icons.Outlined.StarBorder, null, tint = txtColor)
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = myComment,
                        onValueChange = { myComment = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Your comment", color = Color(0xFF444444)) },
                        placeholder = { Text("Update your comment…") },
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = Color(0xFFF0F0F0),
                            unfocusedContainerColor = Color(0xFFF0F0F0),
                            focusedIndicatorColor   = txtColor,
                            unfocusedIndicatorColor = txtColor.copy(alpha = 0.6f),
                            focusedTextColor        = Color.Black,
                            unfocusedTextColor      = Color.Black,
                            cursorColor             = Color.Black
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editMode = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (!canSaveEdit) {
                                    Toast.makeText(ctx, "Select rating and write a comment", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val ref = FirebaseFirestore.getInstance()
                                    .collection("courts").document(doc.id)
                                    .collection("comments").document(myExisting.id)
                                scope.launch {
                                    runCatching {
                                        ref.update(
                                            mapOf(
                                                "rating"    to myRating,
                                                "text"      to myComment.trim(),
                                                "updatedAt" to FieldValue.serverTimestamp()
                                            )
                                        ).await()
                                    }.onSuccess {
                                        editMode = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF90CAF9),
                                contentColor   = Color(0xFF1F2937)
                            )
                        ) { Text("Save") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Divider(color = borderCol)
                    Spacer(Modifier.height(8.dp))
                }
            }

            // --- Ostali komentari (bez mog) ---
            val others = remember(comments, myExisting?.id) {
                if (myExisting == null) comments else comments.filterNot { it.id == myExisting.id }
            }
            if (others.isEmpty()) {
                Text("No reviews yet.", color = txtColor.copy(alpha = 0.8f))
            } else {
                others.forEach { c ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.userName, color = txtColor, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(8.dp))
                            Row {
                                (1..5).forEach { i ->
                                    if (i <= c.rating)
                                        Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                                    else
                                        Icon(Icons.Outlined.StarBorder, null, tint = txtColor, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            val dateTs = c.updatedAt ?: c.createdAt
                            val dateStr = dateTs?.toDate()?.let {
                                android.text.format.DateFormat.format("dd.MM.yyyy", it).toString()
                            } ?: ""
                            val editedMark = if (c.updatedAt != null) " (edited)" else ""
                            Text("$dateStr$editedMark", color = txtColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                        }
                        if (c.text.isNotBlank()) {
                            Text(c.text, color = txtColor, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
