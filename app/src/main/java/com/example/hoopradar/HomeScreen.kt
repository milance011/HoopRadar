package com.example.hoopradar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.*

@Composable
fun HomeScreen(navController: NavController) {

    val ctx = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }

    /* --- 1. dozvola --- */
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) { permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    /* --- 2. klijenti --- */
    val fused           = remember { LocationServices.getFusedLocationProviderClient(ctx) }
    val settingsClient  = remember { LocationServices.getSettingsClient(ctx) }

    /* --- 3. state --- */
    var currentLoc by remember { mutableStateOf<LatLng?>(null) }
    val pathPoints      = remember { mutableStateListOf<LatLng>() }

    /* ---------- 4. CALLBACK & helper --------- */
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    currentLoc = latLng
                    pathPoints.add(latLng)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startUpdates(req: LocationRequest) {
        fused.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    /* ---------- 5. launcher za „Turn on GPS” dialog ---------- */
    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // korisnik uključio GPS – request updates:
            val req = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 3_000L
            ).setMinUpdateDistanceMeters(15f).build()
            startUpdates(req)
        }
    }

    /* ---------- 6. DisposableEffect: permission + settings ---------- */
    DisposableEffect(hasPermission) {
        if (hasPermission) {
            val req = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 3_000L
            ).setMinUpdateDistanceMeters(15f).build()

            val settingsRequest = LocationSettingsRequest.Builder()
                .addLocationRequest(req)
                .build()

            settingsClient.checkLocationSettings(settingsRequest)
                .addOnSuccessListener { startUpdates(req) }
                .addOnFailureListener { e ->
                    if (e is ResolvableApiException) {
                        resolutionLauncher.launch(
                            IntentSenderRequest.Builder(e.resolution).build()
                        )
                    }
                }
        }
        onDispose { fused.removeLocationUpdates(locationCallback) }
    }

    /* ---------- 7. UI (mapa + logout) ---------- */
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        val cameraState = rememberCameraPositionState()

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = hasPermission),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false,
                mapToolbarEnabled = false
            )
        ) { /* marker optional */ }

        LaunchedEffect(currentLoc) {
            currentLoc?.let {
                cameraState.move(          // <-- тренутно померање, без анимације
                    CameraUpdateFactory.newLatLngZoom(it, 17f)
                )
            }
        }
    }
}
