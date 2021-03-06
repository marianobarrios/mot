package mot.impl

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.Duration
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal
import com.typesafe.scalalogging.slf4j.StrictLogging
import mot.Address
import mot.Client
import mot.LocalClosedException
import mot.Message
import mot.dump.Direction
import mot.dump.Operation
import mot.dump.TcpEvent
import lbmq.LinkedBlockingMultiQueue
import mot.util.Util.FunctionToRunnable
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents the link between the client and one server.
 * This connector will create connections and re-create them forever when they terminate with errors.
 */
final class ClientConnector(val client: Client, val target: Address) extends StrictLogging {

  val creationTime = System.nanoTime()

  val sendingQueue = new LinkedBlockingMultiQueue[String, OutgoingEvent]

  val messagesQueue = {
    val key = "messages"
    sendingQueue.addSubQueue(key, 100 /* priority */, client.maxQueueSize /* capacity */)
    sendingQueue.getSubQueue(key)
  }
  
  val flowControlQueue = {
    val key = "flow-control"
    sendingQueue.addSubQueue("flow-control", 10 /* priority */, 5 /* capacity */)
    sendingQueue.getSubQueue(key)
  }

  val writerThread = new Thread(connectLoop _, s"mot(${client.name})-writer-for-$target")

  private val closed = new AtomicBoolean

  val pendingResponses =
    new ConcurrentHashMap[Int /* request id */ , PendingResponse](
      1000 /* initial capacity */ ,
      0.5f /* load factor */ )

  val notYetConnected = new Exception("not yet connected")

  @volatile var currentConnection: Try[ClientConnection] = Failure(notYetConnected)

  val requestCounter = new AtomicInteger

  val lastUse = new AtomicLong(System.nanoTime())

  val timeoutsCounter = new AtomicLong
  val unrespondableSentCounter = new AtomicLong
  val respondableSentCounter = new AtomicLong
  val responsesReceivedCounter = new AtomicLong
  val triedToSendTooLargeMessage = new AtomicLong

  writerThread.start()
  logger.debug(s"creating connector: ${client.name}->$target")

  def offerMessage(message: Message, timeout: Long, unit: TimeUnit): Boolean = {
    lastUse.lazySet(System.nanoTime())
    messagesQueue.offer(OutgoingMessage(message, None), timeout, unit)
  }

  def offerRequest(message: Message, pendingResponse: PendingResponse, timeout: Long, unit: TimeUnit): Boolean = {
    lastUse.lazySet(System.nanoTime())
    // It is necessary to add the pending response to the map before enqueuing, to avoid a race between that and the
    // arrival of the response.
    pendingResponses.put(pendingResponse.requestId, pendingResponse)
    val success = messagesQueue.offer(OutgoingMessage(message, Some(pendingResponse)), timeout, unit)
    if (success) {
      pendingResponse.scheduleExpiration()
    } else {
      pendingResponses.remove(pendingResponse.requestId)
    }
    success
  }

  def connectLoop(): Unit = {
    try {
      while (!closed.get) {
        connectSocket()
        for (conn <- currentConnection)
          conn.startAndBlockWriting()
      }
    } catch {
      case NonFatal(e) => client.context.uncaughtErrorHandler.handle(e)
    }
    logger.trace("Client connector finished")
  }

  val minimumDelay = Duration(1000, TimeUnit.MILLISECONDS)

  /*
   * Connects a socket for sending messages. In case of failures, retries indefinitely
   */
  private def connectSocket(): Unit = {
    val dumper = client.context.dumper
    val prospConn = ProspectiveConnection(target, client.name)
    currentConnection = Failure(notYetConnected)
    var lastAttempt = -1L
    while (!closed.get && currentConnection.isFailure) {
      lastAttempt = System.nanoTime()
      try {
        val resolved = InetAddress.getAllByName(target.host).toSeq // DNS resolution
        var i = 0
        while (!closed.get && currentConnection.isFailure && i < resolved.size) {
          val addr = resolved(i).getHostAddress
          val addrStr = s"${target.host}/$addr:${target.port}"
          val socketAddress = new InetSocketAddress(addr, target.port)
          try {
            val socket = new Socket
            logger.debug(s"connecting to $addrStr (address ${i + 1} of ${resolved.size})")
            socket.connect(socketAddress, ClientConnector.connectTimeout)
            logger.info(s"socket to $addrStr connected")
            currentConnection = Success(new ClientConnection(this, socket))
          } catch {
            case e @ (_: SocketException | _: SocketTimeoutException) =>
              logger.info(s"cannot connect to $addrStr: ${e.getMessage}.")
              logger.trace("", e)
              dumper.dump(TcpEvent(prospConn, Direction.Outgoing, Operation.FailedAttempt, e.getMessage))
              currentConnection = Failure(e)
          }
          i += 1
        }
      } catch {
        case e: UnknownHostException =>
          logger.info(s"cannot resolve ${target.host}: ${e.getMessage}.")
          logger.trace("", e)
          dumper.dump(TcpEvent(prospConn, Direction.Outgoing, Operation.FailedNameResolution, e.getMessage))
          currentConnection = Failure(e)
      }
      waitIfNecessary(lastAttempt)
    }
    if (closed.get)
      currentConnection = Failure(new LocalClosedException)
  }

  private def waitIfNecessary(lastAttempt: Long): Unit = {
    val elapsed = Duration(System.nanoTime() - lastAttempt, TimeUnit.NANOSECONDS)
    val remaining = minimumDelay - elapsed
    Thread.sleep(math.max(remaining.toMillis, 0))
  }

  def close(): Unit = {
    logger.debug(s"Closing connector ${client.name}->$target")
    closed.set(true)
    currentConnection.foreach(_.close())
    writerThread.join()
    currentConnection.foreach(_.readerThread.join())
  }

}

object ClientConnector {
  val connectTimeout = 3000
}
