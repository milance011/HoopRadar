package com.example.hoopradar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlin.math.abs
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,     // root nav (login, addCourt)
    tabNav: NavHostController         // tab nav (map, courts)
) {
    /* ---------- helpers ---------- */
    val ctx         = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(ctx) }
    val settingsCl  = remember { LocationServices.getSettingsClient(ctx) }
    val cameraState = rememberCameraPositionState()
    val scope       = rememberCoroutineScope()

    /* ---------- permission ---------- */
    var hasPerm by remember { mutableStateOf(false) }
    val permL = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPerm = it }
    LaunchedEffect(Unit) { permL.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    /* ---------- location ---------- */
    var currentLoc by remember { mutableStateOf<LatLng?>(null) }

    // pratimo samo kad korisnik to traži (FAB); inicijalno: OFF
    var followMe by rememberSaveable { mutableStateOf(false) }
    // centriraj SAMO prvi put kad uđeš na screen bez nav fokusa
    var centeredOnce by rememberSaveable { mutableStateOf(false) }

    val locCb = remember {
        object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                r.lastLocation?.let { currentLoc = LatLng(it.latitude, it.longitude) }
            }
        }
    }
    @SuppressLint("MissingPermission")
    fun start(req: LocationRequest) =
        fusedClient.requestLocationUpdates(req, locCb, Looper.getMainLooper())

    /* ---------- GPS dialog ---------- */
    val gpsL = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            start(
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, 3_000L
                ).setMinUpdateDistanceMeters(15f).build()
            )
        }
    }

    DisposableEffect(hasPerm) {
        if (hasPerm) {
            val req = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 3_000L
            ).setMinUpdateDistanceMeters(15f).build()

            settingsCl.checkLocationSettings(
                LocationSettingsRequest.Builder().addLocationRequest(req).build()
            ).addOnSuccessListener { start(req) }
                .addOnFailureListener {
                    if (it is ResolvableApiException) {
                        gpsL.launch(IntentSenderRequest.Builder(it.resolution).build())
                    }
                }
        }
        onDispose { fusedClient.removeLocationUpdates(locCb) }
    }

    /* ---------- courts (Firestore) ---------- */
    val courts by produceState(emptyList<DocumentSnapshot>()) {
        FirebaseFirestore.getInstance()
            .collection("courts")
            .addSnapshotListener { s, _ -> value = s?.documents ?: emptyList() }
    }

    /* ---------- marker / panel state ---------- */
    var selectedCourt by remember { mutableStateOf<DocumentSnapshot?>(null) }

    /* ---------- fokus sa navigacije (čitaj iz TAB grafa) ---------- */
    val backStackEntry by tabNav.currentBackStackEntryAsState()
    val args = backStackEntry?.arguments
    val argLat = args?.getString("lat")?.toDoubleOrNull()
    val argLng = args?.getString("lng")?.toDoubleOrNull()

    val prevHandle  = tabNav.previousBackStackEntry?.savedStateHandle
    val prevLat     = prevHandle?.get<Double>("focusLat")
    val prevLng     = prevHandle?.get<Double>("focusLng")

    val hasNavFocus = (argLat != null && argLng != null) || (prevLat != null && prevLng != null)

    /* ❶ Fokus na court iz CourtsScreen-a → uvek centriraj (ali NE uključuj follow) */
    LaunchedEffect(argLat, argLng, prevLat, prevLng, courts) {
        val targetLat = argLat ?: prevLat
        val targetLng = argLng ?: prevLng
        if (targetLat != null && targetLng != null) {
            followMe = false
            centeredOnce = true  // da se kasnije ne auto-centira na user-a
            val target = LatLng(targetLat, targetLng)
            cameraState.move(CameraUpdateFactory.newLatLngZoom(target, 17f))

            selectedCourt = courts.firstOrNull { d ->
                d.getGeoPoint("geo")?.let { gp ->
                    abs(gp.latitude - targetLat) < 1e-5 && abs(gp.longitude - targetLng) < 1e-5
                } == true
            }

            prevHandle?.run {
                remove<Double>("focusLat"); remove<Double>("focusLng")
                remove<String>("focusName"); remove<String>("focusUrl")
                remove<Boolean>("focusLights")
            }
        }
    }

    /* ❷ Auto-centriranje na korisnika: SAMO prvi put kad uđeš na mapu (bez nav fokusa) */
    val isOnMapRoute = backStackEntry?.destination?.route?.startsWith("map") == true
    LaunchedEffect(isOnMapRoute, hasNavFocus, currentLoc, centeredOnce) {
        if (isOnMapRoute && !hasNavFocus && !centeredOnce && currentLoc != null) {
            cameraState.move(CameraUpdateFactory.newLatLngZoom(currentLoc!!, 17f))
            centeredOnce = true            // ⟵ posle ovoga više nema auto-centra
        }
    }

    /* ❸ Prati lokaciju samo kad korisnik uključi follow (FAB) */
    LaunchedEffect(currentLoc, followMe, hasNavFocus, selectedCourt) {
        if (currentLoc != null && followMe && selectedCourt == null) {
            cameraState.move(CameraUpdateFactory.newLatLng(currentLoc!!))
        }
    }

    /* ---------------- UI ---------------- */
    Box(Modifier.fillMaxSize().systemBarsPadding()) {

        GoogleMap(
            modifier            = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties          = MapProperties(isMyLocationEnabled = hasPerm),
            uiSettings          = MapUiSettings(
                zoomControlsEnabled     = false,
                myLocationButtonEnabled = false,
                compassEnabled          = false,
                mapToolbarEnabled       = false
            ),
            onMapClick      = { selectedCourt = null; followMe = false }, // korisnik pan/zoom → isključi follow
            onMapLongClick  = { selectedCourt = null; followMe = false }
        ) {
            courts.forEach { d ->
                d.getGeoPoint("geo")?.let { gp ->
                    val pos = LatLng(gp.latitude, gp.longitude)
                    Marker(
                        state  = MarkerState(pos),
                        title  = d.getString("name") ?: "Court",
                        icon   = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                        onClick = {
                            selectedCourt = d
                            followMe = false
                            false
                        }
                    )
                }
            }
        }

        /* ---------- INFO KARTICA ---------- */
        AnimatedVisibility(
            visible = selectedCourt != null,
            enter = slideInVertically(animationSpec = tween(300), initialOffsetY = { -it }),
            exit  = slideOutVertically(animationSpec = tween(300), targetOffsetY = { -it })
        ) {
            val doc = selectedCourt ?: return@AnimatedVisibility
            CourtInfoCard(
                navController = navController,
                doc = doc,
                onClose = { selectedCourt = null },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        /* ---------- FAB: My-location (uključuje follow po želji) ---------- */
        FloatingActionButton(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 96.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            onClick = {
                selectedCourt = null
                followMe = true
                centeredOnce = true            // i posle toga ostaje na user-u dok ne pomeriš mapu
                currentLoc?.let { loc ->
                    scope.launch {
                        cameraState.move(CameraUpdateFactory.newLatLngZoom(loc, 17f))
                    }
                }
            }
        ) { Icon(Icons.Filled.MyLocation, null) }

        /* ---------- FAB: Add court ---------- */
        FloatingActionButton(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 166.dp),
            onClick = { navController.navigate("addCourt") }
        ) { Icon(Icons.Filled.Add, null) }
    }
}
