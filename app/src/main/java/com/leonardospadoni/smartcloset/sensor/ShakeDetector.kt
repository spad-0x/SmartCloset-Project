package com.leonardospadoni.smartcloset.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetector(context: Context) : SensorEventListener {

    private var sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Callback da chiamare quando avviene lo shake
    private var onShakeListener: (() -> Unit)? = null

    // Variabili per il calcolo della forza G
    private var lastTime: Long = 0
    private var lastShakeTime: Long = 0
    private val SHAKE_THRESHOLD = 12.0f // Sensibilità (più alto = devi scuotere più forte)
    private val MIN_TIME_BETWEEN_SHAKES = 1000 // Millisecondi (per evitare doppio trigger)

    fun start(onShake: () -> Unit) {
        this.onShakeListener = onShake
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Calcolo vettoriale della forza di gravità
            val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat() / SensorManager.GRAVITY_EARTH

            if (gForce > SHAKE_THRESHOLD) {
                val now = System.currentTimeMillis()
                // Ignora shake troppo vicini tra loro
                if (lastShakeTime + MIN_TIME_BETWEEN_SHAKES > now) {
                    return
                }
                lastShakeTime = now
                onShakeListener?.invoke()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Non ci serve gestire la precisione per questo scopo
    }
}
