package mot.message

import mot.buffer.ReadBuffer
import mot.BadDataException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import mot.buffer.WriteBuffer
import scala.collection.immutable
import scala.collection.mutable.HashMap
import com.typesafe.scalalogging.slf4j.StrictLogging

trait MessageBase {
  def writeToBuffer(writeBuffer: WriteBuffer)
}

object MessageBase extends StrictLogging {

  def readFromBuffer(readBuffer: ReadBuffer, maxLength: Int) = {
    val messageType = MessageType(readBuffer.get)
    val factory = fromMessageType(messageType)
    val msg = factory(readBuffer, maxLength)
    logger.trace("Read " + msg)
    msg
  }

  def fromMessageType(messageType: MessageType.Value): ((ReadBuffer, Int) => MessageBase) = {
    messageType match {
      case MessageType.Hello => Hello.factory
      case MessageType.Heartbeat => Heartbeat.factory
      case MessageType.Response => Response.factory
      case MessageType.Message => MessageFrame.factory
    }
  }

  def readAttributes(readBuffer: ReadBuffer): immutable.Seq[(String, Array[Byte])] = {
    val size = readBuffer.get
    if (size < 0)
      throw new BadDataException("Negative attribute number: " + size)
    // Optimize no-attributes case
    if (size == 0)
      return Vector.empty
    for (i <- 0 until size) yield {
      val name = new String(readByteSizeByteField(readBuffer), StandardCharsets.US_ASCII)
      val value = readShortByteField(readBuffer)
      name -> value
    }
  }

  def readAttributesAsStringMap(readBuffer: ReadBuffer) = {
    val seq = readAttributes(readBuffer)
    val map = new HashMap[String, String]
    map.sizeHint(seq.size)
    for ((key, value) <- seq) {
      val previous = map.put(key, new String(value, StandardCharsets.US_ASCII))
      if (previous.isDefined)
        throw new BadDataException("Duplicated attribute key: " + key)
    }
    map.toMap
  }
  
  def getAttributeAsString(attributes: Map[String, String], key: String) = {
    attributes.get(key).getOrElse(throw new BadDataException("Missing attribute in message: " + key))
  }
  
  def readIntSizeByteField(readBuffer: ReadBuffer, maxLength: Int) = {
    val length = readBuffer.getInt()
    if (length > maxLength) {
      // Protect from DOS attacks
      throw new BadDataException(s"Field size ($length) bigger than maximum allowed length ($maxLength)")
    }
    readByteField(readBuffer, length)
  }

  def readByteSizeByteField(readBuffer: ReadBuffer) = {
    val length = readBuffer.get()
    readByteField(readBuffer, length)
  }

  def readShortByteField(readBuffer: ReadBuffer) = {
    val length = readBuffer.getShort()
    readByteField(readBuffer, length)
  }
  
  private def readByteField(readBuffer: ReadBuffer, size: Int) = {
    if (size < 0)
      throw new BadDataException("Negative length: " + size)
    val fieldBuffer = ByteBuffer.allocate(size)
    readBuffer.transferToByteBuffer(fieldBuffer, size)
    fieldBuffer.array
  }

  def writeAttributes(writeBuffer: WriteBuffer, attributes: immutable.Seq[(String, Array[Byte])]) = {
    writeBuffer.put(attributes.size.toByte)
    for ((name, value) <- attributes) {
      writeByteSizeByteField(writeBuffer, name.getBytes(StandardCharsets.US_ASCII))
      writeShortByteField(writeBuffer, value)
    }
  }

  def writeByteSizeByteField(writeBuffer: WriteBuffer, array: Array[Byte]) = {
    writeBuffer.put(array.length.toByte)
    writeBuffer.put(array)
  }
  
  def writeShortByteField(writeBuffer: WriteBuffer, array: Array[Byte]) = {
    writeBuffer.putShort(array.length.toShort)
    writeBuffer.put(array)
  }
  
  def writeIntSizeByteMultiField(writeBuffer: WriteBuffer, length: Int, buffers: immutable.Seq[ByteBuffer]) = {
    writeBuffer.putInt(length)
    buffers.foreach(b => writeBuffer.put(b.array, b.arrayOffset, b.limit))
  }
  
}