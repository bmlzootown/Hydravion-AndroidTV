package ml.bmlzootown.hydravion.playback

import android.os.Build
import ml.bmlzootown.hydravion.Constants

/**
 * Workarounds for device-specific ExoPlayer/MediaCodec issues.
 *
 * Google TV Streamer (mt8696 / MediaTek) is known to freeze HLS MPEG-TS video after a few
 * seconds while audio continues when using the vendor HW AVC decoder (c2.mtk.avc.decoder).
 */
object DevicePlaybackCompat {

    /** Prefer the software AVC decoder for live HLS on chipsets with this bug. */
    @JvmStatic
    fun preferSoftwareVideoDecoderForLive(): Boolean {
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()

        if (hardware.contains("mt8696") || board.contains("mt8696")) {
            return true
        }
        // Google TV Streamer and other MediaTek-based TV devices.
        if (model.contains("google tv streamer") || product.contains("boreal")) {
            return true
        }
        if (hardware.startsWith("mt") || board.startsWith("mt")) {
            return true
        }
        return false
    }

    /** Live delivery: fMP4 segments avoid MPEG-TS demux issues on some TV SoCs. */
    @JvmStatic
    fun preferredLiveOutputKind(): String {
        return if (preferSoftwareVideoDecoderForLive()) {
            Constants.OUTPUT_FORMAT_HLS_FMP4
        } else {
            Constants.OUTPUT_FORMAT_HLS_MPEGTS
        }
    }
}
