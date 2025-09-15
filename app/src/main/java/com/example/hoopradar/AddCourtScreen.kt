package com.example.hoopradar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.foundation.layout.Arrangement
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.min

private fun createTmpFile(ctx: Context, prefix: String): File {
    val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val dir  = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile("${prefix}_${time}_", ".jpg", dir)
}

private fun centerCropTo500(ctx: Context, srcUri: Uri): Uri {
    /* 1. оригинални Bitmap */
    val src = MediaStore.Images.Media.getBitmap(ctx.contentResolver, srcUri)

    /* 2. квадрат из центра */
    val size = min(src.width, src.height)
    val xOff = (src.width  - size) / 2
    val yOff = (src.height - size) / 2
    val sqr  = Bitmap.createBitmap(src, xOff, yOff, size, size)

    /* 3. скалирај на 500×500 */
    val outBmp = Bitmap.createScaledBitmap(sqr, 500, 500, true)

    /* 4. упиши у temp-file као JPEG 90 % */
    val file = createTmpFile(ctx, "CROP")
    FileOutputStream(file).use {
        outBmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
    }

    /* 5. врати FileProvider URI */
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
}

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourtScreen(navController: NavController) {

    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()

    /* ---------- тренутна локација ---------- */
    val fused = remember { LocationServices.getFusedLocationProviderClient(ctx) }
    var location by remember { mutableStateOf<LatLng?>(null) }

    LaunchedEffect(Unit) {
        @SuppressLint("MissingPermission")
        val loc = fused.lastLocation.await()
        location = LatLng(loc.latitude, loc.longitude)
    }

    /* ---------- state поља ---------- */
    var name        by remember { mutableStateOf("") }
    var surface     by remember { mutableStateOf("Concrete") }
    var hasLights   by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var rating      by remember { mutableStateOf(0) }            // 1‒5
    var photoUri    by remember { mutableStateOf<Uri?>(null) }

    /* ---------- pick / camera launcher-и ---------- */
    fun createTmpUri(): Uri {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir  = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile("JPEG_${time}_", ".jpg", dir)
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
    }

    val galleryL = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { photoUri = centerCropTo500(ctx, it) }
    }

    val camTmp   = remember { mutableStateOf<Uri?>(null) }
    val cameraL  = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) camTmp.value?.let { photoUri = centerCropTo500(ctx, it) }
    }

    /* 1️⃣  реактивно проверимо да ли су сва обавезна поља попуњена  */
    val canSave by derivedStateOf {
        location != null &&                       // имамо GPS
                name.isNotBlank() &&                      // унет назив
                photoUri != null &&                       // слика изабрана
                rating > 0                                // изабран бар 1 ⭐
        /* description сме да буде празан */
    }

    /* ---------- UI ---------- */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally          // ① све потомке центрира по X-оси
    ) {

        /* --- ред: Close (X) дугме --- */
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.Red,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { navController.popBackStack() }
            )
        }

        /* --- Назив терена --- */
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Court name") },
            modifier = Modifier.fillMaxWidth()
        )

        /* ---------- SURFACE ---------- */
        var surfExpanded by remember { mutableStateOf(false) }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /* лева колона ‒ етикета */
            Text(
                "Surface:",
                color = Color.Black,
                modifier = Modifier.weight(0.30f)        // ← 30 % ширине, лево поравнање
            )

            /* десна колона ‒ падајући мени */
            ExposedDropdownMenuBox(
                expanded = surfExpanded,
                onExpandedChange = { surfExpanded = !surfExpanded },
                modifier = Modifier.weight(0.70f)        // ← заузми преосталих 70 %
            ) {
                TextField(
                    value = surface,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = surfExpanded)
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor   = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor   = Color.White,
                        unfocusedIndicatorColor = Color.LightGray,
                        focusedTextColor        = Color.Black,
                        unfocusedTextColor      = Color.Black
                    ),
                    modifier = Modifier
                        .menuAnchor()
                        .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = surfExpanded,
                    onDismissRequest = { surfExpanded = false }
                ) {
                    listOf("Concrete", "Rubber", "Asphalt", "Wood").forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt) },
                            onClick = {
                                surface = opt
                                surfExpanded = false
                            }
                        )
                    }
                }
            }
        }

        /* ---------- LIGHTS ---------- */
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Lights?",
                color = Color.Black,
                modifier = Modifier.weight(0.30f)
            )

            Row(                                    // сад је у својој “ћелији” (70 %)
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.weight(0.70f)
            ) {
                RadioButton(
                    selected = hasLights,
                    onClick  = { hasLights = true },
                    colors   = RadioButtonDefaults.colors(selectedColor = Color.Blue)
                )
                Text("Yes", color = Color.Black)
                Spacer(Modifier.width(16.dp))
                RadioButton(
                    selected = !hasLights,
                    onClick  = { hasLights = false },
                    colors   = RadioButtonDefaults.colors(selectedColor = Color.Blue)
                )
                Text("No", color = Color.Black)
            }
        }

        /* --- Опис --- */
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )

        /* ---------- RATING ---------- */
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Rating:",
                color = Color.Black,
                modifier = Modifier.weight(0.30f)
            )

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(0.70f)
            ) {
                repeat(5) { i ->
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint  = if (rating >= i + 1) Color(0xFFFFD600) else Color.Gray,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { rating = i + 1 }
                    )
                }
            }
        }

        /* ---------- THUMBNAIL ---------- */
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, Color.Gray, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            photoUri?.let {
                val bmp = remember(it) {
                    BitmapFactory.decodeStream(ctx.contentResolver.openInputStream(it))
                }
                Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize())
            } ?: Text("No photo", color = Color.Gray)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Button(
                onClick = {
                    galleryL.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                colors = ButtonDefaults.buttonColors(Color(0xFF01579B))
            ) { Text("Gallery") }

            Button(
                onClick = {
                    camTmp.value = createTmpUri()
                    cameraL.launch(camTmp.value)
                },
                colors = ButtonDefaults.buttonColors(Color(0xFF01579B))
            ) { Text("Camera") }
        }

        Spacer(Modifier.weight(1f))

        /* --- SAVE --- */
        Button(
            onClick = {
                if (location == null || name.isBlank() || photoUri == null || rating == 0) {
                    Toast.makeText(ctx, "Fill all fields", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                scope.launch {
                    try {
                        /* ➊ ––– reverse-geocoding (blokira mrežu → radi u IO niti) */
                        val address = withContext(Dispatchers.IO) {
                            runCatching {
                                val geo  = Geocoder(ctx, Locale.getDefault())
                                val list = geo.getFromLocation(location!!.latitude, location!!.longitude, 1)
                                list?.firstOrNull()?.getAddressLine(0)
                            }.getOrNull() ?: "Unknown address"
                        }

                        /* ➋ ––– pre dohvat/određivanje autora (UID + nickname) */
                        val auth    = FirebaseAuth.getInstance()
                        val authorId = auth.currentUser?.uid
                        val authorNickname = withContext(Dispatchers.IO) {
                            // pokuša(va)mo redom: users/{uid} → users where uid == ... → Firebase displayName
                            val usersCol = FirebaseFirestore.getInstance().collection("users")
                            val fromDocId = authorId?.let {
                                val doc = usersCol.document(it).get().await()
                                if (doc.exists()) doc.getString("username")
                                    ?: doc.getString("nickname")
                                    ?: doc.getString("name") else null
                            }

                            fromDocId ?: run {
                                val byUid = authorId?.let {
                                    usersCol.whereEqualTo("uid", it).limit(1).get().await()
                                        .documents.firstOrNull()
                                }
                                byUid?.getString("username")
                                    ?: byUid?.getString("nickname")
                                    ?: byUid?.getString("name")
                            } ?: auth.currentUser?.displayName ?: "Unknown"
                        }

                        /* ➌ ––– upload slike */
                        val courts = FirebaseFirestore.getInstance().collection("courts")
                        val id     = courts.document().id
                        val ref    = FirebaseStorage.getInstance()
                            .reference.child("courtPhotos/$id.jpg")

                        ref.putFile(photoUri!!).await()
                        val url = ref.downloadUrl.await()

                        /* ➍ ––– upis u Firestore (dodati authorId + authorNickname) */
                        val data = mapOf(
                            "geo"             to GeoPoint(location!!.latitude, location!!.longitude),
                            "address"         to address,
                            "author"          to authorId,           // (legacy) ostavljen radi kompatibilnosti
                            "authorId"        to authorId,
                            "authorNickname"  to authorNickname,     // ⟵ traženo polje
                            "name"            to name.trim(),
                            "surface"         to surface,
                            "hasLights"       to hasLights,
                            "description"     to description.trim(),
                            "rating"          to rating,
                            "photoUrl"        to url.toString(),
                            "createdAt"       to FieldValue.serverTimestamp()
                        )

                        courts.document(id).set(data).await()

                        Toast.makeText(ctx, "Court saved ✔", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()

                    } catch (e: Exception) {
                        Toast.makeText(ctx, e.localizedMessage ?: "Error", Toast.LENGTH_LONG).show()
                    }
                }
            },
            enabled  = canSave,
            colors  = ButtonDefaults.buttonColors(Color(0xFF52B788)),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save", fontSize = 18.sp) }
    }
}
