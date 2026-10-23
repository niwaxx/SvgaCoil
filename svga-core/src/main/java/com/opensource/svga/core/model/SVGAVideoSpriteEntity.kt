package com.opensource.svga.core.model

import com.opensource.svgaplayer.proto.SpriteEntity

class SVGAVideoSpriteEntity(obj: SpriteEntity) {

    val imageKey: String? = obj.imageKey.takeIf { it.isNotEmpty() }
    val matteKey: String? = obj.matteKey.takeIf { it.isNotEmpty() }
    val frames: List<SVGAVideoSpriteFrameEntity>

    init {
        var lastFrame: SVGAVideoSpriteFrameEntity? = null
        frames = obj.framesList.map { frameEntity ->
            val frameItem = SVGAVideoSpriteFrameEntity(frameEntity)
            if (frameItem.shapes.isNotEmpty() && frameItem.shapes.first().isKeep) {
                lastFrame?.let {
                    frameItem.shapes = it.shapes
                }
            }
            lastFrame = frameItem
            frameItem
        }
    }
}
