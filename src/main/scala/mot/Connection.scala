package mot

import java.net.InetSocketAddress
import java.net.Socket

trait Connection {
  
  def localAddress: InetSocketAddress
  def remoteAddress: InetSocketAddress
  
  def prepareSocket(socket: Socket) {
    socket.setSoTimeout(Protocol.HeartBeatInterval.toMillis.toInt * 2)
    socket.setTcpNoDelay(true)
  }

}