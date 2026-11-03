package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.GpsPoint
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.LimeGreenPrimary

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeafletMapView(
    modifier: Modifier = Modifier,
    points: List<GpsPoint>,
    currentPoint: GpsPoint?,
    isLiveTracking: Boolean = true,
    showEndMarker: Boolean = false,
    onMapReady: (() -> Unit)? = null
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isWebLoaded by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    // Send latest point update to WebView Leaflet JS
    LaunchedEffect(currentPoint, isWebLoaded) {
        if (isWebLoaded && currentPoint != null && webViewInstance != null) {
            val js = if (isLiveTracking) {
                "if (typeof addPoint === 'function') { addPoint(${currentPoint.latitude}, ${currentPoint.longitude}); }"
            } else {
                "if (typeof updateUserPosition === 'function') { updateUserPosition(${currentPoint.latitude}, ${currentPoint.longitude}); }"
            }
            webViewInstance?.evaluateJavascript(js, null)
        }
    }

    // Update full route on completed run or history view
    LaunchedEffect(points.size, isWebLoaded) {
        if (isWebLoaded && points.isNotEmpty() && webViewInstance != null) {
            val jsonArray = GpsPoint.listToJsonString(points)
            val js = "if (typeof setFullRoute === 'function') { setFullRoute($jsonArray, $showEndMarker); }"
            webViewInstance?.evaluateJavascript(js, null)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        allowFileAccess = true
                        allowContentAccess = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isWebLoaded = true
                            onMapReady?.invoke()
                            // If points exist already, draw them immediately
                            if (points.isNotEmpty()) {
                                val jsonArray = GpsPoint.listToJsonString(points)
                                view?.evaluateJavascript(
                                    "if (typeof setFullRoute === 'function') { setFullRoute($jsonArray, $showEndMarker); }",
                                    null
                                )
                            } else if (currentPoint != null) {
                                view?.evaluateJavascript(
                                    "if (typeof initMap === 'function') { initMap(${currentPoint.latitude}, ${currentPoint.longitude}, 16); }",
                                    null
                                )
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            hasError = true
                        }
                    }
                    loadUrl("file:///android_asset/leaflet_map.html")
                    webViewInstance = this
                }
            },
            update = {
                webViewInstance = it
            }
        )

        // Offline Route Fallback Canvas if WebView has error or map tiles cannot connect
        if (hasError && points.size >= 2) {
            OfflineRouteCanvas(
                points = points,
                modifier = Modifier.fillMaxSize()
            )
        }

        // OSM Attribution Tag
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp),
            color = Color(0x99000000),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = "© OpenStreetMap • Leaflet",
                color = Color.LightGray,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * High performance hardware-accelerated Canvas fallback when offline
 */
@Composable
fun OfflineRouteCanvas(
    points: List<GpsPoint>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(DarkBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            if (points.size < 2) return@Canvas

            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE

            for (p in points) {
                if (p.latitude < minLat) minLat = p.latitude
                if (p.latitude > maxLat) maxLat = p.latitude
                if (p.longitude < minLon) minLon = p.longitude
                if (p.longitude > maxLon) maxLon = p.longitude
            }

            val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
            val lonSpan = (maxLon - minLon).coerceAtLeast(0.0001)

            val path = Path()
            points.forEachIndexed { index, p ->
                val x = (((p.longitude - minLon) / lonSpan) * size.width).toFloat()
                // Invert Y because latitude grows northwards
                val y = ((1.0 - (p.latitude - minLat) / latSpan) * size.height).toFloat()

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            // Draw Glow
            drawPath(
                path = path,
                color = Color(0x33A3E635),
                style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Draw Line
            drawPath(
                path = path,
                color = LimeGreenPrimary,
                style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Draw Start Point
            val startX = (((points.first().longitude - minLon) / lonSpan) * size.width).toFloat()
            val startY = ((1.0 - (points.first().latitude - minLat) / latSpan) * size.height).toFloat()
            drawCircle(
                color = Color(0xFF22C55E),
                radius = 10f,
                center = Offset(startX, startY)
            )

            // Draw Current / End Point
            val endX = (((points.last().longitude - minLon) / lonSpan) * size.width).toFloat()
            val endY = ((1.0 - (points.last().latitude - minLat) / latSpan) * size.height).toFloat()
            drawCircle(
                color = LimeGreenPrimary,
                radius = 12f,
                center = Offset(endX, endY)
            )
        }

        Text(
            text = "Mode Visual Offline (GPS Aktif)",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        )
    }
}
