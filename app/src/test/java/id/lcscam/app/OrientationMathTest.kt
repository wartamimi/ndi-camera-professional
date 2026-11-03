package id.lcscam.app

import id.lcscam.app.camera.OrientationMath
import id.lcscam.app.domain.model.OutputOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationMathTest {
    @Test fun rearCameraLandscapeNeedsNoRotation() {
        // Typical rear sensor (90) held in landscape with the top to the left (listener reports 270).
        assertEquals(0, OrientationMath.outputRotation(90, 270, frontFacing = false))
        assertEquals(180, OrientationMath.outputRotation(90, 90, frontFacing = false))
        assertEquals(90, OrientationMath.outputRotation(90, 0, frontFacing = false))
    }

    @Test fun frontCameraLandscape() {
        assertEquals(0, OrientationMath.outputRotation(270, 270, frontFacing = true))
        assertEquals(270, OrientationMath.outputRotation(270, 0, frontFacing = true))
    }

    @Test fun landscapeModeIgnoresPortraitDevice() {
        assertEquals(270, OrientationMath.constrainDeviceOrientation(0, 270, 0, OutputOrientation.LANDSCAPE))
        assertEquals(90, OrientationMath.constrainDeviceOrientation(90, 270, 0, OutputOrientation.LANDSCAPE))
        assertEquals(180, OrientationMath.constrainDeviceOrientation(180, 270, 0, OutputOrientation.PORTRAIT))
        assertEquals(0, OrientationMath.constrainDeviceOrientation(270, 270, 0, OutputOrientation.PORTRAIT))
    }

    @Test fun snapHasHysteresis() {
        assertEquals(270, OrientationMath.snap(300, 270))   // within hysteresis band
        assertEquals(0, OrientationMath.snap(350, 270))
        assertEquals(90, OrientationMath.snap(95, 0))
        assertEquals(0, OrientationMath.snap(-1, 0))        // unknown orientation keeps previous
    }

    @Test fun previewRotationAndTapMapping() {
        assertEquals(0, OrientationMath.previewRotation(90, 90, frontFacing = false))
        assertEquals(90, OrientationMath.previewRotation(90, 0, frontFacing = false))
        val (x, y) = OrientationMath.displayToSensor(0.25f, 0.75f, 0, mirrored = false)
        assertEquals(0.25f, x, 1e-6f); assertEquals(0.75f, y, 1e-6f)
        // Portrait display of a 90-degree sensor: display top-left maps to sensor bottom-left.
        val (x2, y2) = OrientationMath.displayToSensor(0f, 0f, 90, mirrored = false)
        assertEquals(0f, x2, 1e-6f); assertEquals(1f, y2, 1e-6f)
        val (x3, _) = OrientationMath.displayToSensor(0.2f, 0.5f, 0, mirrored = true)
        assertEquals(0.8f, x3, 1e-6f)
    }
}
