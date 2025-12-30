//> using scala 3.3.7
//> using dep com.lihaoyi::upickle::4.4.2
//> using dep com.lihaoyi::os-lib::0.11.6

import upickle.default.*
import java.time.Instant
import scala.util.Try

case class LogEntry(
    clientIp: Option[String] = None,
    client_ip: Option[String] = None,
    path: Option[String] = None,
    status: Option[Int] = None
) derives ReadWriter {
  def ip: String = clientIp.orElse(client_ip).getOrElse("unknown")
}

case class IpStats(
    requestCount: Int = 0,
    notFoundCount: Int = 0,
    forbiddenCount: Int = 0,
    suspiciousPathCount: Int = 0,
    paths: Set[String] = Set.empty
)

val suspiciousPaths = Set(
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
  "/vendor",
  "/backup",
  "/.aws",
  "/.ssh",
  "/debug",
  "/console",
  "/manager",
  "/jmx",
  "/solr",
  "/jenkins",
  "/struts",
  "/axis2",
  "/.svn",
  "/elmah",
  "/trace",
  "/druid",
  "/nacos",
  "/api/v1",
  "/graphql"
)

val maxSuspiciousPaths = 3
val max404Rate = 0.5
val minRequestsForRateCheck = 10

def isSuspicious(path: String): Boolean =
  suspiciousPaths.exists(path.toLowerCase.contains)

def shouldBlock(stats: IpStats): Boolean =
  stats.suspiciousPathCount >= maxSuspiciousPaths ||
    (stats.requestCount >= minRequestsForRateCheck &&
      stats.notFoundCount.toDouble / stats.requestCount > max404Rate)

def parseLine(line: String): Option[LogEntry] =
  Try(read[LogEntry](line)).toOption

def analyzeFile(path: os.Path): Map[String, IpStats] =
  val lines = if path.ext == "gz" then
    import java.util.zip.GZIPInputStream
    import java.io.{BufferedReader, InputStreamReader}
    val gis = GZIPInputStream(java.io.FileInputStream(path.toIO))
    val reader = BufferedReader(InputStreamReader(gis))
    try Iterator.continually(reader.readLine()).takeWhile(_ != null).toList
    finally reader.close()
  else os.read.lines(path).toList

  lines.flatMap(parseLine).foldLeft(Map.empty[String, IpStats]) {
    (acc, entry) =>
      val ip = entry.ip
      val current = acc.getOrElse(ip, IpStats())
      val status = entry.status.getOrElse(0)
      val path = entry.path.getOrElse("")
      acc.updated(
        ip,
        current.copy(
          requestCount = current.requestCount + 1,
          notFoundCount =
            current.notFoundCount + (if status == 404 then 1 else 0),
          forbiddenCount =
            current.forbiddenCount + (if status == 403 then 1 else 0),
          suspiciousPathCount =
            current.suspiciousPathCount + (if isSuspicious(path) then 1 else 0),
          paths =
            if isSuspicious(path) then current.paths + path else current.paths
        )
      )
  }

def generateBlacklistConf(ips: Set[String]): String =
  val ipList = ips.map(ip => s""""$ip"""").mkString(", ")
  s"security.blacklist = [$ipList]"

def readExistingBlacklist(path: os.Path): Set[String] =
  if !os.exists(path) then Set.empty
  else
    val content = os.read(path)

    val IpRegex = "\"([^\"]+)\"".r
    IpRegex.findAllMatchIn(content).map(_.group(1)).toSet

@main def run(logsDir: String, projectDir: String): Unit =

  val projectPath = os.Path(projectDir)
  val dir = os.Path(logsDir)
  require(os.exists(dir), s"Directory not found: $dir")
  require(os.exists(projectPath), s"Directory not found: $projectDir")

  val files = os.list(dir).filter(f => f.ext == "json" || f.ext == "gz")
  println(s"Analyzing ${files.size} files...")

  val allStats = files.foldLeft(Map.empty[String, IpStats]) { (acc, file) =>
    println(s"  Processing: ${file.last}")
    val fileStats = analyzeFile(file)
    fileStats.foldLeft(acc) { case (a, (ip, stats)) =>
      val current = a.getOrElse(ip, IpStats())
      a.updated(
        ip,
        IpStats(
          requestCount = current.requestCount + stats.requestCount,
          notFoundCount = current.notFoundCount + stats.notFoundCount,
          forbiddenCount = current.forbiddenCount + stats.forbiddenCount,
          suspiciousPathCount =
            current.suspiciousPathCount + stats.suspiciousPathCount,
          paths = current.paths ++ stats.paths
        )
      )
    }
  }

  val blocked = allStats.filter((_, stats) => shouldBlock(stats))
  val newBlockedIps = blocked.keySet

  println(s"\n=== Results ===")
  println(s"Total IPs: ${allStats.size}")
  println(s"Blocked IPs: ${blocked.size}")

  if blocked.nonEmpty then
    println("\n=== Blocked IPs ===")
    blocked.toList.sortBy(-_._2.suspiciousPathCount).foreach { (ip, stats) =>
      println(
        s"  $ip -> requests=${stats.requestCount}, 404s=${stats.notFoundCount}, suspicious=${stats.suspiciousPathCount}"
      )
      if stats.paths.nonEmpty then
        println(s"    paths: ${stats.paths.mkString(", ")}")
    }

    val existingIps = readExistingBlacklist(outFile)
    val mergedIps = existingIps ++ newBlockedIps
    val confContent = generateBlacklistConf(mergedIps)
    val outFile = projectPath / "server" / "conf" / "blacklist.conf"
    os.write.over(outFile, confContent)
    println(s"\nGenerated (merged): $outFile")
    println(s"Previous IPs: ${existingIps.size}")
    println(s"New IPs: ${blocked.size}")
    println(s"Total IPs: ${mergedIps.size}")
