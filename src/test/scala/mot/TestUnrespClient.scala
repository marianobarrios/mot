package mot

import java.util.concurrent.TimeUnit
import Util.FunctionToRunnable

object TestUnrespClient {

  def main(args: Array[String]) = {
    val ctx = new Context(4001)
    val client = new Client(ctx, "test-client", queueSize = 100000, readerBufferSize = 100000, writerBufferSize = 100000)
    val target = Address("10.70.134.145", 5000)
    val msg = Message.fromArray(Nil, "x".getBytes)
    def send() = {
      while (true) {
        val res = client.sendRequest(target, msg, 10000)
      }
    }
    val clientThread1 = new Thread(send _, "client-thread-1").start()
    val clientThread2 = new Thread(send _, "client-thread-2").start()
    val clientThread3 = new Thread(send _, "client-thread-3").start()
  }

}