package mot.buffer

import java.io.OutputStream

class WriteBuffer(val os: OutputStream, val bufferSize: Int) {

  val array = Array.ofDim[Byte](bufferSize)

  private var position = 0

  def writen() = position
  def remaining() = bufferSize - position
  
  def isFull() = position == bufferSize
  def hasData() = position > 0
  
  def put(byte: Byte) {
    if (isFull)
      flush()
    array(position) = byte
    position += 1
  }

  def put(array: Array[Byte], offset: Int, length: Int) {
    var bytesPut = 0
    while (bytesPut < length) {
      if (isFull)
        flush()
      val bytesToPut = math.min(remaining, length - bytesPut)
      System.arraycopy(array, offset + bytesPut, this.array, position, bytesToPut)
      position += bytesToPut
      bytesPut += bytesToPut
    }
  }

  def put(array: Array[Byte]): Unit = put(array, 0, array.length)

  /**
   * Put short value in network (big-endian) byte order.
   */
  def putShort(value: Short) = putShortBigEndian(value)

  /**
   * Put int value in network (big-endian) byte order.
   */
  def putInt(value: Int) = putIntBigEndian(value)

  private def putShortBigEndian(x: Short) {
    put(WriteBuffer.byte1(x))
    put(WriteBuffer.byte0(x))
  }

  private def putIntBigEndian(x: Int) {
    put(WriteBuffer.byte3(x))
    put(WriteBuffer.byte2(x))
    put(WriteBuffer.byte1(x))
    put(WriteBuffer.byte0(x))
  }
  
  def flush() {
    os.write(array, 0, position)
    position = 0
  }

}

object WriteBuffer {
  def byte3(x: Int) = (x >> 24).toByte
  def byte2(x: Int) = (x >> 16).toByte
  def byte1(x: Int) = (x >> 8).toByte
  def byte0(x: Int) = x.toByte
}