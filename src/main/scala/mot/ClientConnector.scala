package mot

import java.net.Socket
import Util.FunctionToRunnable
import scala.util.control.NonFatal
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.LinkedBlockingQueue
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Promise
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import io.netty.util.HashedWheelTimer
import com.typesafe.scalalogging.slf4j.StrictLogging
import scala.concurrent.duration.Duration

/**
 * Represents the link between the client and one server.
 * This connector will create connections and re-create them forever when they terminate with errors.
 */
class ClientConnector(val client: Client, val target: Address) extends StrictLogging {

  val sendingQueue = new LinkedBlockingQueue[(Message, Option[PendingResponse])](client.queueSize)

  val thread = new Thread(connectLoop _, s"mot(${client.name})-writer-for-$target")
  val closed = new AtomicBoolean

  @volatile var currentConnection: Option[ClientConnection] = None

  /**
   * Hold the last exception than occurred trying to establish a connection. Does not hold exceptions
   * produced during the connection.
   */
  @volatile var lastConnectingException: Option[Throwable] = None

  @volatile var lastUse = System.nanoTime()

  // It need not be atomic as the expirator has only one thread
  @volatile var timeoutsCounter = 0L

  val unrespondableEnqueued = new AtomicLong
  val respondableEnqueued = new AtomicLong
  
  @volatile var expiredInQueue = 0L
  @volatile var unrespondableSentCounter = 0L
  @volatile var respondableSentCounter = 0L
  @volatile var responsesReceivedCounter = 0L

  @volatile var triedToSendTooLargeMessage = 0L
  
  val promiseExpirator = {
    val tf = new ThreadFactory {
      def newThread(r: Runnable) = new Thread(r, s"mot(${client.name})-promise-expiratior-for-$target")
    }
    new HashedWheelTimer(tf, 200, TimeUnit.MILLISECONDS, 1000)
  }

  thread.start()
  logger.debug(s"Creating connector: ${client.name}->$target")

  def isConnected() = currentConnection.isDefined
  def lastConnectingError() = lastConnectingException
  def isErrorState() = lastConnectingException.isDefined

  def offerMessage(message: Message) = {
    lastUse = System.nanoTime()
    val success = sendingQueue.offer((message, None))
    if (success)
      unrespondableEnqueued.incrementAndGet()
    success
  }

  def putMessage(message: Message): Unit = {
    lastUse = System.nanoTime()
    sendingQueue.put((message, None))
    unrespondableEnqueued.incrementAndGet()
  }

  def offerRequest(message: Message, pendingResponse: PendingResponse) = {
    lastUse = System.nanoTime()
    /* 
     * It is necessary to schedule the expiration before enqueuing to avoid a race condition between the assignment of the variable
     * with the task and the arrival of the response (in case it arrives quickly). This forces to unschedule the task if the 
     * enqueueing fails.
     */  
    pendingResponse.scheduleExpiration()
    val success = sendingQueue.offer((message, Some(pendingResponse)))
    if (!success) {
      pendingResponse.unscheduleExpiration()
      respondableEnqueued.incrementAndGet()
    }
    success
  }

  def putRequest(message: Message, pendingResponse: PendingResponse) = {
    lastUse = System.nanoTime()
    pendingResponse.scheduleExpiration()
    sendingQueue.put((message, Some(pendingResponse)))
    respondableEnqueued.incrementAndGet()
  }

  def connectLoop() = {
    try {
      var socket = connectSocket()
      while (!closed.get) {
        val conn = new ClientConnection(this, socket.get)
        currentConnection = Some(conn)
        conn.startAndBlockWriting()
        currentConnection = None
        socket = connectSocket()
      }
    } catch {
      case NonFatal(e) => client.context.uncaughtErrorHandler.handle(e)
    }
    logger.trace("Client connector finished")
  }

  /*
   * Connects a socket for sending messages. In case of failures, retries indefinitely
   */
  private def connectSocket() = {
    def op() = {
      logger.trace(s"Connecting to $target")
      val socket = new Socket
      // Create a socket address for each connection attempt, to avoid caching DNS resolution forever
      val socketAddress = new InetSocketAddress(target.host, target.port)
      socket.connect(socketAddress, client.connectTimeout)
      logger.info(s"Socket to $target connected")
      socket
    }
    val start = System.nanoTime()
    val res = Util.retryConditionally(op, closed) {
      // Must catch anything that is network-related (to retry) but nothing that could be a bug.
      // Note that DNS-related errors do not throw SocketExceptions
      case e: IOException =>
        logger.info(s"Cannot connect to $target: ${e.getMessage}.")
        val delay = System.nanoTime() - start
        if (delay > ClientConnector.optimisticTolerance.toNanos && lastConnectingException.isEmpty) {
          lastConnectingException = Some(e) // used also as a flag of error (when isDefined)
          logger.info(s"Connector set to error state afer optimistic tolerance of ${ClientConnector.optimisticTolerance}")
        }
    }
    lastConnectingException = None
    res
  }

  def close() {
    logger.debug(s"Closing connector ${client.name}->$target")
    closed.set(true)
    currentConnection.foreach(_.close())
    thread.join()
    currentConnection.foreach(_.readerThread.join())
  }

}

object ClientConnector {
  val optimisticTolerance = Duration(10, TimeUnit.SECONDS)
}
