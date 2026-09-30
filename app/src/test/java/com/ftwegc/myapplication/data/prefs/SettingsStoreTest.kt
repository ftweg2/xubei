package com.ftwegc.myapplication.data.prefs

import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStoreTest {

    @Test
    fun emptyPreferences_giveDefaults() {
        val s = SettingsStore.read(emptyPreferences())
        assertEquals(AppSettings(), s)
        assertEquals(SortOrder.DUE_DATE, s.sortOrder)
        assertEquals(listOf(7, 3, 1, 0), s.reminderDays)
        assertEquals(7, s.dueSoonDays)
        assertEquals("CNY", s.defaultCurrency)
        assertEquals(80, s.trafficAlertPercent)
    }

    @Test
    fun reminderDays_codec_normalizes() {
        assertEquals("7,3,1,0", SettingsStore.encodeDays(listOf(0, 1, 3, 7, 3)))
        assertEquals(listOf(14, 2), SettingsStore.decodeDays(" 2, x, 14 ,-1, 999"))
        assertEquals(emptyList<Int>(), SettingsStore.decodeDays(""))
    }

    @Test
    fun appearance_defaultsToDarkWithMovingBackground_andIsStored() = kotlinx.coroutines.test.runTest {
        val defaults = SettingsStore.read(emptyPreferences())
        assertEquals(ThemeMode.DARK, defaults.themeMode)
        assertEquals(true, defaults.backgroundAnimation)
        val store = SettingsStore(com.ftwegc.myapplication.data.repo.FakePreferencesDataStore())
        store.setThemeMode(ThemeMode.LIGHT)
        store.setBackgroundAnimation(false)
        store.setReduceTransparency(true)
        val read = store.settings.first()
        assertEquals(ThemeMode.LIGHT, read.themeMode)
        assertEquals(false, read.backgroundAnimation)
        assertEquals(true, read.reduceTransparency)
        store.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, store.settings.first().themeMode)
    }

    @Test
    fun dueSoonDays_followsLargestReminder() {
        assertEquals(14, AppSettings(reminderDays = listOf(14, 1)).dueSoonDays)
        assertEquals(1, AppSettings(reminderDays = listOf(0)).dueSoonDays)
        assertEquals(7, AppSettings(reminderDays = emptyList()).dueSoonDays)
    }
}
