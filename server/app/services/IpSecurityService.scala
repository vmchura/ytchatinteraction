package services

import javax.inject.{Inject, Singleton}
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._
import play.api.Logging

case class IpStats(
    requestCount: Int = 0,
    notFoundCount: Int = 0,
    forbiddenCount: Int = 0,
    suspiciousPathCount: Int = 0,
    firstSeen: Instant = Instant.now(),
    lastSeen: Instant = Instant.now()
)

@Singleton
class IpSecurityService @Inject() () extends Logging {

  private val ipStats = new ConcurrentHashMap[String, IpStats]()
  private val blacklist = ConcurrentHashMap.newKeySet[String]()

  private val suspiciousPaths = Set(
    "/wp-admin",
    "/wp-login",
    "/.env",
    "/phpmyadmin",
    "/actuator",
    "/.git",
    "/config",
    "/xmlrpc",
    "/wp-content",
    "/wp-includes",
    "/cgi-bin",
    "/shell",
    "/eval",
    "/vendor"
  )

  private val maxSuspiciousPaths = 3
  private val max404Rate = 0.5
  private val minRequestsForRateCheck = 10

  def recordRequest(ip: String, path: String, status: Int): Unit = {
    if (blacklist.contains(ip)) return

    val isSuspiciousPath = suspiciousPaths.exists(path.toLowerCase.contains)

    ipStats.compute(
      ip,
      (_, existing) => {
        val current = Option(existing).getOrElse(IpStats())
        current.copy(
          requestCount = current.requestCount + 1,
          notFoundCount = current.notFoundCount + (if (status == 404) 1 else 0),
          forbiddenCount =
            current.forbiddenCount + (if (status == 403) 1 else 0),
          suspiciousPathCount =
            current.suspiciousPathCount + (if (isSuspiciousPath) 1 else 0),
          lastSeen = Instant.now()
        )
      }
    )

    evaluateAndBlock(ip)
  }

  private def evaluateAndBlock(ip: String): Unit = {
    Option(ipStats.get(ip)).foreach { stats =>
      val shouldBlock =
        stats.suspiciousPathCount >= maxSuspiciousPaths ||
          (stats.requestCount > minRequestsForRateCheck &&
            stats.notFoundCount.toDouble / stats.requestCount > max404Rate)

      if (shouldBlock) {
        blacklist.add(ip)
        logger.warn(s"Auto-blocked IP: $ip - stats: $stats")
      }
    }
  }

  def isBlocked(ip: String): Boolean = blacklist.contains(ip)

  def getBlacklist: Set[String] = blacklist.asScala.toSet

  def getStats: Map[String, IpStats] = ipStats.asScala.toMap

  def manualBlock(ip: String): Unit = {
    blacklist.add(ip)
    logger.info(s"Manually blocked IP: $ip")
  }

  def unblock(ip: String): Unit = {
    blacklist.remove(ip)
    logger.info(s"Unblocked IP: $ip")
  }

  def clearStats(): Unit = {
    ipStats.clear()
    logger.info("Cleared all IP stats")
  }
}
