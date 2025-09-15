package com.example.hoopradar.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.hoopradar.data.location.LocationEntity
import com.example.hoopradar.data.location.LocationRepo
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class ForegroundLocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fused: FusedLocationProviderClient
    private var cb: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        startInFg()
        startUpdatesSafely()
    }

    private fun startInFg() {
        val chId = "loc_foreground"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    chId, "Location tracking", NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, chId)
            .setContentTitle("Praćenje lokacije aktivno")
            .setContentText("Aplikacija beleži vašu lokaciju")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        startForeground(101, notif)
    }

    private fun request(): LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(5f)
            .setWaitForAccurateLocation(true)
            .setMaxUpdateDelayMillis(10_000L)
            .build()

    /** Provera dozvola + bezbedno startovanje update-a */
    private fun startUpdatesSafely() {
        if (!hasLocationPermission()) {
            // Nema dozvole – mirno se ugasi, bez bacanja izuzetka
            stopSelf()
            return
        }

        cb = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                val l = res.lastLocation ?: return
                scope.launch {
                    LocationRepo.insert(
                        applicationContext,
                        LocationEntity(
                            lat = l.latitude,
                            lng = l.longitude,
                            accuracy = l.accuracy,
                            speed = if (l.hasSpeed()) l.speed else null,
                            bearing = if (l.hasBearing()) l.bearing else null,
                            provider = l.provider,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    // čuvaj max 7 dana
                    LocationRepo.cleanupOld(applicationContext, 7L * 24 * 60 * 60 * 1000)
                }
            }
        }

        try {
            fused.requestLocationUpdates(request(), cb as LocationCallback, mainLooper)
        } catch (_: SecurityException) {
            // Ako sistem ipak odbije – ugasi servis
            stopSelf()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    override fun onDestroy() {
        try {
            cb?.let { fused.removeLocationUpdates(it) }
        } catch (_: SecurityException) {
            // ignorisi – nema dozvole/više nema registracije
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
