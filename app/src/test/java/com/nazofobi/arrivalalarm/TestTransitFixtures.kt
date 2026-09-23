package com.nazofobi.arrivalalarm

object TestGermanyTransitFixtures {
    val snapshot=CatalogSnapshot(
        providers=listOf(
            TransitProvider("delfi","DELFI / GTFS Deutschland","Germany","https://gtfs.de/en/feeds/de_full/","Creative Commons 4.0","fixture","fixture",true,"https://www.gtfs.de/de/realtime/"),
            TransitProvider("vbb","VBB","Berlin-Brandenburg","fixture","Open Data","fixture","fixture",true),
            TransitProvider("mvv","MVV","Munich region","fixture","CC BY","fixture","fixture",false)),
        stops=listOf(CatalogStop("de:hb:hbf","delfi","Bremen Hbf",53.0834,8.8137),CatalogStop("de:hb:airport","delfi","Flughafen Bremen",53.0531,8.7866),CatalogStop("de:be:hbf","vbb","Berlin Hauptbahnhof",52.5251,13.3694),CatalogStop("de:be:alex","vbb","Alexanderplatz",52.5219,13.4132),CatalogStop("de:by:mhbf","mvv","München Hauptbahnhof",48.1402,11.5586),CatalogStop("de:by:marien","mvv","Marienplatz",48.1372,11.5755)),
        routes=listOf(CatalogRoute("r-hb-6","delfi","6","Flughafen",listOf("de:hb:hbf","de:hb:airport")),CatalogRoute("r-be-s5","vbb","S5","Strausberg Nord",listOf("de:be:hbf","de:be:alex")),CatalogRoute("r-by-s8","mvv","S8","Herrsching",listOf("de:by:mhbf","de:by:marien"))),
        trips=listOf(CatalogTrip("t-hb-6-1200","delfi","r-hb-6","12:00"),CatalogTrip("t-be-s5-1205","vbb","r-be-s5","12:05"),CatalogTrip("t-by-s8-1210","mvv","r-by-s8","12:10")),generatedAt="fixture")
}

class TestFixtureStaticTransitRepository : StaticTransitRepository {
    private val hbf = TransitStop("fixture-bremen-hbf", "Bremen Hbf")
    private val amBrill = TransitStop("fixture-am-brill", "Am Brill")
    private val airport = TransitStop("fixture-airport", "Flughafen Bremen")
    private val stops = listOf(hbf, amBrill, airport)

    override fun searchStops(query: String): List<TransitStop> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()
        return stops.filter { it.name.contains(normalized, ignoreCase = true) }
    }

    override fun plan(startId: String, destinationId: String): TransitJourney? {
        val start = stops.firstOrNull { it.id == startId } ?: return null
        val destination = stops.firstOrNull { it.id == destinationId } ?: return null
        if (start == destination) return null
        val ordered = when {
            start == hbf && destination == airport -> listOf(hbf, amBrill, airport)
            start == airport && destination == hbf -> listOf(airport, amBrill, hbf)
            else -> listOf(start, destination)
        }
        return TransitJourney(start, destination, listOf(TransitLeg("Fixture 6", destination.name, ordered)))
    }
}
