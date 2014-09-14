package mot

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Await
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit

/**
 * Binary protocol format (all string are UTF-8):
 * 
 * Connection header:
 * - Protocol version (1 octet)
 * - Sender name length (1 octet)
 * - Sender name (N octets)
 * 
 * Each message:
 * - Message type length (1 octet)
 * - Message type (N octets)
 * - Attribute quantity (1 octet)
 *   Each attribute:
 *   - Attribute name length (1 octet)
 *   - Attribute name
 *   - Attribute value length (1 octet)
 *   - Attribute value 
 * - Message body length (2 octets)
 * - Message body (N octets)
 *
 */
object Protocol {
  
  val ProtocolVersion = 1.toByte
  val HeartBeatInterval = 5000
  val HeartBeatIntervalNs = HeartBeatInterval.toLong * 1000 * 1000 
  
  val PartyNameMaxLength = Byte.MaxValue
  val AttributeNameMaxLength = Byte.MaxValue
  val AttributeValueMaxLength = Short.MaxValue
  val BodyMaxLength = Int.MaxValue
  
    
  def checkName(name: String) {
    if (!Util.isAscii(name))
      throw new IllegalArgumentException(s"Only US-ASCII characters are allowed in party name")
    if (name.length > PartyNameMaxLength)
      throw new IllegalArgumentException(s"Party name cannot be longer than $PartyNameMaxLength characters")
  }
  
  def wait[A](future: Future[A], stop: AtomicBoolean) = {
    var value: Option[A] = None
    while (value.isEmpty && !stop.get) {
      value = try {
        Some(Await.result(future, Duration(100, TimeUnit.MILLISECONDS)))
      } catch {
        case e: TimeoutException => None
      }
    }
    value
  }
  
}