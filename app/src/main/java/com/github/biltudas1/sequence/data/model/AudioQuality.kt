package com.github.biltudas1.sequence.data.model

import androidx.annotation.StringRes
import com.github.biltudas1.sequence.R

enum class AudioQualityLevel(
    @StringRes val labelResId: Int,
    val bitrateKbps: Int,
    val stereo: Boolean,
    val opusModeAudio: Boolean, // true for 'audio', false for 'voip'
    val cbr: Boolean,
    val useProcessing: Boolean
) {
    VERY_HIGH(R.string.audio_quality_very_high, 128, true, true, true, false),
    HIGH(R.string.audio_quality_high, 64, false, false, false, true),
    STANDARD(R.string.audio_quality_standard, 40, false, false, false, true),
    LOW(R.string.audio_quality_low, 16, false, false, false, true);
}
