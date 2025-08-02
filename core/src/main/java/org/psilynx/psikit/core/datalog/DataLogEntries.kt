package org.psilynx.psikit.core.datalog

import org.psilynx.psikit.core.wpi.Struct
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Base sealed class for log entries with type-specific implementations
 */
sealed interface LogEntry<T> {
    val id: Int
    val name: String
    val typeString: String
    var metadata: String
    var isFinished: Boolean

    /**
     * Encode a value of the appropriate type to ByteBuffer
     */
    fun encodeValue(value: T): ByteBuffer

    /**
     * Decode ByteBuffer to a value of the appropriate type
     */
    fun decodeValue(data: ByteBuffer): T

    /**
     * Create a StartRecord for this entry with the given timestamp and metadata.
     */
    fun startRecord(timestamp: Long, metadata: String = "") = StartRecord(
        timestamp,
        id,
        name,
        typeString,
        metadata
    )

    /**
     * Create a SetMetadataRecord for this entry with the given timestamp and metadata.
     */
    fun setMetadataRecord(timestamp: Long, metadata: String = "") = SetMetadataRecord(
        timestamp,
        id,
        metadata
    )

    /**
     * Create a FinishRecord for this entry with the given timestamp.
     */
    fun finishRecord(timestamp: Long) = FinishRecord(timestamp, id)

    /**
     * Create a DataRecord for this entry with the given timestamp and value.
     */
    fun dataRecord(timestamp: Long, value: T) = DataRecord(id, timestamp, encodeValue(value))
}

/**
 * Raw data entry
 */
data class RawEntry(
    override val id: Int,
    override val name: String,
    override var metadata: String = "",
    override var isFinished: Boolean = false
) : LogEntry<ByteBuffer> {
    override val typeString = "raw"

    override fun encodeValue(value: ByteBuffer): ByteBuffer = value.duplicate()

    override fun decodeValue(data: ByteBuffer): ByteBuffer = data.duplicate()
}

/**
 * Boolean entry
 */
data class BooleanEntry(
    override val id: Int,
    override val name: String,
    override var metadata: String = "",
    override var isFinished: Boolean = false
) : LogEntry<Boolean> {
    override val typeString = "boolean"

    override fun encodeValue(value: Boolean): ByteBuffer = ByteBuffer.allocate(1).put(if (value) 1 else 0).flip() as ByteBuffer

    override fun decodeValue(data: ByteBuffer): Boolean = data.get(0) != 0.toByte()
}

/**
 * 64-bit integer entry
 */
data class Int64Entry(
    override val id: Int,
    override val name: String,
    override var metadata: String = "",
    override var isFinished: Boolean = false
) : LogEntry<Long> {
    override val typeString = "int64"

    override fun encodeValue(value: Long): ByteBuffer =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).flip()

    override fun decodeValue(data: ByteBuffer): Long =
        data.order(ByteOrder.LITTLE_ENDIAN).long
}

/**
 * 32-bit float entry
 */
data class FloatEntry(
    override val id: Int,
    override val name: String,
    override var metadata: String = "",
    override var isFinished: Boolean = false
) : LogEntry<Float> {
    override val typeString = "float"

    override fun encodeValue(value: Float): ByteBuffer =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).flip()

    override fun decodeValue(data: ByteBuffer): Float =
        data.order(ByteOrder.LITTLE_ENDIAN).float
}

/**
 * 64-bit double entry
 */
data class DoubleEntry(
    override val id: Int,
    override val name: String,
    override var metadata: String = "",
    override var isFinished: Boolean = false
) : LogEntry<Double> {
    override val typeString = "double"

    override fun encodeValue(value: Double): ByteBuffer =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value).flip()

    override fun decodeValue(data: ByteBuffer): Double =
        data.order(ByteOrder.LITTLE_ENDIAN).double
}

/**
 * String entry
 */
data class StringEntry(
    override val id: Int,
    override val name: String,
    override var metadata: String = "",
    override var isFinished: Boolean = false
) : LogEntry<String> {
    override val typeString = "string"

    override fun encodeValue(value: String): ByteBuffer = ByteBuffer.wrap(value.toByteArray(StandardCharsets.UTF_8)).flip()

    override fun decodeValue(data: ByteBuffer): String {
        val bytes = ByteArray(data.remaining())
        data.get(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }
}

/**
 * Boolean array entry
 */
data class BooleanArrayEntry(
    override val id: Int,
    override val name: String,
    override var metadata: String = "",
    override var isFinished: Boolean = false
) : LogEntry<BooleanArray> {
    override val typeString = "boolean[]"

    override fun encodeValue(value: BooleanArray): ByteBuffer {
        val buffer = ByteBuffer.allocate(value.size).order(ByteOrder.LITTLE_ENDIAN)
        value.forEach { buffer.put(if (it) 1 else 0) }
        return buffer.flip()
    }

    override fun decodeValue(data: ByteBuffer): BooleanArray {
        val array = BooleanArray(data.remaining())
        for (i in array.indices) {
            array[i] = data.get(i) != 0.toByte()
        }
        return array
    }
}

/**
 * 64-bit integer array entry
 */
data class Int64ArrayEntry(
    override val id: Int,
    override val name: String,
    override var metadata: String = "",
    override var isFinished: Boolean = false
) : LogEntry<LongArray> {
    override val typeString = "int64[]"

    override fun encodeValue(value: LongArray): ByteBuffer {
        val buffer = ByteBuffer.allocate(value.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        value.forEach { buffer.putLong(it) }
        return buffer.flip()
    }

    override fun decodeValue(data: ByteBuffer): LongArray {
        val array = LongArray(data.remaining() / 8)
        for (i in array.indices) {
            array[i] = data.long
        }
        return array
    }
}

/**
 * 32-bit float array entry
 */
data class FloatArrayEntry(
    override val id: Int,
    override val name: String,
    override var metadata: String = "",
    override var isFinished: Boolean = false
) : LogEntry<FloatArray> {
    override val typeString = "float[]"

    override fun encodeValue(value: FloatArray): ByteBuffer {
        val buffer = ByteBuffer.allocate(value.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        value.forEach { buffer.putFloat(it) }
        return buffer.flip()
    }

    override fun decodeValue(data: ByteBuffer): FloatArray {
        val array = FloatArray(data.remaining() / 4)
        for (i in array.indices) {
            array[i] = data.float
        }
        return array
    }
}

/**
 * 64-bit double array entry
 */
data class DoubleArrayEntry(
    override val id: Int,
    override val name: String,
    override var metadata: String = "",
    override var isFinished: Boolean = false
) : LogEntry<DoubleArray> {
    override val typeString = "double[]"

    override fun encodeValue(value: DoubleArray): ByteBuffer {
        val buffer = ByteBuffer.allocate(value.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        value.forEach { buffer.putDouble(it) }
        return buffer.flip()
    }

    override fun decodeValue(data: ByteBuffer): DoubleArray {
        val array = DoubleArray(data.remaining() / 8)
        for (i in array.indices) {
            array[i] = data.double
        }
        return array
    }
}

/**
 * String array entry
 */
data class StringArrayEntry(
    override val id: Int,
    override val name: String,
    override var metadata: String = "",
    override var isFinished: Boolean = false
) : LogEntry<Array<String>> {
    override val typeString = "string[]"

    override fun encodeValue(value: Array<String>): ByteBuffer {
        val stringBytes = value.map { it.toByteArray(StandardCharsets.UTF_8) }
        val totalSize = 4 + stringBytes.sumOf { 4 + it.size }
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(value.size)
        stringBytes.forEach { bytes ->
            buffer.putInt(bytes.size)
            buffer.put(bytes)
        }

        return buffer.flip()
    }

    override fun decodeValue(data: ByteBuffer): Array<String> {
        val array = Array(data.int) {
            val stringLength = data.int
            val stringBytes = ByteArray(stringLength)
            data.get(stringBytes)
            String(stringBytes, StandardCharsets.UTF_8)
        }
        return array
    }
}

/**
 * Log entry for PsiKit Struct type
 */
data class StructEntry<T>(
    override val id: Int,
    override val name: String,
    override var metadata: String = "",
    override var isFinished: Boolean = false,
    val struct: Struct<T>
) : LogEntry<T> {
    override val typeString = "${struct.typeString}"

    override fun encodeValue(value: T): ByteBuffer {
        val buffer = ByteBuffer.allocate(struct.size).order(ByteOrder.LITTLE_ENDIAN)
        struct.pack(buffer, value)
        return buffer.flip()
    }

    override fun decodeValue(data: ByteBuffer): T = struct.unpack(data)
}



/**
 * Custom entry type for non-standard data types
 */
data class CustomEntry<T>(
    override val id: Int,
    override val name: String,
    override val typeString: String,
    val encoder: (T) -> ByteBuffer,
    val decoder: (ByteBuffer) -> T,
    override var metadata: String = "",
    override var isFinished: Boolean = false
) : LogEntry<T> {

    override fun encodeValue(value: T): ByteBuffer = encoder(value)

    override fun decodeValue(data: ByteBuffer): T = decoder(data)
}

/**
 * Factory object for creating LogEntry instances from type strings.
 * Note: Due to generics, this factory returns LogEntry<*> and may require casting
 * Consider using the specific factory methods for better type safety
 */
object LogEntryFactory {
    fun createEntry(id: Int, name: String, typeString: String, metadata: String = ""): LogEntry<*> {
        return when (typeString.lowercase()) {
            "raw" -> RawEntry(id, name, metadata)
            "boolean" -> BooleanEntry(id, name, metadata)
            "int64", "int", "long" -> Int64Entry(id, name, metadata)
            "float" -> FloatEntry(id, name, metadata)
            "double" -> DoubleEntry(id, name, metadata)
            "string" -> StringEntry(id, name, metadata)
            "boolean[]" -> BooleanArrayEntry(id, name, metadata)
            "int64[]", "int[]", "long[]" -> Int64ArrayEntry(id, name, metadata)
            "float[]" -> FloatArrayEntry(id, name, metadata)
            "double[]" -> DoubleArrayEntry(id, name, metadata)
            "string[]" -> StringArrayEntry(id, name, metadata)
            else -> CustomEntry(id, name, typeString, { it }, { it }, metadata)
        }
    }

    // Type-safe factory methods
    fun createRawEntry(id: Int, name: String, metadata: String = ""): RawEntry =
        RawEntry(id, name, metadata)

    fun createBooleanEntry(id: Int, name: String, metadata: String = ""): BooleanEntry =
        BooleanEntry(id, name, metadata)

    fun createInt64Entry(id: Int, name: String, metadata: String = ""): Int64Entry =
        Int64Entry(id, name, metadata)

    fun createFloatEntry(id: Int, name: String, metadata: String = ""): FloatEntry =
        FloatEntry(id, name, metadata)

    fun createDoubleEntry(id: Int, name: String, metadata: String = ""): DoubleEntry =
        DoubleEntry(id, name, metadata)

    fun createStringEntry(id: Int, name: String, metadata: String = ""): StringEntry =
        StringEntry(id, name, metadata)

    fun createBooleanArrayEntry(id: Int, name: String, metadata: String = ""): BooleanArrayEntry =
        BooleanArrayEntry(id, name, metadata)

    fun createInt64ArrayEntry(id: Int, name: String, metadata: String = ""): Int64ArrayEntry =
        Int64ArrayEntry(id, name, metadata)

    fun createFloatArrayEntry(id: Int, name: String, metadata: String = ""): FloatArrayEntry =
        FloatArrayEntry(id, name, metadata)

    fun createDoubleArrayEntry(id: Int, name: String, metadata: String = ""): DoubleArrayEntry =
        DoubleArrayEntry(id, name, metadata)

    fun createStringArrayEntry(id: Int, name: String, metadata: String = ""): StringArrayEntry =
        StringArrayEntry(id, name, metadata)

    fun <T> createStructEntry(id: Int, name: String, struct: Struct<T>, metadata: String = ""): StructEntry<T> =
        StructEntry(id, name, metadata, struct = struct)

    /**
     * Create a custom entry with custom encoder/decoder functions
     */
    fun <T> createCustomEntry(
        id: Int,
        name: String,
        typeString: String,
        encoder: (T) -> ByteBuffer,
        decoder: (ByteBuffer) -> T,
        metadata: String = ""
    ): CustomEntry<T> = CustomEntry(id, name, typeString, encoder, decoder, metadata)
}