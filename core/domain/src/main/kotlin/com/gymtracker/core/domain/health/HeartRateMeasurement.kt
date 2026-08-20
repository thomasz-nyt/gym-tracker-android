package com.gymtracker.core.domain.health

/**
 * A parsed Bluetooth Heart Rate Profile "Heart Rate Measurement" characteristic (0x2A37)
 * notification (ADR-0039). Pure byte arithmetic, no Android import — `:feature:health`'s
 * `HeartRateBandGateway` hands this parser the raw notification payload and nothing else
 * crosses the seam.
 */
data class HeartRateMeasurement(
    val bpm: Int,
    /** Cumulative energy expended since the sensor's last reset, if the flags report one. */
    val energyExpendedKilocalories: Int?,
) {
    companion object {
        private const val FLAG_VALUE_FORMAT_UINT16 = 0x01
        private const val FLAG_ENERGY_EXPENDED_PRESENT = 0x08
        private const val FLAGS_SIZE = 1
        private const val BPM_SIZE_UINT8 = 1
        private const val BPM_SIZE_UINT16 = 2
        private const val ENERGY_EXPENDED_SIZE = 2
        private const val BITS_PER_BYTE = 8

        /**
         * Parses the flags byte per the Bluetooth SIG GATT Specification Supplement's Heart
         * Rate Measurement layout: flags, then BPM as uint8 or uint16 depending on bit 0, then
         * (if bit 3 is set) a little-endian uint16 Energy Expended field. RR-interval fields,
         * when present, follow and are ignored — this app has no use for them.
         *
         * Returns `null` for any payload too short for what its own flags claim, rather than
         * throwing: a band delivering a malformed notification degrades to "no reading" here,
         * the same as if it had sent nothing (constitution §3 — an enhancement layer).
         */
        fun parse(payload: ByteArray): HeartRateMeasurement? {
            val flags = payload.getOrNull(0)?.toInt() ?: return null
            val isUint16 = flags and FLAG_VALUE_FORMAT_UINT16 != 0
            val hasEnergyExpended = flags and FLAG_ENERGY_EXPENDED_PRESENT != 0
            val bpmSize = if (isUint16) BPM_SIZE_UINT16 else BPM_SIZE_UINT8
            val energySize = if (hasEnergyExpended) ENERGY_EXPENDED_SIZE else 0
            val requiredSize = FLAGS_SIZE + bpmSize + energySize

            return payload.takeIf { it.size >= requiredSize }?.let { valid ->
                val bpm =
                    if (isUint16) {
                        valid.readUInt16LittleEndian(FLAGS_SIZE)
                    } else {
                        valid[FLAGS_SIZE].toUByte().toInt()
                    }
                val energyExpendedKilocalories =
                    if (hasEnergyExpended) valid.readUInt16LittleEndian(FLAGS_SIZE + bpmSize) else null

                HeartRateMeasurement(bpm, energyExpendedKilocalories)
            }
        }

        private fun ByteArray.readUInt16LittleEndian(offset: Int): Int =
            this[offset].toUByte().toInt() or (this[offset + 1].toUByte().toInt() shl BITS_PER_BYTE)
    }
}
