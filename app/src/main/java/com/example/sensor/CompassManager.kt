package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CompassManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    // Core sensors for rotation
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

    // Fallback vector values
    private val gravityValues = FloatArray(3)
    private val geomagneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    fun startListening() {
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            // Register both accelerometer and magnet
            if (accelerometerSensor != null) {
                sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
            }
            if (magneticSensor != null) {
                sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        try {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientationValues = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientationValues)
                
                // Azimuth is orientationValues[0], converts radians to degrees (-180 to 180)
                var deg = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                if (deg < 0) {
                    deg += 360f
                }
                _azimuth.value = deg
            } else {
                // Fallback: accelerometer + magnetometer algorithm
                val sizeToCopy = minOf(event.values.size, 3)
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, gravityValues, 0, sizeToCopy)
                    hasGravity = true
                } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, geomagneticValues, 0, sizeToCopy)
                    hasGeomagnetic = true
                }

                if (hasGravity && hasGeomagnetic) {
                    val r = FloatArray(9)
                    val i = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, i, gravityValues, geomagneticValues)) {
                        val actualOrientation = FloatArray(3)
                        SensorManager.getOrientation(r, actualOrientation)
                        var deg = Math.toDegrees(actualOrientation[0].toDouble()).toFloat()
                        if (deg < 0) {
                            deg += 360f
                        }
                        _azimuth.value = deg
                    }
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
