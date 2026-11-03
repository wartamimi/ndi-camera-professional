package id.lcscam.app.camera

import id.lcscam.app.domain.model.OutputOrientation

/** Pure orientation helpers (unit-tested). All angles are clockwise degrees. */
object OrientationMath {

    /** Snaps an OrientationEventListener angle to 0/90/180/270, or null if unknown. */
    fun snap(orientationDeg: Int, previous: Int): Int {
        if (orientationDeg < 0) return previous
        // 20 degree hysteresis around the previous value avoids flicker at 45 degree boundaries.
        val diff = Math.floorMod(orientationDeg - previous + 180, 360) - 180
        if (kotlin.math.abs(diff) <= 65) return previous
        return (((orientationDeg + 45) / 90) * 90) % 360
    }

    /**
     * Rotation to apply to sensor frames so that the NDI output is upright for a device
     * physically rotated by [deviceOrientation] (OrientationEventListener convention).
     */
    fun outputRotation(sensorOrientation: Int, deviceOrientation: Int, frontFacing: Boolean): Int {
        val d = if (frontFacing) -deviceOrientation else deviceOrientation
        return Math.floorMod(sensorOrientation + d, 360)
    }

    /**
     * Picks the device orientation used for the NDI output: in LANDSCAPE mode a portrait device
     * orientation is replaced by the last landscape orientation (and vice versa).
     */
    fun constrainDeviceOrientation(device: Int, lastLandscape: Int, lastPortrait: Int, mode: OutputOrientation): Int =
        when (mode) {
            OutputOrientation.LANDSCAPE -> if (device == 90 || device == 270) device else lastLandscape
            OutputOrientation.PORTRAIT -> if (device == 0 || device == 180) device else lastPortrait
        }

    /** Rotation between the sensor image and what the preview displays for a display rotation. */
    fun previewRotation(sensorOrientation: Int, displayRotationDeg: Int, frontFacing: Boolean): Int =
        if (frontFacing) Math.floorMod(sensorOrientation + displayRotationDeg, 360)
        else Math.floorMod(sensorOrientation - displayRotationDeg, 360)

    /**
     * Maps a normalised point on the displayed preview back to normalised sensor coordinates.
     * [rotation] is [previewRotation]; front previews are mirrored horizontally.
     */
    fun displayToSensor(x: Float, y: Float, rotation: Int, mirrored: Boolean): Pair<Float, Float> {
        val dx = if (mirrored) 1f - x else x
        return when (rotation) {
            90 -> Pair(y, 1f - dx)
            180 -> Pair(1f - dx, 1f - y)
            270 -> Pair(1f - y, dx)
            else -> Pair(dx, y)
        }
    }
}
