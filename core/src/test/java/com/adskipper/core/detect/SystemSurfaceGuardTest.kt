package com.adskipper.core.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemSurfaceGuardTest {

    @Test
    fun `builtin system packages are system surfaces`() {
        val guard = SystemSurfaceGuard()
        // The share sheet runs in the system process; the shade/recents in
        // systemui; both used to fall through the whitelist gate.
        assertTrue(guard.isSystemSurface("android"))
        assertTrue(guard.isSystemSurface("com.android.systemui"))
        assertTrue(guard.isSystemSurface("com.android.intentresolver"))
        assertTrue(guard.isSystemSurface("com.android.internal.app"))
        assertTrue(guard.isSystemSurface("com.android.permissioncontroller"))
        assertTrue(guard.isSystemSurface("com.google.android.permissioncontroller"))
    }

    @Test
    fun `package without a launcher activity is a system surface`() {
        // e.g. an OEM share sheet / security centre with no home-screen icon
        val guard = SystemSurfaceGuard { pkg -> pkg != "com.example.oem.share" }
        assertTrue(guard.isSystemSurface("com.example.oem.share"))
    }

    @Test
    fun `normal app is allowed through`() {
        val guard = SystemSurfaceGuard { true }
        assertFalse(guard.isSystemSurface("com.example.app"))
    }

    @Test
    fun `unknown probe result never blocks a real app`() {
        val guard = SystemSurfaceGuard { null }
        assertFalse(guard.isSystemSurface("com.example.app"))
    }

    @Test
    fun `throwing probe is treated as unknown`() {
        val guard = SystemSurfaceGuard { throw IllegalStateException("no package visibility") }
        assertFalse(guard.isSystemSurface("com.example.app"))
    }

    @Test
    fun `blank package is a system surface`() {
        assertTrue(SystemSurfaceGuard().isSystemSurface(""))
        assertTrue(SystemSurfaceGuard().isSystemSurface("   "))
    }

    @Test
    fun `user configured packages are system surfaces`() {
        val guard = SystemSurfaceGuard(userPackages = setOf("com.example.odd.launcher")) { true }
        assertTrue(guard.isSystemSurface("com.example.odd.launcher"))
        assertFalse(guard.isSystemSurface("com.example.other"))
    }

    @Test
    fun `probe result is cached per package`() {
        var calls = 0
        val guard = SystemSurfaceGuard {
            calls++
            true
        }
        guard.isSystemSurface("com.example.app")
        guard.isSystemSurface("com.example.app")
        guard.isSystemSurface("com.example.app")
        assertEquals(1, calls)
    }

    @Test
    fun `invalidate clears the probe cache`() {
        var calls = 0
        val guard = SystemSurfaceGuard {
            calls++
            false
        }
        guard.isSystemSurface("com.example.sheet")
        guard.invalidate()
        guard.isSystemSurface("com.example.sheet")
        assertEquals(2, calls)
    }
}
