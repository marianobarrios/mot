package mot

import java.util.concurrent.ConcurrentHashMap
import mot.monitoring.Commands

class Context(monitoringPort: Int = 4001) {

  val clients = new ConcurrentHashMap[String, Client]
  val servers = new ConcurrentHashMap[String, Server]
  
  new Commands(this, monitoringPort).start()
  
}