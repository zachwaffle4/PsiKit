package org.psilynx.psikit.core.datalog

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * WPILog file header containing format identification and version info
 */
data class WpiLogHeader(
    val version: Short = 0x0100, // Version 1.0
    val extraHeaderData: String = ""
) {
    fun toByteBuffer(): ByteBuffer {
        val extraHeaderBytes = extraHeaderData.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(12 + extraHeaderBytes.size).order(ByteOrder.LITTLE_ENDIAN)

        // Magic string "WPILOG"
        buffer.put("WPILOG".toByteArray(StandardCharsets.US_ASCII))
        // Version (little endian)
        buffer.putShort(version)
        // Extra header length
        buffer.putInt(extraHeaderBytes.size)
        // Extra header data
        buffer.put(extraHeaderBytes)

        return buffer.flip()
    }

    companion object {
        const val MAGIC_STRING = "WPILOG"
        const val MAGIC_LENGTH = 6

        fun fromByteArray(data: ByteArray): WpiLogHeader {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // Verify magic string
            val magic = ByteArray(MAGIC_LENGTH)
            buffer.get(magic)
            require(String(magic, StandardCharsets.US_ASCII) == MAGIC_STRING) {
                "Invalid WPILog magic string"
            }

            val version = buffer.short
            val extraHeaderLength = buffer.int

            val extraHeaderData = if (extraHeaderLength > 0) {
                val extraBytes = ByteArray(extraHeaderLength)
                buffer.get(extraBytes)
                String(extraBytes, StandardCharsets.UTF_8)
            } else {
                ""
            }

            return WpiLogHeader(version, extraHeaderData)
        }
    }
}

/**
 * Represents the header length bitfield encoding
 */
data class RecordHeaderBitfield(
    val entryIdLength: Int,    // 1-4 bytes
    val payloadSizeLength: Int, // 1-4 bytes
    val timestampLength: Int    // 1-8 bytes
) {
    init {
        require(entryIdLength in 1..4) { "Entry ID length must be 1-4 bytes" }
        require(payloadSizeLength in 1..4) { "Payload size length must be 1-4 bytes" }
        require(timestampLength in 1..8) { "Timestamp length must be 1-8 bytes" }
    }

    fun toByte(): Byte {
        val entryIdBits = (entryIdLength - 1) and 0x03
        val payloadSizeBits = ((payloadSizeLength - 1) and 0x03) shl 2
        val timestampBits = ((timestampLength - 1) and 0x07) shl 4
        return (entryIdBits or payloadSizeBits or timestampBits).toByte()
    }

    companion object {
        fun fromByte(value: Byte): RecordHeaderBitfield {
            val intValue = value.toInt() and 0xFF
            val entryIdLength = (intValue and 0x03) + 1
            val payloadSizeLength = ((intValue shr 2) and 0x03) + 1
            val timestampLength = ((intValue shr 4) and 0x07) + 1
            return RecordHeaderBitfield(entryIdLength, payloadSizeLength, timestampLength)
        }
    }
}

/**
 * Base class for all WPILog records
 */
sealed interface WpiLogRecord {
    val entryId: Int
    val timestamp: Long
    val payload: ByteBuffer

    fun toByteBuffer(): ByteBuffer {
        val payloadSize = payload.remaining()

        // Determine minimum field lengths
        val entryIdLength = when {
            entryId <= 0xFF -> 1
            entryId <= 0xFFFF -> 2
            entryId <= 0xFFFFFF -> 3
            else -> 4
        }

        val payloadSizeLength = when {
            payloadSize <= 0xFF -> 1
            payloadSize <= 0xFFFF -> 2
            payloadSize <= 0xFFFFFF -> 3
            else -> 4
        }

        val timestampLength = when {
            timestamp <= 0xFF -> 1
            timestamp <= 0xFFFF -> 2
            timestamp <= 0xFFFFFF -> 3
            timestamp <= 0xFFFFFFFFL -> 4
            timestamp <= 0xFFFFFFFFFFL -> 5
            timestamp <= 0xFFFFFFFFFFFFL -> 6
            timestamp <= 0xFFFFFFFFFFFFFFL -> 7
            else -> 8
        }

        val bitfield = RecordHeaderBitfield(entryIdLength, payloadSizeLength, timestampLength)
        val totalSize = 1 + entryIdLength + payloadSizeLength + timestampLength + payloadSize
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // Header bitfield
        buffer.put(bitfield.toByte())

        // Entry ID (variable length)
        when (entryIdLength) {
            1 -> buffer.put(entryId.toByte())
            2 -> buffer.putShort(entryId.toShort())
            3 -> {
                buffer.put((entryId and 0xFF).toByte())
                buffer.put(((entryId shr 8) and 0xFF).toByte())
                buffer.put(((entryId shr 16) and 0xFF).toByte())
            }
            4 -> buffer.putInt(entryId)
        }

        // Payload size (variable length)
        when (payloadSizeLength) {
            1 -> buffer.put(payloadSize.toByte())
            2 -> buffer.putShort(payloadSize.toShort())
            3 -> {
                buffer.put((payloadSize and 0xFF).toByte())
                buffer.put(((payloadSize shr 8) and 0xFF).toByte())
                buffer.put(((payloadSize shr 16) and 0xFF).toByte())
            }
            4 -> buffer.putInt(payloadSize)
        }

        // Timestamp (variable length)
        repeat(timestampLength) { i ->
            buffer.put(((timestamp shr (i * 8)) and 0xFF).toByte())
        }

        // Payload
        val payloadDup = payload.duplicate()
        buffer.put(payloadDup)

        buffer.flip()
        return buffer
    }
}

/**
 * Data record containing timestamped entry data
 */
data class DataRecord(
    override val entryId: Int,
    override val timestamp: Long,
    override val payload: ByteBuffer
) : WpiLogRecord {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DataRecord

        if (entryId != other.entryId) return false
        if (timestamp != other.timestamp) return false
        if (!payload.duplicate().equals(other.payload.duplicate())) return false

        return true
    }

    override fun hashCode(): Int {
        var result = entryId
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + payload.hashCode()
        return result
    }
}

/**
 * Enum for control record types
 */
enum class ControlRecordType(val value: Byte) {
    START(0),
    FINISH(1),
    SET_METADATA(2);

    companion object {
        fun fromByte(value: Byte): ControlRecordType = entries.first { it.value == value }
    }
}

/**
 * Base class for control records (entry ID = 0)
 */
sealed interface ControlRecord : WpiLogRecord {
    override val entryId: Int
        get() = 0
    val controlType: ControlRecordType
    val targetEntryId: Int
}

/**
 * Start control record to define an entry
 */
data class StartRecord(
    override val timestamp: Long,
    override val targetEntryId: Int,
    val entryName: String,
    val entryType: String,
    val metadata: String = ""
) : ControlRecord {
    override val controlType = ControlRecordType.START

    override val payload: ByteBuffer by lazy {
        val nameBytes = entryName.toByteArray(StandardCharsets.UTF_8)
        val typeBytes = entryType.toByteArray(StandardCharsets.UTF_8)
        val metadataBytes = metadata.toByteArray(StandardCharsets.UTF_8)

        val buffer = ByteBuffer.allocate(
            1 + 4 + 4 + nameBytes.size + 4 + typeBytes.size + 4 + metadataBytes.size
        ).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(controlType.value)
        buffer.putInt(targetEntryId)
        buffer.putInt(nameBytes.size)
        buffer.put(nameBytes)
        buffer.putInt(typeBytes.size)
        buffer.put(typeBytes)
        buffer.putInt(metadataBytes.size)
        buffer.put(metadataBytes)

        buffer.flip()
        buffer
    }
}

/**
 * Finish control record to indicate entry completion
 */
data class FinishRecord(
    override val timestamp: Long,
    override val targetEntryId: Int
) : ControlRecord {
    override val controlType = ControlRecordType.FINISH

    override val payload: ByteBuffer by lazy {
        val buffer = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(controlType.value)
        buffer.putInt(targetEntryId)
        buffer.flip()
        buffer
    }
}

/**
 * Set metadata control record to update entry metadata
 */
data class SetMetadataRecord(
    override val timestamp: Long,
    override val targetEntryId: Int,
    val metadata: String
) : ControlRecord {
    override val controlType = ControlRecordType.SET_METADATA

    override val payload: ByteBuffer by lazy {
        val metadataBytes = metadata.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(1 + 4 + 4 + metadataBytes.size).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(controlType.value)
        buffer.putInt(targetEntryId)
        buffer.putInt(metadataBytes.size)
        buffer.put(metadataBytes)

        buffer.flip()
        buffer
    }
}
