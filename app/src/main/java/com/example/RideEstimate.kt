package com.example

data class RideEstimate(val fuelCost: Double, val totalCost: Double, val remaining: Double)

/** km / (km per litre) * rupees per litre, for a return journey. */
fun buildRideEstimate(
    oneWayKm: Double, mileageKmPerLitre: Double, fuelPricePerLitre: Double,
    food: Double, buffer: Double, availableBudget: Double
): RideEstimate {
    require(listOf(oneWayKm, fuelPricePerLitre, food, buffer, availableBudget).all { it.isFinite() && it >= 0 })
    require(mileageKmPerLitre.isFinite() && mileageKmPerLitre > 0)
    val fuel = roundMoney(oneWayKm * 2 / mileageKmPerLitre * fuelPricePerLitre)
    val total = roundMoney(fuel + roundMoney(food) + roundMoney(buffer))
    return RideEstimate(fuel, total, roundMoney(availableBudget - total))
}
