package com.opensource.svga.core.model

data class SVGAPoint(
    val x: Float,
    val y: Float,
    val value: Float = 0f
)

data class SVGARect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
)
