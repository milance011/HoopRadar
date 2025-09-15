package com.example.hoopradar.data.location
import androidx.room.Entity; import androidx.room.PrimaryKey
@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lat: Double, val lng: Double, val accuracy: Float,
    val speed: Float?, val bearing: Float?, val provider: String?, val timestamp: Long
)
