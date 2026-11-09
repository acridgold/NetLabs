package socks.proxy

import socks.eventloop.ChannelHandler
import socks.protocol.Destination
import socks.protocol.Parsed
import socks.protocol.ReplyCode
import socks.protocol.Socks5
import socks.protocol.Socks5Codec
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel


internal class ClientHandshake(
    private val channel: SocketChannel,
    private val key: SelectionKey,
    private val listener: Listener,
) : ChannelHandler {

    interface Listener {

        fun onRequest(destination: Destination)


        fun onFinished()
    }


    class Handover(val toClient: ByteArray, val leftover: ByteArray)

    private enum class Phase { GREETING, REQUEST, AWAITING_TARGET, REPLYING_ERROR }

    private var phase = Phase.GREETING
    private val input: ByteBuffer = ByteBuffer.allocate(BUF_SIZE)   // режим записи
    private val output: ByteBuffer = ByteBuffer.allocate(BUF_SIZE)  // режим записи

    fun start() {
        key.attach(this)
        refresh()
    }


    fun succeeded(bound: InetSocketAddress?): Handover {
        output.put(Socks5Codec.reply(ReplyCode.SUCCEEDED, bound))
        output.flip()
        val toClient = ByteArray(output.remaining()).also { output.get(it) }
        input.flip()
        val leftover = ByteArray(input.remaining()).also { input.get(it) }
        return Handover(toClient, leftover)
    }

    fun failed(code: ReplyCode) {
        queueError(code)
        pump()
    }

    override fun onReady(key: SelectionKey) {
        if (key.isReadable) read()
        if (key.isValid && key.isWritable) flush()
        refresh()
    }

    override fun onError(cause: Exception) = listener.onFinished()

    private fun read() {
        if (phase != Phase.GREETING && phase != Phase.REQUEST) return
        val n = channel.read(input)
        if (n < 0) {
            listener.onFinished()
            return
        }
        if (n > 0) process()
    }

    private fun process() {
        while (true) {
            when (phase) {
                Phase.GREETING -> when (val r = Socks5Codec.parseGreeting(view())) {
                    Parsed.NeedMore -> return
                    Parsed.Malformed, is Parsed.Refused -> {
                        listener.onFinished()
                        return
                    }
                    is Parsed.Ok -> {
                        consume(r.consumed)
                        if (Socks5.METHOD_NO_AUTH in r.value.methods) {
                            output.put(Socks5Codec.methodSelection(Socks5.METHOD_NO_AUTH))
                            phase = Phase.REQUEST
                        } else {
                            output.put(Socks5Codec.methodSelection(Socks5.METHOD_NO_ACCEPTABLE))
                            phase = Phase.REPLYING_ERROR
                            return
                        }
                    }
                }

                Phase.REQUEST -> when (val r = Socks5Codec.parseRequest(view())) {
                    Parsed.NeedMore -> return
                    Parsed.Malformed -> {
                        listener.onFinished()
                        return
                    }
                    is Parsed.Refused -> {
                        queueError(r.code)
                        return
                    }
                    is Parsed.Ok -> {
                        consume(r.consumed)
                        phase = Phase.AWAITING_TARGET
                        listener.onRequest(r.value.destination)
                        return
                    }
                }

                else -> return
            }
        }
    }


    private fun view(): ByteBuffer = input.duplicate().flip()

    private fun consume(count: Int) {
        input.flip()
        input.position(count)
        input.compact()
    }

    private fun queueError(code: ReplyCode) {
        output.put(Socks5Codec.reply(code))
        phase = Phase.REPLYING_ERROR
    }

    private fun flush() {
        if (output.position() == 0) return
        output.flip()
        try {
            channel.write(output)
        } finally {
            output.compact()
        }
    }

    private fun pump() {
        try {
            flush()
            refresh()
        } catch (_: IOException) {
            listener.onFinished()
        }
    }


    private fun refresh() {
        if (!key.isValid) return
        val write = if (output.position() > 0) SelectionKey.OP_WRITE else 0
        val ops = when (phase) {
            Phase.GREETING, Phase.REQUEST ->
                (if (input.hasRemaining()) SelectionKey.OP_READ else 0) or write
            Phase.AWAITING_TARGET -> write
            Phase.REPLYING_ERROR -> {
                if (write == 0) {
                    listener.onFinished()
                    return
                }
                write
            }
        }
        key.interestOps(ops)
    }

    private companion object {
        const val BUF_SIZE = 4096
    }
}
