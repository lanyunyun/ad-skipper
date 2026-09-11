package com.adskipper.core.detect

/**
 * Decides whether a foreground package is a **system surface** — the launcher,
 * the share sheet, a permission dialog, the notification shade, the recent-apps
 * switcher … — where a splash ad never appears and any keyword or image hit is
 * a false positive that ends up tapping around in unrelated UI.
 *
 * Why this is a separate, always-on gate instead of an entry in the user
 * whitelist: the whitelist is persisted on first launch, so entries added to
 * [com.adskipper.core.data.AppSettings.DEFAULT_WHITELIST] by a later version
 * never reach an existing install (see the "whitelist never receive new
 * defaults" note in AdSkipperService). The share sheet is exactly such a miss:
 * it runs in the system process (`android` / com.android.intentresolver), the
 * launcher whitelist does not cover it, so the service treated "first event
 * after a gap" as a splash window and started polling the share sheet.
 *
 * Two rules, cheapest first:
 *
 *  1. [BUILTIN] and any user-configured package are system surfaces.
 *  2. A package with **no launcher activity** cannot be an app the user
 *     launches from the home screen, so it is a system/overlay surface.
 *
 * Rule 2 only blocks on a *positive* "there is no launcher activity" answer.
 * When the probe cannot tell (`null` — e.g. package visibility restrictions)
 * the package is allowed through, so a probe failure can never silently
 * disable ad skipping for a real app.
 */
class SystemSurfaceGuard(
    userPackages: Set<String> = emptySet(),
    private val launcherActivityProbe: (String) -> Boolean? = { null },
) {
    private val user: Set<String> = userPackages.toSet()
    private val probeCache = HashMap<String, Boolean?>()

    fun isSystemSurface(pkg: String): Boolean {
        if (pkg.isBlank()) return true
        if (pkg in BUILTIN || pkg in user) return true
        return hasLauncherActivity(pkg) == false
    }

    private fun hasLauncherActivity(pkg: String): Boolean? = synchronized(probeCache) {
        if (probeCache.containsKey(pkg)) return@synchronized probeCache[pkg]
        val probed = try {
            launcherActivityProbe(pkg)
        } catch (t: Throwable) {
            null
        }
        probeCache[pkg] = probed
        probed
    }

    /** Drop cached probe answers (call after an install/uninstall). */
    fun invalidate() = synchronized(probeCache) { probeCache.clear() }

    companion object {
        /**
         * Packages that never own a normal app window: the system process
         * itself (share sheet / resolver, permission dialogs), the system UI
         * (notification shade, recents, volume panel), the AOSP intent
         * resolver, permission controllers, package installers and the
         * system file picker.
         */
        val BUILTIN: Set<String> = setOf(
            "android",
            "com.android.systemui",
            "com.android.intentresolver",
            "com.android.internal.app",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.documentsui",
        )
    }
}
