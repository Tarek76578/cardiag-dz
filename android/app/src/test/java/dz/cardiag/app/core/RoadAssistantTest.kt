package dz.cardiag.app.core

import dz.cardiag.app.core.road.CoarseLocation
import dz.cardiag.app.core.road.HazardKind
import dz.cardiag.app.core.road.HazardsResult
import dz.cardiag.app.core.road.HazardsProvider
import dz.cardiag.app.core.road.LocationProvider
import dz.cardiag.app.core.road.NearbyResult
import dz.cardiag.app.core.road.NearbySearchProvider
import dz.cardiag.app.core.road.NearbyService
import dz.cardiag.app.core.road.OfflineRoadDataProvider
import dz.cardiag.app.core.road.RoadAssistantService
import dz.cardiag.app.core.road.RoadHazard
import dz.cardiag.app.core.road.ServiceCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadAssistantTest {

    private class FakeLocation(private val fix: CoarseLocation?) : LocationProvider {
        override suspend fun lastKnown(): CoarseLocation? = fix
        override suspend fun current(timeoutMs: Long): CoarseLocation? = fix
    }

    private class FixedNearby(val services: List<NearbyService>) : NearbySearchProvider {
        override val displayName: String = "fake"
        override val isLive: Boolean = true
        override suspend fun search(
            center: CoarseLocation,
            categories: Set<ServiceCategory>,
            radiusMeters: Int,
            language: String
        ): NearbyResult = NearbyResult.Success(services.filter { it.category in categories })
    }

    private class FixedHazards(val hazards: List<RoadHazard>) : HazardsProvider {
        override val displayName: String = "fake"
        override val isLive: Boolean = true
        override suspend fun fetch(center: CoarseLocation, language: String): HazardsResult =
            HazardsResult.Success(hazards)
    }

    private val algiers = CoarseLocation(36.7538, 3.0588, 50.0, 0L, "fake")

    @Test
    fun `offline provider never fabricates businesses`() = runBlocking {
        val provider = dz.cardiag.app.core.road.OfflineNearbyProvider()
        val outFr = provider.search(algiers, setOf(ServiceCategory.MECHANIC), 5000, "fr")
        val outAr = provider.search(algiers, setOf(ServiceCategory.MECHANIC), 5000, "ar")
        assertTrue(outFr is NearbyResult.Success)
        assertTrue(outAr is NearbyResult.Success)
        val fr = (outFr as NearbyResult.Success).services
        val ar = (outAr as NearbyResult.Success).services
        assertEquals(1, fr.size)
        assertEquals(1, ar.size)
        // Address/phone/rating/distance must be null to be honest with the user.
        assertNull(fr[0].address)
        assertNull(fr[0].phone)
        assertNull(fr[0].rating)
        assertNull(fr[0].distanceMeters)
        assertNull(ar[0].address)
        assertNull(ar[0].phone)
        assertNull(ar[0].rating)
        assertNull(ar[0].distanceMeters)
        // Description must be in the right language.
        assertTrue(fr[0].name.isNotBlank())
        assertTrue(ar[0].name.isNotBlank())
        assertNotNull(OfflineRoadDataProvider.categoryDescriptions[ServiceCategory.MECHANIC.key])
    }

    @Test
    fun `offline hazards provider returns no hazards`() = runBlocking {
        val provider = dz.cardiag.app.core.road.OfflineHazardsProvider()
        val out = provider.fetch(algiers, "fr")
        assertTrue(out is HazardsResult.Success)
        // Live hazard data must never be invented.
        assertEquals(0, (out as HazardsResult.Success).hazards.size)
        assertFalse(provider.isLive)
    }

    @Test
    fun `service snapshot aggregates providers`() = runBlocking {
        val fixed = listOf(
            NearbyService(
                id = "n1",
                name = "Garage A",
                category = ServiceCategory.MECHANIC,
                latitude = 36.7,
                longitude = 3.0,
                distanceMeters = 1200.0,
                address = null,
                phone = null,
                source = "fake"
            )
        )
        val service = RoadAssistantService(
            locationProvider = FakeLocation(algiers),
            nearbyProvider = FixedNearby(fixed),
            hazardsProvider = FixedHazards(emptyList())
        )
        val snap = service.snapshot(setOf(ServiceCategory.MECHANIC), 5000, "fr")
        assertNotNull(snap.location)
        assertEquals(1, snap.services.size)
        assertEquals(0, snap.hazards.size)
        assertTrue(snap.servicesLive)
        assertEquals(algiers.latitude, snap.location!!.latitude, 0.0)
    }

    @Test
    fun `service snapshot returns empty when location is unavailable`() = runBlocking {
        val service = RoadAssistantService(
            locationProvider = FakeLocation(null),
            nearbyProvider = FixedNearby(emptyList()),
            hazardsProvider = FixedHazards(emptyList())
        )
        val snap = service.snapshot(setOf(ServiceCategory.MECHANIC), 5000, "fr")
        assertNull(snap.location)
        assertEquals(0, snap.services.size)
        assertEquals(0, snap.hazards.size)
    }

    @Test
    fun `service category parsing accepts both supported and unknown keys`() {
        assertEquals(ServiceCategory.MECHANIC, ServiceCategory.fromKey("mechanic"))
        assertEquals(ServiceCategory.AUTO_ELECTRICIAN, ServiceCategory.fromKey("auto_electrician"))
        assertEquals(ServiceCategory.OTHER, ServiceCategory.fromKey("not_a_real_category"))
    }

    @Test
    fun `hazard kind parsing accepts both supported and unknown keys`() {
        assertEquals(HazardKind.ACCIDENT, HazardKind.fromKey("accident"))
        assertEquals(HazardKind.POTHOLE, HazardKind.fromKey("pothole"))
        assertEquals(HazardKind.OTHER_HAZARD, HazardKind.fromKey("definitely_not_real"))
    }

    @Test
    fun `arabic search queries contain localized terms`() {
        val queries = OfflineRoadDataProvider.searchQueries
        assertNotNull(queries[ServiceCategory.MECHANIC.key])
        val arQueries = queries[ServiceCategory.MECHANIC.key]!!["ar"]
        assertNotNull(arQueries)
        // Must include common Arabic automotive terms.
        assertTrue(arQueries!!.any { it.contains("ميكانيكي") })
    }

    @Test
    fun `french search queries contain localized terms`() {
        val queries = OfflineRoadDataProvider.searchQueries
        val frQueries = queries[ServiceCategory.MECHANIC.key]!!["fr"]
        assertNotNull(frQueries)
        assertTrue(frQueries!!.any { it.contains("mécanicien") || it.contains("garage") })
    }
}
