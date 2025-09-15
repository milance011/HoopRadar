package com.example.hoopradar.data.location
import android.content.Context
import com.example.hoopradar.data.AppDb
object LocationRepo {
    suspend fun insert(ctx: Context, e: LocationEntity) =
        AppDb.get(ctx).locationDao().insert(e)
    suspend fun cleanupOld(ctx: Context, keepMs: Long) =
        AppDb.get(ctx).locationDao().cleanup(System.currentTimeMillis() - keepMs)
}
