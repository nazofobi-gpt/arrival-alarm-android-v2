package com.nazofobi.arrivalalarm

/**
 * GTFS trips.txt bikes_allowed: 0/absent = no information, 1 = allowed, 2 = not allowed.
 * Never infer Deutschland-Ticket eligibility or fares from route names, mode or bike data.
 */
enum class BicycleAllowance { ALLOWED, DISALLOWED, UNKNOWN }

object GermanyFareEvidence {
    /**
     * A journey permits bicycles only when every travelled trip explicitly permits them.
     * A single prohibition is enough to make the entire journey unsuitable.
     * Missing trips and non-standard values fail closed to UNKNOWN.
     */
    fun bicycleAllowance(tripValues: List<Int?>): BicycleAllowance {
        if (tripValues.isEmpty()) return BicycleAllowance.UNKNOWN
        if (tripValues.any { it == 2 }) return BicycleAllowance.DISALLOWED
        if (tripValues.all { it == 1 }) return BicycleAllowance.ALLOWED
        return BicycleAllowance.UNKNOWN
    }
}
