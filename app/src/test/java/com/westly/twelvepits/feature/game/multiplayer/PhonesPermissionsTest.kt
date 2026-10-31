package com.westly.twelvepits.feature.game.multiplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonesPermissionsTest {
    private val bluetooth = listOf(
        PhonesPermissions.BLUETOOTH_ADVERTISE,
        PhonesPermissions.BLUETOOTH_CONNECT,
        PhonesPermissions.BLUETOOTH_SCAN,
    )

    @Test
    fun android14AsksForNearbyDevicesOnly() {
        assertEquals(bluetooth + PhonesPermissions.NEARBY_WIFI_DEVICES, PhonesPermissions.required(34))
        assertEquals(bluetooth + PhonesPermissions.NEARBY_WIFI_DEVICES, PhonesPermissions.required(33))
        assertEquals(bluetooth + PhonesPermissions.NEARBY_WIFI_DEVICES, PhonesPermissions.required(36))
    }

    @Test
    fun android12LAsksForTheThreeBluetoothPermissions() {
        assertEquals(bluetooth, PhonesPermissions.required(32))
    }

    @Test
    fun android12AlsoAsksForLocation() {
        assertEquals(bluetooth + PhonesPermissions.ACCESS_FINE_LOCATION, PhonesPermissions.required(31))
    }

    @Test
    fun android10And11AskForFineLocation() {
        assertEquals(listOf(PhonesPermissions.ACCESS_FINE_LOCATION), PhonesPermissions.required(30))
        assertEquals(listOf(PhonesPermissions.ACCESS_FINE_LOCATION), PhonesPermissions.required(29))
    }

    @Test
    fun android8And9AskForCoarseLocation() {
        assertEquals(listOf(PhonesPermissions.ACCESS_COARSE_LOCATION), PhonesPermissions.required(28))
        assertEquals(listOf(PhonesPermissions.ACCESS_COARSE_LOCATION), PhonesPermissions.required(26))
    }

    @Test
    fun theLocationSwitchMattersOnlyOnAndroid11AndOlder() {
        assertTrue(PhonesPermissions.needsLocationSwitch(26))
        assertTrue(PhonesPermissions.needsLocationSwitch(30))
        assertFalse(PhonesPermissions.needsLocationSwitch(31))
        assertFalse(PhonesPermissions.needsLocationSwitch(34))
    }

    @Test
    fun theLocationWordingIsUsedWhereAndroidShowsLocation() {
        assertTrue(PhonesPermissions.isLocationBased(30))
        assertTrue(PhonesPermissions.isLocationBased(31))
        assertFalse(PhonesPermissions.isLocationBased(32))
        assertFalse(PhonesPermissions.isLocationBased(34))
    }
}
