package org.psilynx.psikit.core.datalog

import org.psilynx.psikit.core.wpi.Struct
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * A class that writes data logs to a file, with support for structured entries and metadata.
 */
class DataLogWriter internal constructor(private val channel: FileChannel) : AutoCloseable {
    /**
     * Creates a DataLogWriter for the given [Path].
     * @param path The path to the log file.
     */
    constructor(path: Path) :
            this(FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE))

    /**
     * Creates a DataLogWriter for the given file path string.
     * @param pathname The string path to the log file.
     */
    constructor(pathname: String) : this(File(pathname).toPath())

    /**
     * Creates a DataLogWriter for the given [Path] and writes a header.
     * @param path The path to the log file.
     * @param header The header string to write at the start of the log.
     */
    constructor(path: Path, header: String) : this(path) {
        start(header)
    }

    /**
     * Creates a DataLogWriter for the given file path string and writes a header.
     * @param pathname The string path to the log file.
     * @param header The header string to write at the start of the log.
     */
    constructor(pathname: String, header: String) : this(pathname) {
        start(header)
    }

    private val entryIDs = mutableSetOf<Int>()
    private val entries = mutableMapOf<Int, LogEntry<*>>()

    /**
     * Starts the log file by writing the header.
     * @param header Optional header string to include in the log file.
     */
    @JvmOverloads
    fun start(header: String = "") {
        channel.write(WpiLogHeader(extraHeaderData = header).toByteBuffer())
    }

    /**
     * Closes the underlying file channel.
     */
    override fun close() {
        channel.close()
    }

    /**
     * Adds a record to the log.
     * @param record The record to add.
     */
    fun addRecord(record: WpiLogRecord) {
        channel.write(record.toByteBuffer())
    }

    /**
     * Creates a new log entry.
     * @param id The ID of the entry.
     * @param name The name of the entry.
     * @param typeString The type string of the entry.
     * @param metadata Optional metadata for the entry.
     * @return The created LogEntry.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> createEntry(id: Int, name: String, typeString: String, metadata: String = ""): LogEntry<T> {
        val entry = LogEntryFactory.createEntry(id, name, typeString, metadata) as LogEntry<T>
        entries[id] = entry
        return entry
    }

    /**
     * Creates a new structured log entry.
     * @param id The ID of the entry.
     * @param name The name of the entry.
     * @param metadata Optional metadata for the entry.
     * @param struct The struct definition for the entry.
     * @return The created StructEntry.
     */
    fun <T> createStructEntry(id: Int, name: String, metadata: String = "", struct: Struct<T>): StructEntry<T> {
        val entry = LogEntryFactory.createStructEntry(id, name, struct, metadata)
        entries[id] = entry
        return entry
    }

    /**
     * Starts a log entry, writing its start record.
     * @param entry The entry to start.
     * @param timestamp The timestamp for the record.
     * @param metadata Optional metadata for the record.
     */
    fun <T> startEntry(entry: LogEntry<T>, timestamp: Long, metadata: String = entry.metadata) {
        addRecord(entry.startRecord(timestamp, metadata))
        entryIDs.add(entry.id)
    }

    /**
     * Sets the metadata for an entry and writes a metadata record.
     * @param entry The entry to update.
     * @param timestamp The timestamp for the record.
     * @param metadata The metadata string.
     */
    fun <T> setEntryMetadata(entry: LogEntry<T>, timestamp: Long, metadata: String) {
        addRecord(entry.setMetadataRecord(timestamp, metadata))
        entry.metadata = metadata
    }

    /**
     * Finishes an entry, writing its finish record.
     * @param entry The entry to finish.
     * @param timestamp The timestamp for the record.
     */
    fun <T> finishEntry(entry: LogEntry<T>, timestamp: Long) {
        addRecord(entry.finishRecord(timestamp))
        entry.isFinished = true
        entries.remove(entry.id)
    }

    /**
     * Adds data to an entry.
     * @param entry The entry to add data to.
     * @param timestamp The timestamp for the record.
     * @param value The data value.
     */
    fun <T> addEntryData(entry: LogEntry<T>, timestamp: Long, value: T) {
        addRecord(entry.dataRecord(timestamp, value))
    }

    /**
     * Retrieves an entry by its ID.
     * @param id The ID of the entry.
     * @return The LogEntry with the given ID, or null if not found.
     */
    fun getEntry(id: Int): LogEntry<*>? = entries[id]
}