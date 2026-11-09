package socks.proxy

import socks.eventloop.ChannelHandler
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.CancelledKeyException
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel


internal class Relay(
    clientChannel: SocketChannel,
    clientKey: SelectionKey,
    remoteChannel: SocketChannel,
    remoteKey: SelectionKey,
    bufferSize: Int,
    initialToRemote: ByteArray,
    initialToClient: ByteArray,
    private val onFinished: () -> Unit,
) {
    private class Pipe(size: Int) {
        val buffer: ByteBuffer = ByteBuffer.allocateDirect(size) // режим записи
        var sourceEof = false
        var sinkShut = false
        var transferred = 0L // сколько байт прочитано из источника
    }

    private val up = Pipe(bufferSize)
    private val down = Pipe(bufferSize)
    private val client = Side(clientChannel, clientKey, readPipe = up, writePipe = down)
    private val remote = Side(remoteChannel, remoteKey, readPipe = down, writePipe = up)
    private var finished = false


    val bytesToRemote: Long get() = up.transferred
    val bytesToClient: Long get() = down.transferred

    init {
        up.buffer.put(initialToRemote)
        down.buffer.put(initialToClient)
        client.peer = remote
        remote.peer = client
    }

    fun start() {
        client.key.attach(client)
        remote.key.attach(remote)
        try {
            client.flush()
            remote.flush()
        } catch (_: IOException) {
            finish()
            return
        }
        refresh()
    }

    private inner class Side(
        val channel: SocketChannel,
        val key: SelectionKey,
        private val readPipe: Pipe,
        private val writePipe: Pipe,
    ) : ChannelHandler {
        lateinit var peer: Side

        override fun onReady(key: SelectionKey) {
            if (finished) return
            if (key.isReadable) read()
            if (key.isValid && key.isWritable) flush()
            refresh()
        }

        override fun onError(cause: Exception) = finish()

        private fun read() {
            val n = channel.read(readPipe.buffer)
            if (n < 0) readPipe.sourceEof = true
            else if (n > 0) {
                readPipe.transferred += n
                peer.flush() // сразу пробуем отправить, не дожидаясь нового круга селектора
            }
        }

        fun flush() {
            val b = writePipe.buffer
            if (b.position() == 0) return
            b.flip()
            try {
                channel.write(b)
            } finally {
                b.compact()
            }
        }


        fun updateInterest() {
            var ops = 0
            if (!readPipe.sourceEof && readPipe.buffer.hasRemaining()) ops = ops or SelectionKey.OP_READ
            if (writePipe.buffer.position() > 0 && !writePipe.sinkShut) ops = ops or SelectionKey.OP_WRITE
            key.interestOps(ops)
        }
    }

    private fun refresh() {
        if (finished) return
        try {
            shutdownIfDrained(up, sink = remote)
            shutdownIfDrained(down, sink = client)
            if (up.sinkShut && down.sinkShut) {
                finish()
                return
            }
            client.updateInterest()
            remote.updateInterest()
        } catch (_: IOException) {
            finish()
        } catch (_: CancelledKeyException) {
            finish()
        }
    }

    private fun shutdownIfDrained(pipe: Pipe, sink: Side) {
        if (pipe.sourceEof && pipe.buffer.position() == 0 && !pipe.sinkShut) {
            sink.channel.shutdownOutput()
            pipe.sinkShut = true
        }
    }

    private fun finish() {
        if (finished) return
        finished = true
        onFinished()
    }
}
