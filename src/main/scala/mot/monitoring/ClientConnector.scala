package mot.monitoring

import mot.util.LiveTabler
import mot.util.Tabler
import mot.Context
import mot.util.Util.CeilingDivider
import scala.collection.immutable
import mot.Address

class ClientConnector(context: Context) extends MultiCommandHandler {

  val subcommands = immutable.Seq(Live, Totals)

  val name = "client-connector"

  val helpLine = "Show connector statistics."

  val interval = 1000

  class CommandException(error: String) extends Exception(error)

  object Live extends CommandHandler {
    val name = "live"
    def handle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String], partWriter: String => Unit): String = {
      import LiveTabler._
      import Tabler._
      val connector = try {
        getConnector(commands)
      } catch {
        case e: CommandException => return e.getMessage
      }
      val connection = connector.currentConnection.getOrElse(return "Not currently connected")
      LiveTabler.draw(
        partWriter,
        Col[Int]("SND-QUEUE", 9, Alignment.Right),
        Col[Int]("PENDING", 9, Alignment.Right),
        Col[Long]("SENT-UNRSP", 11, Alignment.Right),
        Col[Long]("SENT-RESP", 11, Alignment.Right),
        Col[Long]("RESP-RCVD", 11, Alignment.Right),
        Col[Long]("TIMEOUTS", 11, Alignment.Right),
        Col[Long]("SND-TOO-BIG", 11, Alignment.Right),
        Col[Long]("KB-READ", 11, Alignment.Right),
        Col[Long]("KB-WRITTEN", 11, Alignment.Right),
        Col[Long]("BUF-FILLINGS", 12, Alignment.Right),
        Col[Long]("BUF-FULL", 11, Alignment.Right)) { printer =>
          val unrespondableSent = Differ.fromVolatile(connector.unrespondableSentCounter _)
          val respondableSent = Differ.fromVolatile(connector.respondableSentCounter _)
          val responsesReceived = Differ.fromVolatile(connector.responsesReceivedCounter _)
          val timeouts = Differ.fromVolatile(connector.timeoutsCounter _)
          val sendTooLarge = Differ.fromVolatile(connector.triedToSendTooLargeMessage _)
          val bytesRead = Differ.fromVolatile(connection.readBuffer.bytesCount)
          val bytesWriten = Differ.fromVolatile(connection.writeBuffer.bytesCount)
          val fillings = Differ.fromVolatile(connection.readBuffer.readCount)
          val fillingsFull = Differ.fromVolatile(connection.readBuffer.bufferFullCount)
          while (true) {
            if (connection.isClosed)
              return "Disconnected"
            Thread.sleep(interval)
            printer(
              connector.sendingQueue.size,
              connection.pendingResponses.size,
              unrespondableSent.diff(),
              respondableSent.diff(),
              responsesReceived.diff(),
              timeouts.diff(),
              sendTooLarge.diff(),
              bytesRead.diff() /^ 1024,
              bytesWriten.diff() /^ 1024,
              fillings.diff(),
              fillingsFull.diff())
          }
        }
      throw new AssertionError
    }
  }

  object Totals extends SimpleCommandHandler {
    val name = "totals"
    def simpleHandle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String]): String = {
      val connector = try {
        getConnector(commands)
      } catch {
        case e: CommandException => return e.getMessage
      }
      val connection = connector.currentConnection.getOrElse(return "Not currently connected")
      "" +
        f"Sending queue size:                  ${connector.sendingQueue.size}%11d\n" +
        f"Pending responses:                   ${connection.pendingResponses.size}%11d\n" +
        f"Total unrespondable messages sent:   ${connector.unrespondableSentCounter}%11d\n" +
        f"Total respondable messages sent:     ${connector.respondableSentCounter}%11d\n" +
        f"Total responses received:            ${connector.responsesReceivedCounter}%11d\n" +
        f"Total timed out messages:            ${connector.timeoutsCounter}%11d\n"
        f"Total too large msgs. tried to send: ${connector.triedToSendTooLargeMessage}%11d\n"
        f"Total bytes read:                    ${connection.readBuffer.bytesCount}%11d\n" +
        f"Total bytes written:                 ${connection.writeBuffer.bytesCount}%11d\n" +
        f"Total buffer fillings:               ${connection.readBuffer.readCount}%11d\n" +
        f"Total full buffer fillings:          ${connection.readBuffer.bufferFullCount}%11d\n"
    }
  }

  def getConnector(commands: immutable.Seq[String]) = {
    if (commands.size < 2)
      throw new CommandException("Must specify client and target")
    val clientName +: targetName +: rest = commands
    val client = Option(context.clients.get(clientName)).getOrElse {
      throw new CommandException("Unknown client: " + clientName)
    }
    val target = Address.fromString(targetName)
    Option(client.connectors.get(target)).getOrElse(throw new CommandException("Unknown target: " + targetName))
  }

}