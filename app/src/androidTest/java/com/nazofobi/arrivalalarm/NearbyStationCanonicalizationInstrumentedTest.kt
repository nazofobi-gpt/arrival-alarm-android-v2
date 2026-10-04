package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NearbyStationCanonicalizationInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun cleanup() { context.deleteDatabase("nationwide_transit.db") }

    @Test
    fun nationwideNearestCollapsesPlatformsToParentStation() {
        context.deleteDatabase("nationwide_transit.db")
        val index = NationwideTransitIndex(context)
        val db = index.writableDatabase
        db.execSQL("INSERT INTO stops(id,name,lat,lon,parent_station,location_type) VALUES('station','Lohne Bahnhof',52.665,8.237,NULL,1)")
        db.execSQL("INSERT INTO stops(id,name,lat,lon,parent_station,location_type) VALUES('p1','Lohne Bahnhof',52.66501,8.23701,'station',0)")
        db.execSQL("INSERT INTO stops(id,name,lat,lon,parent_station,location_type) VALUES('p2','Lohne Bahnhof',52.66502,8.23702,'station',0)")

        val result = index.nearest(52.6645, 8.237, 5)

        assertEquals(1, result.size)
        assertEquals("station", result.single().stop.id)
        assertNotNull(result.single().bearingDegrees)
        index.close()
    }
}
