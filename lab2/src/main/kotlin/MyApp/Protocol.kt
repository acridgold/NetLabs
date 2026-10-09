package MyApp

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

const val ALOOO = 0x4E534B503637 // "NSKP67"
const val MAX_NAME_BYTES = 4096
const val MAX_FILE_SIZE = 1L shl 40
const val STATUS_FAIL = 0
const val STATUS_OK = 1
const val STATUS_READY = 2
const val BUFFER_SIZE = 64 * 1024

class ProtocolException(message: String) : IOException(message)

data class FileHeader(val fileName: String, val fileSize: Long) {
    val nameBytes: ByteArray get() = fileName.toByteArray(Charsets.UTF_8)
}

enum class Reply(val code: Int) {
    FAIL(STATUS_FAIL),
    OK(STATUS_OK),
    READY(STATUS_READY);

    companion object {
        fun fromCode(code: Int): Reply? = entries.firstOrNull { it.code == code }
    }
}

interface TransferProtocol {
    fun validate(header: FileHeader)

    fun writeHeader(out: DataOutputStream, header: FileHeader)

    fun readHeader(input: DataInputStream): FileHeader?

    fun writeReply(out: DataOutputStream, reply: Reply)

    fun readReply(input: DataInputStream): Reply?
}

class TcpTransferProtocol : TransferProtocol {
    private companion object {
        const val MAGIC_BYTES = 6
    }

    override fun validate(header: FileHeader) {
        val nameLen = header.nameBytes.size
        if (nameLen !in 1..MAX_NAME_BYTES) {
            throw ProtocolException("длина имени $nameLen байт вне диапазона 1..$MAX_NAME_BYTES")
        }
        if (header.fileSize !in 0..MAX_FILE_SIZE) {
            throw ProtocolException("размер ${header.fileSize} байт вне диапазона 0..$MAX_FILE_SIZE")
        }
    }

    override fun writeHeader(out: DataOutputStream, header: FileHeader) {
        validate(header)
        val name = header.nameBytes
        for (shift in (MAGIC_BYTES - 1) * 8 downTo 0 step 8) {
            out.write(((ALOOO ushr shift) and 0xFF).toInt())
        }
        out.writeInt(name.size)
        out.write(name)
        out.writeLong(header.fileSize)
        out.flush()
    }

    override fun readHeader(input: DataInputStream): FileHeader? {
        val first = input.read()
        if (first < 0) return null

        var magic = first.toLong()
        repeat(MAGIC_BYTES - 1) {
            magic = (magic shl 8) or input.readUnsignedByte().toLong()
        }
        if (magic != ALOOO) throw ProtocolException("неверная сигнатура протокола")

        val nameLen = input.readInt()
        if (nameLen !in 1..MAX_NAME_BYTES) throw ProtocolException("недопустимая длина имени: $nameLen байт")
        val nameBytes = ByteArray(nameLen)
        input.readFully(nameBytes)

        val size = input.readLong()
        if (size !in 0..MAX_FILE_SIZE) throw ProtocolException("недопустимый размер файла: $size байт")
        return FileHeader(String(nameBytes, Charsets.UTF_8), size)
    }

    override fun writeReply(out: DataOutputStream, reply: Reply) {
        out.writeByte(reply.code)
        out.flush()
    }

    override fun readReply(input: DataInputStream): Reply? {
        val b = input.read()
        if (b < 0) return null
        return Reply.fromCode(b) ?: throw ProtocolException("неизвестный код ответа: $b")
    }
}