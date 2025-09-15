package com.milance.hoopradar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.example.hoopradar.*
import com.example.hoopradar.ui.theme.LoginJCAuthTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.hoopradar.screens.CourtsScreen
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

// Dozvole + servis
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.hoopradar.location.ForegroundLocationService

// DODATO za splash sliku
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign

/* ---------------- Tabs ---------------- */
sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    object Map         : Tab("map",         "Home",        Icons.Default.Home)
    object Courts      : Tab("courts",      "Courts",      Icons.Default.Search)
    object Leaderboard : Tab("leaderboard", "Leaderboard", Icons.Default.Star)
    object Profile     : Tab("profile",     "Profile",     Icons.Default.Person)
}
val tabs = listOf(Tab.Map, Tab.Courts, Tab.Leaderboard, Tab.Profile)

/* ---------------- Activity ---------------- */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        setContent {
            LoginJCAuthTheme {
                // Kreni uvek sa "splash" – on odlučuje login/main i prikazuje loader sa slikom
                AuthApp()
            }
        }
    }
}

/* ---------------- Auth graph ---------------- */
@Composable
fun AuthApp() {
    val rootNav = rememberNavController()

    NavHost(rootNav, startDestination = "splash") {

        // Splash – fullscreen slika + "Loading…" dok FirebaseAuth odluči (ulogovan/neulogovan)
        composable("splash") { SplashRoute(rootNav) }

        composable("login")    { LoginScreen(rootNav) }
        composable("signup")   { SignupScreen(rootNav) }
        composable("main")     { MainScaffold(rootNav) }
        composable("addCourt") { AddCourtScreen(rootNav) }
    }
}

/* ---------------- Splash ruta (slika + loading tekst) ---------------- */
@Composable
private fun SplashRoute(rootNav: NavController) {
    var navigated by remember { mutableStateOf(false) }

    // UI: stavi svoju sliku u res/drawable/splash_logo.png i pozovi je ispod.
    // Ako je još nemaš, privremeno koristi R.drawable.ic_launcher_foreground.
    FullScreenSplash(imageRes = R.drawable.splash, text = "Loading…")

    // Slušamo FirebaseAuth i čim dobijemo stanje idemo na login ili main
    DisposableEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { fa ->
            if (!navigated) {
                navigated = true
                val target = if (fa.currentUser != null) "main" else "login"
                rootNav.navigate(target) {
                    popUpTo("splash") { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
}

/* ---------------- Full-screen splash helper (slika + tekst) ---------------- */
@Composable
private fun FullScreenSplash(
    @DrawableRes imageRes: Int,
    text: String = "Loading…"
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = Modifier.size(140.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/* ---------------- Main scaffold + bottom-nav ---------------- */
@Composable
fun MainScaffold(rootNav: NavController) {

    val tabNav       = rememberNavController()
    val currentRoute by tabNav.currentBackStackEntryAsState()
    val ctx          = LocalContext.current

    Box(Modifier.fillMaxSize()) {

        // Traži dozvole i pali foreground tracking servis (lokacija se snima "sve vreme")
        TrackingBootstrapper()

        /* ---------- sadržaj ekrana (TAB NavHost) ---------- */
        NavHost(
            navController    = tabNav,
            startDestination = Tab.Map.route,
            modifier         = Modifier.fillMaxSize()
        ) {
            // Prazna mapa (bez argumenata)
            composable("map") { HomeScreen(rootNav, tabNav) }

            // Mapa sa opcionalnim argumentima (lat/lng preko StringType)
            composable(
                route = "map?lat={lat}&lng={lng}&name={name}&url={url}&lights={lights}",
                arguments = listOf(
                    navArgument("lat")    { type = NavType.StringType; defaultValue = "" },
                    navArgument("lng")    { type = NavType.StringType; defaultValue = "" },
                    navArgument("name")   { type = NavType.StringType; defaultValue = "" },
                    navArgument("url")    { type = NavType.StringType; defaultValue = "" },
                    navArgument("lights") { type = NavType.BoolType;   defaultValue = false }
                )
            ) { HomeScreen(rootNav, tabNav) }

            composable(Tab.Courts.route)      { CourtsScreen(tabNav) }
            composable(Tab.Leaderboard.route) { LeaderboardScreen() }
            composable(Tab.Profile.route) {
                ProfileScreen(
                    navController = rootNav,
                    onLogout = {
                        stopTrackingService(ctx)
                        FirebaseAuth.getInstance().signOut()
                        rootNav.navigate("login") { popUpTo("main") { inclusive = true } }
                    }
                )
            }
        }

        /* ---------- bottom nav ---------- */
        NavigationBar(
            containerColor = Color(0xFF01579B),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute?.destination?.route?.startsWith(tab.route) == true

                NavigationBarItem(
                    selected = selected,
                    onClick  = {
                        if (!selected) tabNav.navigate(tab.route) { launchSingleTop = true }
                    },
                    icon  = { Icon(tab.icon, null) },
                    label = {
                        Text(
                            tab.label,
                            fontSize   = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor   = Color.White,
                        selectedTextColor   = Color.White,
                        unselectedIconColor = Color.LightGray,
                        unselectedTextColor = Color.LightGray,
                        indicatorColor      = Color.Transparent
                    )
                )
            }
        }
    }
}

/* ---------------- Dozvole + start/stop tracking servisa ---------------- */
@Composable
private fun TrackingBootstrapper() {
    val ctx = LocalContext.current

    // Background permission (Android 10+)
    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* rezultat nije kritičan za start; za full background tracking korisnik treba da odobri */ }

    // Fine/Coarse + (Tiramisu+) Notifications
    val permsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val fgGranted =
            granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fgGranted) {
            if (Build.VERSION.SDK_INT >= 29) {
                bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            startTrackingService(ctx)
        }
    }

    LaunchedEffect(Unit) {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        permsLauncher.launch(list.toTypedArray())
    }
}

private fun startTrackingService(ctx: Context) {
    val i = Intent(ctx, ForegroundLocationService::class.java)
    if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
}

private fun stopTrackingService(ctx: Context) {
    ctx.stopService(Intent(ctx, ForegroundLocationService::class.java))
}
