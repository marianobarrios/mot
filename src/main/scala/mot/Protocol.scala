package mot

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
  
  val PublisherNameMaxLength = Byte.MaxValue
  val AttributeNameMaxLength = Byte.MaxValue
  val AttributeValueMaxLength = Short.MaxValue
  val BodyMaxLength = Int.MaxValue
  
}