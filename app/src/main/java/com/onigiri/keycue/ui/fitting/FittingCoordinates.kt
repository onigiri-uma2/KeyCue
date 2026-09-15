package com.onigiri.keycue.ui.fitting

internal fun normalizedDragDelta(
    dragX: Float,
    dragY: Float,
    imageWidth: Float,
    imageHeight: Float,
    zoomScale: Float
): Pair<Float, Float> {
    val scaledWidth = imageWidth * zoomScale
    val scaledHeight = imageHeight * zoomScale
    if (scaledWidth <= 0f || scaledHeight <= 0f) return 0f to 0f
    return dragX / scaledWidth to dragY / scaledHeight
}
