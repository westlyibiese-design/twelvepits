package com.westly.twelvepits.feature.game.multiplayer

/**
 * The questions Android must ask before Nearby Connections may be used, for each Android version.
 * It follows the manifest list in Google's "Nearby Connections: get started" page.
 * Plain text names, so this file has no Android imports and is tested with plain unit tests.
 */
object PhonesPermissions {
    const val BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
    const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES"
    const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"

    /** The permissions that need a question on screen, on a phone with Android API level [sdkInt]. */
    fun required(sdkInt: Int): List<String> = when {
        sdkInt >= 33 -> listOf(BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT, BLUETOOTH_SCAN, NEARBY_WIFI_DEVICES)
        sdkInt == 32 -> listOf(BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT, BLUETOOTH_SCAN)
        sdkInt == 31 -> listOf(BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_FINE_LOCATION)
        sdkInt >= 29 -> listOf(ACCESS_FINE_LOCATION)
        else -> listOf(ACCESS_COARSE_LOCATION)
    }

    /** On Android 11 and older, finding other phones over Bluetooth only works while the Location switch is on. */
    fun needsLocationSwitch(sdkInt: Int): Boolean = sdkInt <= 30

    /** True when the question is shown by Android as "Location" and not as "Nearby devices". */
    fun isLocationBased(sdkInt: Int): Boolean = sdkInt <= 31
}
