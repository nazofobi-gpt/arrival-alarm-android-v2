package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GtfsShapePolylineTest {
    private fun p(i: Int, lon: Double, distance: Double?) =
        GtfsScheduleShapePoint("test-shape", i, 52.0, lon, distance)

    @Test fun clipsToSelectedBoardingAndAlighting() {
        val points = listOf(p(1, 8.0, 0.0), p(2, 8.5, 5.0), p(3, 9.0, 10.0))
        val result = clipGtfsShapePolyline(points, 2.0, 8.0)
        assertEquals(3, result.size)
        assertEquals(8.2, result.first().longitude, 1e-9)
        assertEquals(8.8, result.last().longitude, 1e-9)
    }

    @Test fun missingOrInvalidEvidenceProducesNoLine() {
        val points = listOf(p(1, 8.0, 0.0), p(2, 9.0, 10.0))
        assertTrue(clipGtfsShapePolyline(points, null, 5.0).isEmpty())
        assertTrue(clipGtfsShapePolyline(points, 8.0, 2.0).isEmpty())
        assertTrue(clipGtfsShapePolyline(points, 2.0, 20.0).isEmpty())
        assertTrue(clipGtfsShapePolyline(points, 2.0, Double.NaN).isEmpty())
        assertTrue(clipGtfsShapePolyline(listOf(p(1, 8.0, 0.0), p(2, 9.0, null)), 1.0, 2.0).isEmpty())
    }

    @Test fun largeShapeIsBoundedButEndpointsRemainExact() {
        val points = (0..700).map { p(it + 1, 8.0 + it / 1000.0, it.toDouble()) }
        val result = clipGtfsShapePolyline(points, 50.5, 640.5, 40)
        assertEquals(40, result.size)
        assertEquals(8.0505, result.first().longitude, 1e-9)
        assertEquals(8.6405, result.last().longitude, 1e-9)
    }

    @Test fun nonMonotonicShapeIsNotRendered() {
        val points = listOf(p(1, 8.0, 0.0), p(2, 8.5, 7.0), p(3, 9.0, 4.0))
        assertTrue(clipGtfsShapePolyline(points, 1.0, 3.0).isEmpty())
    }
    @Test fun antimeridianJumpInsideSelectedSegmentProducesNoLine() {
        val points = listOf(p(1, 179.0, 0.0), p(2, -179.0, 5.0), p(3, -178.0, 10.0))
        assertTrue(clipGtfsShapePolyline(points, 0.0, 10.0).isEmpty())
    }

    @Test fun mixedShapeIdsProduceNoLine() {
        val points = listOf(p(1, 8.0, 0.0), p(2, 8.5, 5.0).copy(shapeId = "other"), p(3, 9.0, 10.0))
        assertTrue(clipGtfsShapePolyline(points, 1.0, 9.0).isEmpty())
    }

    @Test fun invalidVertexBudgetProducesNoLine() {
        val points = listOf(p(1, 8.0, 0.0), p(2, 9.0, 10.0))
        assertTrue(clipGtfsShapePolyline(points, 1.0, 9.0, 1).isEmpty())
        assertTrue(clipGtfsShapePolyline(points, 1.0, 9.0, 513).isEmpty())
    }

    @Test fun oversizedInputShapeProducesNoLine() {
        val points = (0..2_048).map { p(it + 1, 8.0 + it / 10_000.0, it.toDouble()) }
        assertTrue(clipGtfsShapePolyline(points, 1.0, 2_000.0).isEmpty())
    }

}
