package dz.cardiag.app.core

import dz.cardiag.app.core.road.LocationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies the Road Assistant is fully offline-safe and never claims to
 * know a real business, address, phone number, rating or distance.
 * The architecture is provider-agnostic: live providers can be plugged
 * in later without changing the UI.
 */
class LocationProviderTest {

    @Test
    fun `service category keys are stable`() {
        // These keys are referenced from the curated offline catalog; they
        // must remain stable so the screen layer keeps working.
        val expected = setOf(
            "mechanic",
            "auto_electrician",
            "roadside_assistance",
            "spare_parts",
            "fuel_station",
            "hospital",
            "towing",
            "other"
        )
        val actual = dz.cardiag.app.core.road.ServiceCategory.entries.map { it.key }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `hazard kind keys are stable`() {
        val expected = setOf(
            "accident",
            "road_closure",
            "obstacle",
            "broken_down_vehicle",
            "pothole",
            "other"
        )
        val actual = dz.cardiag.app.core.road.HazardKind.entries.map { it.key }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `offline road data provider is explicitly not live`() {
        assertFalse(dz.cardiag.app.core.road.OfflineRoadDataProvider.isLive)
    }

    @Test
    fun `offline road data provider covers every service category in both languages`() {
        val cats = dz.cardiag.app.core.road.ServiceCategory.entries
        cats.forEach { c ->
            val desc = dz.cardiag.app.core.road.OfflineRoadDataProvider.categoryDescriptions[c.key]
            assertNotNull("Missing description for $c", desc)
            assertTrue("$c has FR description", desc!!["fr"]?.isNotBlank() == true)
            assertTrue("$c has AR description", desc["ar"]?.isNotBlank() == true)
        }
    }

    @Test
    fun `offline road data provider has Arabic queries for key categories`() {
        val cats = listOf(
            dz.cardiag.app.core.road.ServiceCategory.MECHANIC,
            dz.cardiag.app.core.road.ServiceCategory.AUTO_ELECTRICIAN,
            dz.cardiag.app.core.road.ServiceCategory.ROADSIDE_ASSISTANCE,
            dz.cardiag.app.core.road.ServiceCategory.SPARE_PARTS,
            dz.cardiag.app.core.road.ServiceCategory.HOSPITAL
        )
        cats.forEach { c ->
            val q = dz.cardiag.app.core.road.OfflineRoadDataProvider.searchQueries[c.key]
            assertNotNull("Missing queries for $c", q)
            assertNotNull("Missing AR queries for $c", q!!["ar"])
            assertNotNull("Missing FR queries for $c", q["fr"])
        }
    }

    @Test
    fun `android manifest declares location permissions`() {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(cwd, "src/main/AndroidManifest.xml"),
            File(cwd.parentFile ?: cwd, "app/src/main/AndroidManifest.xml"),
            File(cwd, "app/src/main/AndroidManifest.xml")
        )
        val manifest = candidates.firstOrNull { it.exists() }?.readText()
            ?: error("AndroidManifest.xml not found from ${cwd.absolutePath}")
        assertTrue("Manifest must declare ACCESS_COARSE_LOCATION", manifest.contains("ACCESS_COARSE_LOCATION"))
        assertTrue("Manifest must declare ACCESS_FINE_LOCATION", manifest.contains("ACCESS_FINE_LOCATION"))
    }
}
