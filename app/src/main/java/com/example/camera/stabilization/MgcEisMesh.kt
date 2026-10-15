package com.example.camera.stabilization

internal class MgcEisMesh {
    companion object {
        const val STRIP_COUNT = 12
        private const val POSITION_COMPONENT_COUNT = 4
        private const val TEX_COORD_COMPONENT_COUNT = 2
        private const val VERTEX_COUNT = (STRIP_COUNT + 1) * 2
        const val INDEX_COUNT = STRIP_COUNT * 6

        internal val textureCoordinates: FloatArray = buildTextureCoordinates()
        internal val indices: ShortArray = ShortArray(INDEX_COUNT).also { output ->
            for (triangle in 0 until STRIP_COUNT * 2) {
                for (corner in 0 until 3) {
                    output[triangle * 3 + corner] = (triangle + corner).toShort()
                }
            }
        }

        internal fun warpedPositions(rowHomographies: FloatArray): FloatArray {
            require(rowHomographies.size == STRIP_COUNT * 9)
            val output = FloatArray(VERTEX_COUNT * POSITION_COMPONENT_COUNT)
            for (boundary in 0..STRIP_COUNT) {
                val matrixIndex = if (boundary == 0) 0 else boundary - 1
                val matrixOffset = matrixIndex * 9
                val y = 1f - 2f * boundary.toFloat() / STRIP_COUNT.toFloat()
                for (side in 0..1) {
                    val x = if (side == 0) -1f else 1f
                    val outputOffset = (boundary * 2 + side) * POSITION_COMPONENT_COUNT
                    output[outputOffset] =
                        x * rowHomographies[matrixOffset] +
                            y * rowHomographies[matrixOffset + 1] +
                            rowHomographies[matrixOffset + 2]
                    output[outputOffset + 1] =
                        x * rowHomographies[matrixOffset + 3] +
                            y * rowHomographies[matrixOffset + 4] +
                            rowHomographies[matrixOffset + 5]
                    output[outputOffset + 2] = 0f
                    output[outputOffset + 3] =
                        x * rowHomographies[matrixOffset + 6] +
                            y * rowHomographies[matrixOffset + 7] +
                            rowHomographies[matrixOffset + 8]
                }
            }
            return output
        }

        private fun buildTextureCoordinates(): FloatArray {
            val output = FloatArray(VERTEX_COUNT * TEX_COORD_COMPONENT_COUNT)
            for (boundary in 0..STRIP_COUNT) {
                val y = boundary.toFloat() / STRIP_COUNT.toFloat()
                val offset = boundary * 4
                output[offset] = 0f
                output[offset + 1] = y
                output[offset + 2] = 1f
                output[offset + 3] = y
            }
            return output
        }
    }
}
