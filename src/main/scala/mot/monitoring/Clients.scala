package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._
import mot.util.Util

class Clients(context: Context) extends SimpleCommandHandler {

  val name = "clients"
  val helpLine = "Print information about clients"

  def simpleHandle(processedCommands: Seq[String], commands: Seq[String]) = {
    import Tabler._
    import Alignment._
    Tabler.draw(
      Col[String]("NAME", 20, Left),
      Col[Int]("MAX-MSG-LEN", 11, Right),
      Col[Int]("MAX-SND-QUEUE", 13, Right),
      Col[Int]("READBUF-SIZE", 13, Right),
      Col[Int]("WRITEBUF-SIZE", 13, Right),
      Col[String]("TOLERANCE", 13, Left),
      Col[Int]("CONNECTORS", 12, Right)) { printer =>
        for (client <- context.clients.values) {
          printer(
            client.name,
            client.maxLength,
            client.maxQueueSize,
            client.readBufferSize,
            client.writeBufferSize,
            Util.durationToString(client.tolerance), 
            client.connectors.size)
        }
      }
  }

}
