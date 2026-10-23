package com.opensource.svga.core.drawer

import android.widget.ImageView

class SvgaScaleInfo {

    var tranFx: Float = 0.0f
    var tranFy: Float = 0.0f
    var scaleFx: Float = 1.0f
    var scaleFy: Float = 1.0f

    private fun reset() {
        tranFx = 0.0f
        tranFy = 0.0f
        scaleFx = 1.0f
        scaleFy = 1.0f
    }

    fun performScaleType(
        canvasWidth: Float,
        canvasHeight: Float,
        videoWidth: Float,
        videoHeight: Float,
        scaleType: ImageView.ScaleType
    ) {
        if (canvasWidth <= 0f || canvasHeight <= 0f || videoWidth <= 0f || videoHeight <= 0f) {
            return
        }

        reset()
        val videoRatio = videoWidth / videoHeight
        val canvasRatio = canvasWidth / canvasHeight

        val canH_d_vidH = canvasHeight / videoHeight
        val canW_d_vidW = canvasWidth / videoWidth

        when (scaleType) {
            ImageView.ScaleType.CENTER -> {
                tranFx = (canvasWidth - videoWidth) / 2.0f
                tranFy = (canvasHeight - videoHeight) / 2.0f
            }
            ImageView.ScaleType.CENTER_CROP -> {
                if (videoRatio > canvasRatio) {
                    scaleFx = canH_d_vidH
                    scaleFy = canH_d_vidH
                    tranFx = (canvasWidth - videoWidth * canH_d_vidH) / 2.0f
                } else {
                    scaleFx = canW_d_vidW
                    scaleFy = canW_d_vidW
                    tranFy = (canvasHeight - videoHeight * canW_d_vidW) / 2.0f
                }
            }
            ImageView.ScaleType.CENTER_INSIDE -> {
                if (videoWidth < canvasWidth && videoHeight < canvasHeight) {
                    tranFx = (canvasWidth - videoWidth) / 2.0f
                    tranFy = (canvasHeight - videoHeight) / 2.0f
                } else {
                    if (videoRatio > canvasRatio) {
                        scaleFx = canW_d_vidW
                        scaleFy = canW_d_vidW
                        tranFy = (canvasHeight - videoHeight * canW_d_vidW) / 2.0f
                    } else {
                        scaleFx = canH_d_vidH
                        scaleFy = canH_d_vidH
                        tranFx = (canvasWidth - videoWidth * canH_d_vidH) / 2.0f
                    }
                }
            }
            ImageView.ScaleType.FIT_CENTER -> {
                if (videoRatio > canvasRatio) {
                    scaleFx = canW_d_vidW
                    scaleFy = canW_d_vidW
                    tranFy = (canvasHeight - videoHeight * canW_d_vidW) / 2.0f
                } else {
                    scaleFx = canH_d_vidH
                    scaleFy = canH_d_vidH
                    tranFx = (canvasWidth - videoWidth * canH_d_vidH) / 2.0f
                }
            }
            ImageView.ScaleType.FIT_START -> {
                if (videoRatio > canvasRatio) {
                    scaleFx = canW_d_vidW
                    scaleFy = canW_d_vidW
                } else {
                    scaleFx = canH_d_vidH
                    scaleFy = canH_d_vidH
                }
            }
            ImageView.ScaleType.FIT_END -> {
                if (videoRatio > canvasRatio) {
                    scaleFx = canW_d_vidW
                    scaleFy = canW_d_vidW
                    tranFy = canvasHeight - videoHeight * canW_d_vidW
                } else {
                    scaleFx = canH_d_vidH
                    scaleFy = canH_d_vidH
                    tranFx = canvasWidth - videoWidth * canH_d_vidH
                }
            }
            ImageView.ScaleType.FIT_XY -> {
                scaleFx = canW_d_vidW
                scaleFy = canH_d_vidH
            }
            else -> {
                scaleFx = canW_d_vidW
                scaleFy = canW_d_vidW
            }
        }
    }
}
