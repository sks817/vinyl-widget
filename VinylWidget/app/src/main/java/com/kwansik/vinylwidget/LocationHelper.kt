package com.kwansik.vinylwidget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager

/**
 * 앱이 화면에 있을 때: 현재 위치를 새로 받아 저장
 * 백그라운드(30분 주기): 마지막 위치를 시도하고, 못 받으면 저장된 위치 사용
 */
object LocationHelper {

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun bestLocation(ctx: Context): Pair<Double, Double>? {
        lastKnown(ctx)?.let { WeatherStore.setLocation(ctx, it.latitude, it.longitude) }
        return WeatherStore.location(ctx)
    }

    private fun lastKnown(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java)
        return try {
            lm.getProviders(true)
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        } catch (e: SecurityException) {
            null
        }
    }

    fun requestCurrent(ctx: Context, done: () -> Unit) {
        if (!hasPermission(ctx)) {
            done()
            return
        }
        val lm = ctx.getSystemService(LocationManager::class.java)
        val provider = when {
            lm.hasProvider(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            else -> LocationManager.NETWORK_PROVIDER
        }
        try {
            lm.getCurrentLocation(provider, null, ctx.mainExecutor) { loc ->
                if (loc != null) WeatherStore.setLocation(ctx, loc.latitude, loc.longitude)
                done()
            }
        } catch (e: Exception) {
            done()
        }
    }
}
