package io.snyk.plugin.client

import java.nio.file.{Path, Paths}
import java.util.UUID

import io.circe._
import io.circe.parser._
import io.circe.derivation._
import io.circe.syntax._
import java.io.PrintWriter
import java.net.URI

import com.softwaremill.sttp.{HttpURLConnectionBackend, Id, SttpBackend, Uri, sttp}

import scala.concurrent.{Future, Promise}
import scala.io.Source
import scala.util.Try
import monix.execution.Scheduler.Implicits.{global => scheduler}

import scala.concurrent.duration._

object SnykCredentials {
  implicit val decoder: Decoder[SnykCredentials] = deriveDecoder[SnykCredentials]
  implicit val encoder: Encoder[SnykCredentials] = deriveEncoder[SnykCredentials]

  def defaultConfigFile: Path = Paths.get(System.getProperty("user.home"), ".config/configstore/snyk.json")
  def defaultEndpoint: String = "http://snyk.io/api"

  def default: Try[SnykCredentials] = forPath(defaultConfigFile)

  def forPath(configFilePath: Path): Try[SnykCredentials] =
    Try { Source.fromFile(configFilePath.toFile).mkString } flatMap {
      str => decode[SnykCredentials](str).toTry
    }

  private[this] def endpointFor(configFilePath: Path): URI =
    new URI(forPath(configFilePath).toOption.flatMap(_.endpoint) getOrElse defaultEndpoint)

  def auth(
    openBrowser: URI => Unit,  // e.g. IntelliJ's  BrowserUtil.browse
    configFilePath: Path = defaultConfigFile
  ): Future[SnykCredentials] = {
    val endpointUri = endpointFor(configFilePath)
    val newToken = UUID.randomUUID()
    val loginUri = endpointUri.resolve(s"/login?token=$newToken")

    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

    openBrowser(loginUri)

    new AuthPoller(configFilePath, newToken).run()
  }

  class AuthPoller(configFilePath: Path, token: UUID) {
    val endpointUri = endpointFor(configFilePath)
    val pollUri = endpointUri.resolve(s"/api/verify/callback")

    val body: Json = Map("token" -> token.toString).asJson

    val request = sttp.post(Uri(pollUri))
      .header("content-type", "application/json")
      .header("user-agent", "Needle/2.1.1 (Node.js v8.11.3; linux x64)")
      .body(body.noSpaces)

    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

    def run() : Future[SnykCredentials] = {
      val p = Promise[SnykCredentials]
      queueNext(30, p)
      p.future
    }

    private def queueNext(remainingAttempts: Int, p: Promise[SnykCredentials]): Unit =
      scheduler.scheduleOnce(1.second) {
        poll(remainingAttempts, p)
      }

    private def poll(remainingAttempts: Int, p: Promise[SnykCredentials]): Unit = {
      if(remainingAttempts <= 0) {
        p.failure(new RuntimeException("Auth token expired"))
      } else {
        println(s"Auth poll Sending to $pollUri... ${body.noSpaces}" )
        val response = request.send()
        println(s"auth response was: ${response.code} ${response.unsafeBody}")
        if (response.is200) {
          p complete decode[SnykCredentials](response.unsafeBody).toTry
        } else {
          queueNext(remainingAttempts - 1, p)
        }
      }
    }
  }

}

import SnykCredentials._

case class SnykCredentials(
  api: UUID,
  endpoint: Option[String]
) {
  def endpointOrDefault: String = endpoint getOrElse defaultEndpoint

  private def normalised: SnykCredentials = SnykCredentials(
    this.api,
    this.endpoint.filterNot(_ == defaultEndpoint)
  )

  def writeToFile(path: Path = defaultConfigFile): Unit = {
    val pw = new PrintWriter(path.toFile)
    val printer = Printer.spaces4.copy(dropNullValues = true)
    pw.write(printer.pretty(normalised.asJson))
    pw.close()

  }
}


