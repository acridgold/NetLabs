package socks.dns

import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.net.InetAddress


internal object DnsCodec {

    class Answer(val id: Int, val question: Name?, val rcode: Int, val address: InetAddress?)

    fun buildQuery(name: Name, id: Int): ByteArray {
        val msg = Message.newQuery(Record.newRecord(name, Type.A, DClass.IN))
        msg.header.setID(id)
        return msg.toWire()
    }


    fun parseResponse(bytes: ByteArray): Answer? {
        val msg = try {
            Message(bytes)
        } catch (_: Exception) {
            return null
        }
        if (!msg.header.getFlag(Flags.QR.toInt())) return null
        val address = msg.getSection(Section.ANSWER)
            .filterIsInstance<ARecord>()
            .firstOrNull()?.address
        return Answer(msg.header.getID(), msg.question?.name, msg.header.rcode, address)
    }
}
