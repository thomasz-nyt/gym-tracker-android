package com.gymtracker.core.domain.health

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-46/ADR-0039: the Bluetooth Heart Rate Profile's Heart Rate Measurement characteristic
 * (0x2A37) payload — flags byte, then either a uint8 or uint16 BPM value, then an optional
 * uint16 Energy Expended field when the flags say it is present. Fixture bytes are the
 * profile's own examples (Bluetooth SIG GATT Specification Supplement, Heart Rate Measurement).
 */
class HeartRateMeasurementTest {
    @Test
    fun `uint8 BPM, no energy expended`() {
        // flags = 0x00: uint8 format, no energy expended, no RR-interval
        val measurement = HeartRateMeasurement.parse(byteArrayOf(0x00, 0x50))

        assertEquals(0x50, measurement?.bpm)
        assertNull(measurement?.energyExpendedKilocalories)
    }

    @Test
    fun `uint16 BPM, no energy expended`() {
        // flags = 0x01: uint16 format. BPM = 0x0190 = 400, little-endian
        val measurement = HeartRateMeasurement.parse(byteArrayOf(0x01, 0x90.toByte(), 0x01))

        assertEquals(400, measurement?.bpm)
        assertNull(measurement?.energyExpendedKilocalories)
    }

    @Test
    fun `uint8 BPM with energy expended present`() {
        // flags = 0x08: uint8 format, energy-expended bit set. BPM = 0x46 = 70.
        // Energy Expended = 0x00C8 = 200 kJ, little-endian, per the GATT spec's own unit.
        val measurement =
            HeartRateMeasurement.parse(byteArrayOf(0x08, 0x46, 0xC8.toByte(), 0x00))

        assertEquals(70, measurement?.bpm)
        assertEquals(200, measurement?.energyExpendedKilocalories)
    }

    @Test
    fun `uint16 BPM with energy expended present`() {
        // flags = 0x09: uint16 format + energy-expended bit. BPM = 0x0064 = 100.
        // Energy Expended = 0x012C = 300.
        val measurement =
            HeartRateMeasurement.parse(
                byteArrayOf(0x09, 0x64, 0x00, 0x2C, 0x01),
            )

        assertEquals(100, measurement?.bpm)
        assertEquals(300, measurement?.energyExpendedKilocalories)
    }

    @Test
    fun `RR-interval bytes present are ignored, not misread as energy expended`() {
        // flags = 0x10: uint8 format, RR-interval present, no energy expended. A parser that
        // ignores the flag bits would misread the first RR-interval pair as an energy value.
        val measurement =
            HeartRateMeasurement.parse(byteArrayOf(0x10, 0x4B, 0xFF.toByte(), 0x03))

        assertEquals(0x4B, measurement?.bpm)
        assertNull(measurement?.energyExpendedKilocalories)
    }

    @Test
    fun `empty payload is malformed, not a crash`() {
        assertNull(HeartRateMeasurement.parse(byteArrayOf()))
    }

    @Test
    fun `flags byte with no BPM byte is malformed, not a crash`() {
        assertNull(HeartRateMeasurement.parse(byteArrayOf(0x00)))
    }

    @Test
    fun `uint16 format truncated mid-value is malformed, not a crash`() {
        // flags say uint16, but only one BPM byte follows.
        assertNull(HeartRateMeasurement.parse(byteArrayOf(0x01, 0x50)))
    }

    @Test
    fun `energy-expended flag set but payload truncated before it is malformed, not a crash`() {
        // flags say uint8 + energy present, but the energy bytes are missing entirely.
        assertNull(HeartRateMeasurement.parse(byteArrayOf(0x08, 0x50)))
    }

    @Test
    fun `zero BPM is honestly reported, not treated as malformed`() {
        // A malfunctioning sensor reporting zero is still a valid parse of the wire format;
        // "no reading" is represented by Searching/Lost states upstream, not by this parser
        // inventing a null (constitution §2.4 — never fabricate, but never hide a real zero
        // either).
        val measurement = HeartRateMeasurement.parse(byteArrayOf(0x00, 0x00))

        assertEquals(0, measurement?.bpm)
    }
}
