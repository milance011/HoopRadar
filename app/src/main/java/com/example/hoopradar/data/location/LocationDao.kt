package com.example.hoopradar.data.location
import androidx.room.*; import kotlinx.coroutines.flow.Flow
@Dao interface LocationDao {
    @Insert suspend fun insert(e: LocationEntity)
    @Query("DELETE FROM locations WHERE timestamp < :cutoff") suspend fun cleanup(cutoff: Long)
    @Query("SELECT * FROM locations ORDER BY timestamp DESC LIMIT :limit")
    fun lastN(limit: Int = 100): Flow<List<LocationEntity>>
}
