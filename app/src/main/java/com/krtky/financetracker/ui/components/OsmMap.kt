package com.krtky.financetracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.tileprovider.tilesource.XYTileSource

val CartoLight: XYTileSource = XYTileSource(
    "CartoDB_Light", 0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/light_all/",
        "https://b.basemaps.cartocdn.com/light_all/",
        "https://c.basemaps.cartocdn.com/light_all/",
        "https://d.basemaps.cartocdn.com/light_all/",
    ),
)

val CartoDark: XYTileSource = XYTileSource(
    "CartoDB_Dark", 0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/",
    ),
)

/**
 * Tiny static OSM map for a receipt/location preview.
 * No gestures, no scrolling; purely decorative context.
 */
@Composable
fun OsmMiniMap(
    latitude: Double,
    longitude: Double,
    placeName: String?,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f

    key(isDark) {
        val mapView = remember(latitude, longitude, isDark) {
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            org.osmdroid.views.MapView(ctx).apply {
                setTileSource(if (isDark) CartoDark else CartoLight)
                setMultiTouchControls(false)
                setOnTouchListener { _, _ -> true }
                controller.setZoom(16.0)
                controller.setCenter(org.osmdroid.util.GeoPoint(latitude, longitude))
                val marker = org.osmdroid.views.overlay.Marker(this)
                marker.position = org.osmdroid.util.GeoPoint(latitude, longitude)
                marker.setAnchor(
                    org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                    org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM,
                )
                marker.title = placeName ?: ""
                overlays.add(marker)
                minZoomLevel = 12.0
                isVerticalMapRepetitionEnabled = false
            }
        }
        DisposableEffect(lifecycleOwner) {
            mapView.onResume()
            onDispose { mapView.onPause() }
        }
        AndroidView(
            factory = { mapView },
            modifier = modifier,
        )
    }
}
