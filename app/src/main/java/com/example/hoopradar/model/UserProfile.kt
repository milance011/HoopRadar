//  app/src/main/java/com/example/hoopradar/model/UserProfile.kt
package com.example.hoopradar.model   // ← ili root paket, bitno je da oba ekrana uvoze isto

data class UserProfile(
    val photoUrl: String = "",
    val username: String = "",
    val fullName: String = "",
    val phone:    String = "",
    val points:   Long   = 0
)
