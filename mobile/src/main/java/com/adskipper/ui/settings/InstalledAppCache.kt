package com.adskipper.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Installed-app list behind the whitelist picker, cached for the process
 * lifetime.
 *
 * Building this list costs ~1 s (queryIntentActivities + loadLabel per app) and
 * the picker used to re-run that query on every visit to the settings tab.
 * That was half of the tab's stutter; the other half was rendering every row at
 * once, which the LazyColumn in [SettingsScreen] now avoids. The cache is a
 * singleton, so switching tabs never re-queries.
 */
object InstalledAppCache {

    private val mutex = Mutex()
    private val _apps = MutableStateFlow<List<Pair<String, String>>?>(null)

    /** null while the first query is still running. */
    val apps: StateFlow<List<Pair<String, String>>?> = _apps.asStateFlow()

    suspend fun load(context: Context): List<Pair<String, String>> {
        _apps.value?.let { return it }
        return mutex.withLock {
            _apps.value?.let { return@withLock it }
            query(context.applicationContext).also { _apps.value = it }
        }
    }

    /** For an install/uninstall while the app is running. */
    suspend fun refresh(context: Context): List<Pair<String, String>> =
        mutex.withLock {
            query(context.applicationContext).also { _apps.value = it }
        }

    private suspend fun query(context: Context): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .sortedBy { it.second }
                .filter { it.first != context.packageName }
        }
}
