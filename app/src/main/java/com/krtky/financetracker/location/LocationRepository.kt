package com.krtky.financetracker.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.LocationSampleEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val placeName: String? = null,
)

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun captureCurrent(): GeoPoint? {
        return try {
            val cts = CancellationTokenSource()
            val loc = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
                ?: client.lastLocation.await()
                ?: return null
            val place = reverseGeocode(loc.latitude, loc.longitude)
            val point = GeoPoint(loc.latitude, loc.longitude, loc.accuracy, place)
            saveSample(point)
            point
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveSample(point: GeoPoint, capturedAt: Long = System.currentTimeMillis()) {
        db.locationSampleDao().insert(
            LocationSampleEntity(
                latitude = point.latitude,
                longitude = point.longitude,
                accuracy = point.accuracy,
                capturedAt = capturedAt,
                placeName = point.placeName,
            )
        )
        val cutoff = System.currentTimeMillis() - 72L * 60 * 60 * 1000
        db.locationSampleDao().pruneOlderThan(cutoff)
    }

    private fun reverseGeocode(lat: Double, lon: Double): String? {
        return try {
            if (!Geocoder.isPresent()) return null
            val geo = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val list = geo.getFromLocation(lat, lon, 1)
            list?.firstOrNull()?.let { a ->
                listOfNotNull(a.featureName, a.subLocality, a.locality).distinct().joinToString(", ").take(120)
            }
        } catch (_: Exception) {
            null
        }
    }
}
