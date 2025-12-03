package com.leonardospadoni.smartcloset.model

// Struttura JSON di Open-Meteo
data class WeatherResponse(
    val current: CurrentWeather
)

data class CurrentWeather(
    val temperature_2m: Float, // Temperatura
    val weather_code: Int      // Codice numerico (0=Sereno, 61=Pioggia, ecc.)
)