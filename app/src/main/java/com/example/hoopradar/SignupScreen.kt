package com.example.hoopradar

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

/* ---------- helper: креира temp-датотеку ---------- */
private fun createTmpFile(ctx: Context, prefix: String): File {
    val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val dir  = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile("${prefix}_${time}_", ".jpg", dir)
}

/* ---------- helper: square-crop + compress ---------- */
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

/* ================ Signup screen ================ */
@Composable
fun SignupScreen(navController: NavHostController) {

    val ctx = LocalContext.current
    var password    by remember { mutableStateOf("") }
    var username    by remember { mutableStateOf("") }
    var fullName    by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var imageUri    by remember { mutableStateOf<Uri?>(null) }

    var isUsernameTaken by remember { mutableStateOf(false) }
    var isAnyFieldEmpty by remember { mutableStateOf(false) }

    /* ---- галерија ---- */
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { imageUri = centerCropTo500(ctx, it) }
    }

    /* ---- камера ---- */
    val camTmpUri = remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) camTmpUri.value?.let { imageUri = centerCropTo500(ctx, it) }
    }

    /* ---- UI ---- */
    Box(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            /* --- унос текста (исто као раније) --- */
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; isUsernameTaken = false },
                label = { Text("Username") },
                isError = isUsernameTaken || (isAnyFieldEmpty && username.isBlank()),
                modifier = Modifier.fillMaxWidth()
            )
            if (isUsernameTaken)
                Text("Korisničko ime је заузето", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                isError = isAnyFieldEmpty && password.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full name") },
                isError = isAnyFieldEmpty && fullName.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone number") },
                isError = isAnyFieldEmpty && phoneNumber.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            /* --- рам 200dp + приказ --- */
            Box(
                Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, Color.Gray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                imageUri?.let {
                    val bmp = remember(it) {
                        BitmapFactory.decodeStream(ctx.contentResolver.openInputStream(it))
                    }
                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize())
                } ?: Text("No photo", color = Color.Gray)
            }

            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Gallery") }

                Spacer(Modifier.width(8.dp))

                Button(onClick = {
                    camTmpUri.value = createTmpFile(ctx, "CAM")
                        .let { FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", it) }
                    takePhoto.launch(camTmpUri.value)
                }) { Text("Camera") }
            }

            Spacer(Modifier.height(16.dp))

            /* --- Sign-up (непромењен) --- */
            Button(
                onClick = {
                    isAnyFieldEmpty =
                        password.isBlank() || username.isBlank() || fullName.isBlank() ||
                                phoneNumber.isBlank() || imageUri == null
                    if (isAnyFieldEmpty) {
                        Toast.makeText(ctx, "Попуни сва поља + слика", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val db = FirebaseFirestore.getInstance()
                    db.collection("users")
                        .whereEqualTo("username", username.trim())
                        .get()
                        .addOnSuccessListener { docs ->
                            if (!docs.isEmpty) {
                                isUsernameTaken = true
                                Toast.makeText(ctx, "Име већ постоји", Toast.LENGTH_SHORT).show()
                                return@addOnSuccessListener
                            }

                            val id  = UUID.randomUUID().toString()
                            val ref = FirebaseStorage.getInstance()
                                .reference.child("profileImages/$id.jpg")

                            ref.putFile(imageUri!!)
                                .continueWithTask { ref.downloadUrl }
                                .addOnSuccessListener { url ->
                                    val user = hashMapOf(
                                        "username" to username.trim(),
                                        "password" to password.trim(),
                                        "fullName" to fullName.trim(),
                                        "phone"    to phoneNumber.trim(),
                                        "photoUrl" to url.toString(),
                                        "points"   to 0L
                                    )
                                    db.collection("users").document(id)
                                        .set(user)
                                        .addOnSuccessListener {
                                            Toast.makeText(ctx, "Successful registration!", Toast.LENGTH_SHORT).show()
                                            navController.navigate("login") {
                                                popUpTo("signup") { inclusive = true }
                                            }
                                        }
                                }
                        }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Sign Up") }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = {
                navController.navigate("login") { popUpTo("signup") { inclusive = true } }
            }) { Text("You already have an account? Login") }
        }
    }
}
