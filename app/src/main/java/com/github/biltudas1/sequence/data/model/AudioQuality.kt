package com.github.biltudas1.sequence.data.model

enum class AudioQualityLevel(
    val label: String,
    val bitrateKbps: Int,
    val stereo: Boolean,
    val opusModeAudio: Boolean, // true for 'audio', false for 'voip'
    val cbr: Boolean,
    val useProcessing: Boolean
) {
    VERY_HIGH("Very High", 128, true, true, true, false),
    HIGH("High", 64, false, false, false, true),
    STANDARD("Standard (Default)", 40, false, false, false, true),
    LOW("Low (Data Saver)", 16, false, false, false, true);

    val description: String
        get() = "$bitrateKbps kbps, ${if (stereo) "Stereo" else "Mono"}, Processing: ${if (useProcessing) "On" else "Off"}"
}
