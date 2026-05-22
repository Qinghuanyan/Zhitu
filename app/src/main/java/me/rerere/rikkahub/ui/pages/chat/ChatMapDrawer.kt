package me.rerere.rikkahub.ui.pages.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps2d.AMap
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.MapView
import com.amap.api.maps2d.model.BitmapDescriptorFactory
import com.amap.api.maps2d.model.LatLng
import com.amap.api.maps2d.model.LatLngBounds
import com.amap.api.maps2d.model.Marker
import com.amap.api.maps2d.model.MarkerOptions
import com.composables.icons.lucide.LocateFixed
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Navigation
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.amap.MapWarmupManager
import me.rerere.rikkahub.data.model.TravelPoi
import me.rerere.rikkahub.ui.components.ui.permission.PermissionLocation
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.isPackageInstalled
import me.rerere.rikkahub.utils.startActivitySafely
import org.koin.compose.koinInject
import java.net.URLEncoder
import java.util.Locale

private const val TAG = "ChatMapDrawer"
private const val AMAP_PACKAGE_NAME = "com.autonavi.minimap"
private const val DEVICE_LOCATION_LABEL = "设备定位"

private data class LocationPoint(
    val latLng: LatLng,
    val title: String? = null,
    val snippet: String? = null,
)

private data class MapUiState(
    val currentLocation: LocationPoint?,
    val destination: LocationPoint?,
    val selectedPoi: TravelPoi?,
    val locationError: String?,
    val locating: Boolean,
)

private data class MapActions(
    val clearDestination: () -> Unit,
    val relocate: () -> Unit,
    val navigate: () -> Unit,
)

private enum class DestinationSelectionMode {
    NONE,
    AUTO_POI,
    MANUAL,
}

@Composable
fun ChatMapDrawerContent(
    modifier: Modifier = Modifier,
    travelPois: List<TravelPoi> = emptyList(),
    highlightPoiId: String? = null,
    onMapGestureStateChanged: (Boolean) -> Unit = {},
    showBottomPanel: Boolean = true,
    onOpenInternalWebView: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val nav = LocalNavController.current
    val mapWarmupManager: MapWarmupManager = koinInject()
    val locationPermission = rememberPermissionState(PermissionLocation)
    val openInternalWebView = remember(nav, onOpenInternalWebView) {
        onOpenInternalWebView ?: { url -> nav.navigate(Screen.WebView(url = url)) }
    }

    PermissionManager(permissionState = locationPermission) {
        if (!locationPermission.allRequiredPermissionsGranted) {
            LaunchedEffect(Unit) {
                locationPermission.requestPermissions()
            }
            PermissionRequiredView(modifier = modifier) {
                locationPermission.requestPermissions()
            }
            return@PermissionManager
        }

        LaunchedEffect(mapWarmupManager) {
            mapWarmupManager.warmup()
        }

        val mapView = rememberMapViewWithLifecycle()
        val map = remember(mapView) { mapView.map }
        val destinationMarker = remember(map) { arrayOfNulls<Marker>(1) }
        val currentLocationMarker = remember(map) { arrayOfNulls<Marker>(1) }
        val travelMarkers = remember(map) { mutableMapOf<String, Marker>() }
        val markerPoiIndex = remember(map) { mutableMapOf<String, TravelPoi>() }
        val hasInitialFix = remember(map) { booleanArrayOf(false) }
        val lastAutoFocusSignature = remember(map) { arrayOfNulls<String>(1) }

        var currentLocation by remember { mutableStateOf<LocationPoint?>(null) }
        var destination by remember { mutableStateOf<LocationPoint?>(null) }
        var selectedPoi by remember { mutableStateOf<TravelPoi?>(null) }
        var destinationSelectionMode by remember { mutableStateOf(DestinationSelectionMode.NONE) }
        var locationError by remember { mutableStateOf<String?>(null) }
        var locating by remember { mutableStateOf(true) }

        val locationClient = remember {
            runCatching {
                AMapLocationClient.updatePrivacyShow(context, true, true)
                AMapLocationClient.updatePrivacyAgree(context, true)
                AMapLocationClient(context)
            }.getOrNull()
        }

        val latestGestureStateChanged = rememberUpdatedState(onMapGestureStateChanged)
        DisposableEffect(Unit) {
            onDispose {
                latestGestureStateChanged.value(false)
            }
        }

        val shouldPreferDeviceLocation = remember(travelPois, highlightPoiId) {
            travelPois.isEmpty() && highlightPoiId.isNullOrBlank()
        }

        DisposableEffect(locationClient, map, shouldPreferDeviceLocation) {
            if (locationClient == null) {
                locating = false
                locationError = "LocationClient init failed"
                return@DisposableEffect onDispose {}
            }

            configureLocationClient(locationClient)
            locationClient.setLocationListener { location: AMapLocation? ->
                if (location != null && location.errorCode == 0) {
                    val point = buildLocationPoint(location)
                    currentLocation = point
                    locationError = null
                    locating = false
                    Log.i(
                        TAG,
                        "Location success lat=${location.latitude}, lon=${location.longitude}, poi=${location.poiName}, address=${location.address}, detail=${location.locationDetail}"
                    )
                    if (!hasInitialFix[0] && shouldPreferDeviceLocation) {
                        hasInitialFix[0] = true
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(point.latLng, 16f))
                    }
                    locationClient.stopLocation()
                } else if (location != null) {
                    locating = false
                    locationError = "[${location.errorCode}] ${location.errorInfo}\n${location.locationDetail}"
                    Log.w(
                        TAG,
                        "Location error code=${location.errorCode}, info=${location.errorInfo}, detail=${location.locationDetail}, lat=${location.latitude}, lon=${location.longitude}"
                    )
                    locationClient.stopLocation()
                }
            }
            locationClient.startLocation()
            onDispose {
                locationClient.stopLocation()
                locationClient.onDestroy()
            }
        }

        LaunchedEffect(travelPois, highlightPoiId) {
            val preferredPoi = travelPois.preferredPoi(highlightPoiId) ?: return@LaunchedEffect
            if (highlightPoiId != null || destination == null || destinationSelectionMode == DestinationSelectionMode.AUTO_POI) {
                destination = preferredPoi.toLocationPoint()
                selectedPoi = preferredPoi
                destinationSelectionMode = DestinationSelectionMode.AUTO_POI
            }
        }

        val uiState = MapUiState(
            currentLocation = currentLocation,
            destination = destination,
            selectedPoi = selectedPoi,
            locationError = locationError,
            locating = locating,
        )
        val actions = remember(context, map, currentLocation, destination, selectedPoi, locationClient, openInternalWebView) {
            MapActions(
                clearDestination = {
                    destination = null
                    selectedPoi = null
                    destinationSelectionMode = DestinationSelectionMode.NONE
                    clearDestinationMarker(destinationMarker)
                },
                relocate = {
                    currentLocation?.latLng?.let { latLng ->
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    } ?: run {
                        locating = true
                        locationClient?.startLocation()
                    }
                },
                navigate = {
                    val targetPoint = selectedPoi?.toLocationPoint() ?: destination
                    targetPoint?.latLng?.let { to ->
                        val from = currentLocation?.latLng
                        val destinationName = selectedPoi?.name
                            ?: targetPoint.title
                            ?: targetPoint.snippet
                            ?: "destination"
                        val nativeUri = Uri.parse(
                            buildNavigationUri(
                                sourceLat = from?.latitude,
                                sourceLon = from?.longitude,
                                destLat = to.latitude,
                                destLon = to.longitude,
                                destinationName = destinationName,
                                applicationName = context.getString(R.string.app_name),
                            )
                        )
                        val nativeIntent = Intent(Intent.ACTION_VIEW, nativeUri)
                        val packagedIntent = Intent(Intent.ACTION_VIEW, nativeUri).setPackage(AMAP_PACKAGE_NAME)
                        val nativeLaunched = buildList {
                            if (context.isPackageInstalled(AMAP_PACKAGE_NAME)) add(packagedIntent)
                            add(nativeIntent)
                        }.any(context::startActivitySafely)
                        if (nativeLaunched) {
                            if (from == null) {
                                Toast.makeText(context, "未获取到当前位置，已打开高德目的地页", Toast.LENGTH_SHORT).show()
                            }
                            return@let
                        }

                        val webUrl = buildWebNavigationUrl(
                            sourceLat = from?.latitude,
                            sourceLon = from?.longitude,
                            destLat = to.latitude,
                            destLon = to.longitude,
                            destinationName = destinationName,
                            applicationName = context.getString(R.string.app_name),
                        )
                        runCatching {
                            openInternalWebView(webUrl)
                        }.onFailure {
                            Toast.makeText(context, "Unable to open navigation", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ChatMapHost(
                        mapView = mapView,
                        currentLocation = currentLocation,
                        currentLocationMarker = currentLocationMarker,
                        destinationMarker = destinationMarker,
                        travelPois = travelPois,
                        highlightPoiId = highlightPoiId,
                        travelMarkers = travelMarkers,
                        lastAutoFocusSignature = lastAutoFocusSignature,
                        markerPoiIndex = markerPoiIndex,
                        onMapGestureStateChanged = { latestGestureStateChanged.value(it) },
                        onDestinationSelected = { point ->
                            destination = point
                            selectedPoi = null
                            destinationSelectionMode = DestinationSelectionMode.MANUAL
                        },
                        onPoiSelected = { poi ->
                            destination = poi.toLocationPoint()
                            selectedPoi = poi
                            destinationSelectionMode = DestinationSelectionMode.MANUAL
                        },
                    )
                    ChatMapOverlays(
                        uiState = uiState,
                        onRelocate = actions.relocate,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (showBottomPanel) {
                ChatMapBottomPanel(
                    uiState = uiState,
                    actions = actions,
                )
            }
        }
    }
}

@Composable
private fun PermissionRequiredView(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.chat_map_permission_required),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRequestPermission) {
            Text(text = stringResource(R.string.permission_location))
        }
    }
}

@Composable
private fun ChatMapHost(
    mapView: MapView,
    currentLocation: LocationPoint?,
    currentLocationMarker: Array<Marker?>,
    destinationMarker: Array<Marker?>,
    travelPois: List<TravelPoi>,
    highlightPoiId: String?,
    travelMarkers: MutableMap<String, Marker>,
    lastAutoFocusSignature: Array<String?>,
    markerPoiIndex: MutableMap<String, TravelPoi>,
    onMapGestureStateChanged: (Boolean) -> Unit,
    onDestinationSelected: (LocationPoint) -> Unit,
    onPoiSelected: (TravelPoi) -> Unit,
) {
    val latestGestureStateChanged = rememberUpdatedState(onMapGestureStateChanged)
    val latestDestinationSelected = rememberUpdatedState(onDestinationSelected)
    val latestPoiSelected = rememberUpdatedState(onPoiSelected)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            mapView.apply {
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_POINTER_DOWN,
                        MotionEvent.ACTION_MOVE -> latestGestureStateChanged.value(true)
                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_POINTER_UP -> {
                            if (event.pointerCount <= 1) {
                                latestGestureStateChanged.value(false)
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> latestGestureStateChanged.value(false)
                    }
                    false
                }
                map?.let { map ->
                    configureMap(
                        map = map,
                        context = viewContext,
                        destinationMarker = destinationMarker,
                        markerPoiIndex = markerPoiIndex,
                        onDestinationSelected = { point -> latestDestinationSelected.value(point) },
                        onPoiSelected = { poi -> latestPoiSelected.value(poi) },
                    )
                }
            }
        },
        update = { view ->
            view.map?.let { amap ->
                syncCurrentLocationMarker(
                    map = amap,
                    context = view.context,
                    currentLocationMarker = currentLocationMarker,
                    point = currentLocation,
                )
                syncTravelPoiMarkers(
                    map = amap,
                    currentLocation = currentLocation,
                    context = view.context,
                    mapWidth = view.width,
                    mapHeight = view.height,
                    travelMarkers = travelMarkers,
                    lastAutoFocusSignature = lastAutoFocusSignature,
                    markerPoiIndex = markerPoiIndex,
                    travelPois = travelPois,
                    highlightPoiId = highlightPoiId,
                )
            }
        }
    )
}

private fun configureMap(
    map: AMap,
    context: Context,
    destinationMarker: Array<Marker?>,
    markerPoiIndex: MutableMap<String, TravelPoi>,
    onDestinationSelected: (LocationPoint) -> Unit,
    onPoiSelected: (TravelPoi) -> Unit,
) {
    map.uiSettings.apply {
        isZoomControlsEnabled = false
        isCompassEnabled = true
        isMyLocationButtonEnabled = false
        isScrollGesturesEnabled = true
        isZoomGesturesEnabled = true
        setAllGesturesEnabled(true)
        setZoomInByScreenCenter(false)
    }
    map.moveCamera(CameraUpdateFactory.zoomTo(15f))
    map.isMyLocationEnabled = true
    map.setOnMapLongClickListener { latLng ->
        val point = LocationPoint(
            latLng = latLng,
            title = context.getString(R.string.chat_map_destination),
            snippet = String.format(Locale.US, "%.6f, %.6f", latLng.latitude, latLng.longitude),
        )
        onDestinationSelected(point)
        updateDestinationMarker(
            map = map,
            context = context,
            destinationMarker = destinationMarker,
            point = point,
        )
        focusLatLngWithOffset(map, latLng, zoom = 15f)
    }
    map.setOnMarkerClickListener { marker ->
        val poi = markerPoiIndex[marker.id]
        if (poi != null) {
            onPoiSelected(poi)
            focusLatLngWithOffset(map, marker.position)
            true
        } else {
            false
        }
    }
}

private fun updateDestinationMarker(
    map: AMap,
    context: Context,
    destinationMarker: Array<Marker?>,
    point: LocationPoint,
) {
    val marker = destinationMarker[0]
    if (marker == null) {
        destinationMarker[0] = map.addMarker(
            MarkerOptions()
                .position(point.latLng)
                .title(context.getString(R.string.chat_map_destination))
                .snippet(point.snippet)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )
    } else {
        marker.position = point.latLng
        marker.title = point.title ?: context.getString(R.string.chat_map_destination)
        marker.snippet = point.snippet
    }
}

private fun syncCurrentLocationMarker(
    map: AMap,
    context: Context,
    currentLocationMarker: Array<Marker?>,
    point: LocationPoint?,
) {
    if (point == null) {
        currentLocationMarker[0]?.remove()
        currentLocationMarker[0] = null
        return
    }

    val marker = currentLocationMarker[0]
    if (marker == null) {
        currentLocationMarker[0] = map.addMarker(
            MarkerOptions()
                .position(point.latLng)
                .title("我的位置")
                .snippet(point.snippet ?: context.getString(R.string.chat_map_locating))
                .zIndex(3f)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )
    } else {
        marker.position = point.latLng
        marker.title = "我的位置"
        marker.snippet = point.snippet
        marker.zIndex = 3f
    }
}

private fun syncTravelPoiMarkers(
    map: AMap,
    currentLocation: LocationPoint?,
    context: Context,
    mapWidth: Int,
    mapHeight: Int,
    travelMarkers: MutableMap<String, Marker>,
    lastAutoFocusSignature: Array<String?>,
    markerPoiIndex: MutableMap<String, TravelPoi>,
    travelPois: List<TravelPoi>,
    highlightPoiId: String?,
) {
    val visiblePois = travelPois.filter { it.lat != null && it.lon != null }
    val stalePoiIds = travelMarkers.keys - visiblePois.mapTo(mutableSetOf()) { it.id }
    stalePoiIds.forEach { poiId ->
        travelMarkers.remove(poiId)?.remove()
    }
    markerPoiIndex.clear()

    visiblePois.forEach { poi ->
        val lat = poi.lat ?: return@forEach
        val lon = poi.lon ?: return@forEach
        val position = LatLng(lat, lon)
        val snippet = poi.address.ifBlank { poi.category }
        val markerHue = if (poi.id == highlightPoiId) {
            BitmapDescriptorFactory.HUE_ORANGE
        } else {
            BitmapDescriptorFactory.HUE_RED
        }
        val marker = travelMarkers[poi.id]
        if (marker == null) {
            val newMarker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(poi.name)
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(markerHue))
            ) ?: return@forEach
            travelMarkers[poi.id] = newMarker
            markerPoiIndex[newMarker.id] = poi
        } else {
            marker.position = position
            marker.title = poi.name
            marker.snippet = snippet
            marker.setIcon(BitmapDescriptorFactory.defaultMarker(markerHue))
            markerPoiIndex[marker.id] = poi
        }
    }

    val signature = buildString {
        append(highlightPoiId.orEmpty())
        append("::")
        currentLocation?.latLng?.let {
            append("me@")
            append(it.latitude)
            append(',')
            append(it.longitude)
            append('|')
        }
        travelPois.forEach { poi ->
            append(poi.id)
            append('@')
            append(poi.lat ?: "")
            append(',')
            append(poi.lon ?: "")
            append('|')
        }
    }
    if (travelMarkers.isEmpty()) {
        lastAutoFocusSignature[0] = null
        return
    }
    if (signature == lastAutoFocusSignature[0]) return

    val highlightMarker = highlightPoiId?.let { target ->
        travelMarkers[target]
    }
    when {
        highlightMarker != null -> {
            focusLatLngWithOffset(map, highlightMarker.position)
        }
        travelMarkers.size == 1 -> {
            val singleMarker = travelMarkers.values.first()
            val currentLatLng = currentLocation?.latLng
            if (currentLatLng != null && isLocationNearTravelPois(currentLatLng, listOf(singleMarker.position))) {
                val boundsBuilder = LatLngBounds.builder()
                boundsBuilder.include(singleMarker.position)
                boundsBuilder.include(currentLatLng)
                focusBoundsWithOffset(
                    map = map,
                    bounds = boundsBuilder.build(),
                    mapWidth = mapWidth,
                    mapHeight = mapHeight,
                )
            } else {
                focusLatLngWithOffset(map, singleMarker.position)
            }
        }
        else -> {
            val boundsBuilder = LatLngBounds.builder()
            travelMarkers.values.forEach { boundsBuilder.include(it.position) }
            val currentLatLng = currentLocation?.latLng
            if (currentLatLng != null && isLocationNearTravelPois(currentLatLng, travelMarkers.values.map { it.position })) {
                boundsBuilder.include(currentLatLng)
            }
            focusBoundsWithOffset(
                map = map,
                bounds = boundsBuilder.build(),
                mapWidth = mapWidth,
                mapHeight = mapHeight,
            )
        }
    }
    lastAutoFocusSignature[0] = signature
}

private fun isLocationNearTravelPois(
    currentLocation: LatLng,
    poiPositions: List<LatLng>,
    thresholdKm: Double = 25.0,
): Boolean {
    if (poiPositions.isEmpty()) return false
    return poiPositions.all { poi -> distanceKm(currentLocation, poi) <= thresholdKm }
}

private fun distanceKm(from: LatLng, to: LatLng): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(to.latitude - from.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val originLat = Math.toRadians(from.latitude)
    val targetLat = Math.toRadians(to.latitude)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(originLat) * kotlin.math.cos(targetLat) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return earthRadiusKm * c
}

private fun clearDestinationMarker(destinationMarker: Array<Marker?>) {
    destinationMarker[0]?.remove()
    destinationMarker[0] = null
}

private fun List<TravelPoi>.preferredPoi(highlightPoiId: String?): TravelPoi? {
    val normalized = filter { it.lat != null && it.lon != null }
    if (normalized.isEmpty()) return null
    return highlightPoiId?.let { target -> normalized.firstOrNull { it.id == target } } ?: normalized.first()
}

private fun TravelPoi.toLocationPoint(): LocationPoint {
    return LocationPoint(
        latLng = LatLng(requireNotNull(lat), requireNotNull(lon)),
        title = name,
        snippet = address.ifBlank { category },
    )
}

@Composable
private fun ChatMapOverlays(
    uiState: MapUiState,
    onRelocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        uiState.currentLocation?.snippet
            ?.takeIf { it.isNotBlank() && it != DEVICE_LOCATION_LABEL }
            ?.let { address ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                tonalElevation = 2.dp,
            ) {
                Text(
                    text = address,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (uiState.locating) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text(text = stringResource(R.string.chat_map_locating))
                }
            }
        }

        SmallFloatingActionButton(
            onClick = onRelocate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(
                imageVector = Lucide.LocateFixed,
                contentDescription = stringResource(R.string.chat_map_relocate),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ChatMapBottomPanel(
    uiState: MapUiState,
    actions: MapActions,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Lucide.LocateFixed,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = uiState.currentLocation?.snippet
                        ?: uiState.locationError
                        ?: stringResource(R.string.chat_map_locating),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Lucide.MapPin,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (uiState.destination != null) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = uiState.selectedPoi?.let { poi ->
                        listOf(poi.name, poi.address.takeIf { it.isNotBlank() })
                            .filterNotNull()
                            .joinToString(" · ")
                    }
                        ?: uiState.destination?.title
                        ?: uiState.destination?.snippet
                        ?: uiState.destination?.let {
                            String.format(Locale.US, "%.6f, %.6f", it.latLng.latitude, it.latLng.longitude)
                        }
                        ?: stringResource(R.string.chat_map_select_destination_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.destination != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = actions.clearDestination,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.destination != null,
                ) {
                    Text(text = stringResource(R.string.chat_map_clear_destination))
                }
                Button(
                    onClick = actions.navigate,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.destination != null,
                ) {
                    Icon(
                        imageVector = Lucide.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = stringResource(R.string.chat_map_start_navigation))
                }
            }
        }
    }
}

private fun configureLocationClient(locationClient: AMapLocationClient) {
    val allowMockLocation = isProbablyRunningOnEmulator()
    locationClient.setLocationOption(
        AMapLocationClientOption().apply {
            isOnceLocation = true
            isOnceLocationLatest = true
            isNeedAddress = true
            isGpsFirst = !allowMockLocation
            gpsFirstTimeout = 15_000
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            locationPurpose = AMapLocationClientOption.AMapLocationPurpose.Transport
            httpTimeOut = 15_000
            isLocationCacheEnable = false
            isMockEnable = allowMockLocation
        }
    )
    Log.i(TAG, "configureLocationClient allowMockLocation=$allowMockLocation")
}

private fun buildLocationPoint(location: AMapLocation): LocationPoint {
    val coordinateText = String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude)
    return LocationPoint(
        latLng = LatLng(location.latitude, location.longitude),
        title = location.poiName,
        snippet = buildString {
            append("设备定位")
            location.poiName?.takeIf { it.isNotBlank() }?.let {
                if (isNotBlank()) append(" 路 ")
                append(it)
            }
            location.address?.takeIf { it.isNotBlank() }?.let {
                if (isNotBlank()) append(" · ")
                append(it)
            }
        }.ifBlank { "$DEVICE_LOCATION_LABEL · $coordinateText" },
    )
}

private fun isProbablyRunningOnEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase(Locale.US)
    val model = Build.MODEL.lowercase(Locale.US)
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
    val brand = Build.BRAND.lowercase(Locale.US)
    val device = Build.DEVICE.lowercase(Locale.US)
    val product = Build.PRODUCT.lowercase(Locale.US)
    return fingerprint.startsWith("generic") ||
        fingerprint.contains("emulator") ||
        fingerprint.contains("virtual") ||
        model.contains("sdk_gphone") ||
        model.contains("emulator") ||
        model.contains("android sdk built for") ||
        manufacturer.contains("genymotion") ||
        (brand.startsWith("generic") && device.startsWith("generic")) ||
        product.contains("sdk") ||
        product.contains("emulator") ||
        product.contains("simulator")
}

private fun focusLatLngWithOffset(
    map: AMap,
    latLng: LatLng,
    zoom: Float = 14f,
    verticalOffsetPx: Float = 96f,
) {
    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
    map.animateCamera(CameraUpdateFactory.scrollBy(0f, verticalOffsetPx))
}

private fun focusBoundsWithOffset(
    map: AMap,
    bounds: LatLngBounds,
    mapWidth: Int,
    mapHeight: Int,
) {
    val safeWidth = mapWidth.coerceAtLeast(1)
    val safeHeight = mapHeight.coerceAtLeast(1)
    val padding = (safeWidth.coerceAtMost(safeHeight) * 0.12f).toInt().coerceAtLeast(96)
    val verticalOffset = (safeHeight * 0.14f).coerceAtLeast(72f)
    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, safeWidth, safeHeight, padding))
    map.animateCamera(CameraUpdateFactory.scrollBy(0f, verticalOffset))
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }

    DisposableEffect(lifecycleOwner, mapView) {
        var created = false
        var destroyed = false

        fun ensureCreated() {
            if (!created && !destroyed) {
                mapView.onCreate(Bundle())
                created = true
            }
        }

        fun destroyMapView() {
            if (!destroyed) {
                if (created) {
                    mapView.onPause()
                    mapView.onDestroy()
                }
                destroyed = true
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> ensureCreated()
                Lifecycle.Event.ON_RESUME -> {
                    ensureCreated()
                    if (!destroyed) {
                        mapView.onResume()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (created && !destroyed) {
                        mapView.onPause()
                    }
                }
                Lifecycle.Event.ON_DESTROY -> destroyMapView()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            ensureCreated()
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !destroyed) {
            mapView.onResume()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            destroyMapView()
        }
    }

    return mapView
}

private fun buildNavigationUri(
    sourceLat: Double?,
    sourceLon: Double?,
    destLat: Double,
    destLon: Double,
    destinationName: String,
    applicationName: String,
): String {
    val encodedName = URLEncoder.encode(applicationName, Charsets.UTF_8.name())
    val base = StringBuilder("androidamap://route?sourceApplication=")
        .append(encodedName)
        .append("&dlat=").append(destLat)
        .append("&dlon=").append(destLon)
        .append("&dname=").append(URLEncoder.encode(destinationName, Charsets.UTF_8.name()))
        .append("&dev=0&t=0")
    if (sourceLat != null && sourceLon != null) {
        base.append("&slat=").append(sourceLat)
        base.append("&slon=").append(sourceLon)
        base.append("&sname=").append(URLEncoder.encode("我的位置", Charsets.UTF_8.name()))
    }
    return base.toString()
}

private fun buildWebNavigationUrl(
    sourceLat: Double?,
    sourceLon: Double?,
    destLat: Double,
    destLon: Double,
    destinationName: String,
    applicationName: String,
): String {
    val to = URLEncoder.encode("$destLon,$destLat,$destinationName", Charsets.UTF_8.name())
    val from = if (sourceLat != null && sourceLon != null) {
        "&from=" + URLEncoder.encode("$sourceLon,$sourceLat,我的位置", Charsets.UTF_8.name())
    } else {
        ""
    }
    val src = URLEncoder.encode(applicationName, Charsets.UTF_8.name())
    return "https://uri.amap.com/navigation?to=$to$from&mode=car&policy=1&coordinate=gaode&callnative=0&src=$src"
}
