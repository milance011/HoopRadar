package com.milance.hoopradar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.example.hoopradar.*
import com.example.hoopradar.ui.theme.LoginJCAuthTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    object Map         : Tab("map",         "Home",         Icons.Default.Home)
    object Courts      : Tab("courts",      "Courts",      Icons.Default.Search)
    object Leaderboard : Tab("leaderboard", "Leaderboard",       Icons.Default.Star)
    object Profile     : Tab("profile",     "Profile",     Icons.Default.Person)
}
val tabs = listOf(Tab.Map, Tab.Courts, Tab.Leaderboard, Tab.Profile)

/* -------------------- Activity -------------------- */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        setContent {
            LoginJCAuthTheme {
                val start = if (FirebaseAuth.getInstance().currentUser != null) "main" else "login"
                AuthApp(start)
            }
        }
    }
}

/* -------------------- Auth graph -------------------- */
@Composable
fun AuthApp(startDestination: String) {
    val rootNav = rememberNavController()

    NavHost(rootNav, startDestination = startDestination) {
        composable("login")  { LoginScreen(rootNav) }
        composable("signup") { SignupScreen(rootNav) }
        composable("main")   { MainScaffold(rootNav) }
    }
}

/* -------------------- Main scaffold + bottom-nav -------------------- */
@Composable
fun MainScaffold(rootNav: NavController) {

    val tabNav       = rememberNavController()
    val currentRoute by tabNav.currentBackStackEntryAsState()

    Box(Modifier.fillMaxSize()) {

        /* ---------- sadržaj ekrana ---------- */
        NavHost(
            navController    = tabNav,
            startDestination = Tab.Map.route,
            modifier         = Modifier.fillMaxSize()
        ) {
            composable(Tab.Map.route)         { HomeScreen(rootNav) }
            composable(Tab.Courts.route)      { CourtsScreen() }
            composable(Tab.Leaderboard.route) { LeaderboardScreen() }
            composable(Tab.Profile.route) {
                ProfileScreen(
                    navController = rootNav,    // ← проследи root-NavController
                    onLogout = {
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
                .navigationBarsPadding()                 // ↑ подигни изнад system-bara // полупровидна тамно-плава
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute?.destination?.route == tab.route

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
                        selectedIconColor  = Color.White,
                        selectedTextColor  = Color.White,
                        unselectedIconColor = Color.LightGray,
                        unselectedTextColor = Color.LightGray,
                        indicatorColor      = Color.Transparent    // без позадине “пила”
                    )
                )
            }
        }
    }
}


