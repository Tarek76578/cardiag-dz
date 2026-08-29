package dz.cardiag.app.core.road

/**
 * Curated, generic road-assistant content.
 *
 * The Road Assistant must NOT fabricate addresses, phone numbers, ratings or
 * "open now" status for real businesses. Instead, the offline provider
 * returns the *category* descriptions and helpful generic guidance, so the
 * user can browse what kinds of help are usually available and hand off to a
 * map / search app to find the closest real option for their position.
 *
 * Live providers can be plugged in via [NearbySearchProvider] and
 * [HazardsProvider] without changing the UI layer.
 */
object OfflineRoadDataProvider {

    val providerDisplayName: String = "CarDiag DZ curated catalog"
    val isLive: Boolean = false

    /**
     * Generic descriptions per category, kept in French and Arabic.
     * Keys are [ServiceCategory.key]. The screen layer resolves the
     * description for the user's language.
     */
    val categoryDescriptions: Map<String, Map<String, String>> = mapOf(
        ServiceCategory.MECHANIC.key to mapOf(
            "fr" to "Atelier ou garage capable de diagnostiquer et réparer votre véhicule.",
            "ar" to "ورشة أو مرآب لتشخيص وإصلاح سيارتك."
        ),
        ServiceCategory.AUTO_ELECTRICIAN.key to mapOf(
            "fr" to "Spécialiste de l'électricité automobile : démarreur, alternateur, faisceau, ECU.",
            "ar" to "متخصص في كهرباء السيارات: بادئ الحركة، المولّد، الضفائر، وحدة التحكم."
        ),
        ServiceCategory.ROADSIDE_ASSISTANCE.key to mapOf(
            "fr" to "Assistance dépannage : batterie, crevaison, remorquage.",
            "ar" to "مساعدة على الطريق: البطارية، ثقب العجل، السحب."
        ),
        ServiceCategory.SPARE_PARTS.key to mapOf(
            "fr" to "Magasin de pièces détachées automobiles.",
            "ar" to "متجر لقطع غيار السيارات."
        ),
        ServiceCategory.FUEL_STATION.key to mapOf(
            "fr" to "Station-service pour carburant.",
            "ar" to "محطة وقود للتزود بالوقود."
        ),
        ServiceCategory.HOSPITAL.key to mapOf(
            "fr" to "Hôpital ou service d'urgence médicale.",
            "ar" to "مستشفى أو خدمة طوارئ طبية."
        ),
        ServiceCategory.TOWING.key to mapOf(
            "fr" to "Service de remorquage.",
            "ar" to "خدمة سحب السيارات."
        ),
        ServiceCategory.OTHER.key to mapOf(
            "fr" to "Service automobile généraliste.",
            "ar" to "خدمة سيارات عامة."
        )
    )

    /** Generic search-query templates per category, Arabic + French. */
    val searchQueries: Map<String, Map<String, List<String>>> = mapOf(
        ServiceCategory.MECHANIC.key to mapOf(
            "fr" to listOf("garage", "mécanicien", "atelier automobile", "mécanique"),
            "ar" to listOf("ميكانيكي", "ميكانيكي سيارات", "ورشة", "مرآب")
        ),
        ServiceCategory.AUTO_ELECTRICIAN.key to mapOf(
            "fr" to listOf("électricien automobile", "auto électricien", "diagnostic électronique"),
            "ar" to listOf("كهربائي سيارات", "كهربائي", "تشخيص إلكتروني")
        ),
        ServiceCategory.ROADSIDE_ASSISTANCE.key to mapOf(
            "fr" to listOf("dépannage", "assistance routière", "remorquage"),
            "ar" to listOf("مساعدة على الطريق", "سحب", "إسعاف الطريق")
        ),
        ServiceCategory.SPARE_PARTS.key to mapOf(
            "fr" to listOf("pièces détachées", "magasin pièces auto", "pneus"),
            "ar" to listOf("قطع غيار", "متجر قطع غيار", "إطارات")
        ),
        ServiceCategory.FUEL_STATION.key to mapOf(
            "fr" to listOf("station service", "station essence", "GPL", "GNV"),
            "ar" to listOf("محطة وقود", "بنزين", "غاز")
        ),
        ServiceCategory.HOSPITAL.key to mapOf(
            "fr" to listOf("hôpital", "urgences", "clinique"),
            "ar" to listOf("مستشفى", "طوارئ", "عيادة")
        ),
        ServiceCategory.TOWING.key to mapOf(
            "fr" to listOf("remorquage", "dépanneuse"),
            "ar" to listOf("سحب", "ونش")
        )
    )

    /** Generic, safety-conscious road-hazard descriptions. */
    val hazardDescriptions: List<RoadHazardTemplate> = listOf(
        RoadHazardTemplate(
            kind = HazardKind.POTHOLE,
            fr = "Nid-de-poule signalé à proximité",
            ar = "تم الإبلاغ عن حفرة في الطريق قريبة"
        ),
        RoadHazardTemplate(
            kind = HazardKind.BROKEN_DOWN_VEHICLE,
            fr = "Véhicule en panne signalé",
            ar = "تم الإبلاغ عن سيارة متعطلة"
        ),
        RoadHazardTemplate(
            kind = HazardKind.ACCIDENT,
            fr = "Accident signalé dans le secteur",
            ar = "تم الإبلاغ عن حادث في المنطقة"
        ),
        RoadHazardTemplate(
            kind = HazardKind.ROAD_CLOSURE,
            fr = "Route barrée ou partiellement fermée",
            ar = "طريق مغلقة أو مغلقة جزئيا"
        )
    )
}

data class RoadHazardTemplate(
    val kind: HazardKind,
    val fr: String,
    val ar: String
)
