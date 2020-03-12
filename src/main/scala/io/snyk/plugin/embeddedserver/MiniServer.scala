package io.snyk.plugin.embeddedserver

import java.io.{File, IOException}
import java.net.{URI, URL, URLEncoder}

import fi.iki.elonen.NanoHTTPD
import io.snyk.plugin.datamodel.SnykVulnResponse
import ColorProvider.RichColor
import HandlebarsEngine.RichTemplate
import io.snyk.plugin.IntellijLogging
import io.snyk.plugin.ui.state.SnykPluginState

import scala.util.{Failure, Success}

/**
  * A low-impact embedded HTTP server, built on top of the NanoHTTPD engine.
  * Minimal routing to process `.hbs` files via handlebars.
  *
  * Special logic to show an interim animation if the requested URL needs a new scan,
  * then asynchronously show the requested URL once the scan is complete
  */
class MiniServer(
    protected val pluginState: SnykPluginState,
    colorProvider: ColorProvider,
    port0: Int = 0
  ) extends NanoHTTPD(port0)
  with ServerFilters
  with ServerResponses
  with IntellijLogging
{ // 0 == first available port

  import NanoHTTPD._
  import pluginState.apiClient

  start(SOCKET_READ_TIMEOUT, false)
  val port: Int = this.getListeningPort

  val rootUrl = new URL(s"http://localhost:$port")
  log.info(s"Mini-server on $rootUrl \n")

  val handlebarsEngine = new HandlebarsEngine

  protected def navigateTo(path: String, params: ParamSet): Unit =
    pluginState.navigator().navigateTo(path, params)

  /** The default URL to show when async scanning if an explicit `interstitial` page hasn't been requested **/
  val defaultScanning = "/html/scanning.hbs"

  val webInf = WebInf.instance

  /**
    * Core NanoHTTPD serving method; Parse params for our own needs, extract the URI, and delegate to our own `serve`
    */
  override def serve(session: IHTTPSession): Response = {
    log.debug(s"miniserver serving $session")
    val uri = URI.create(session.getUri).normalize()
    val params = ParamSet.from(session)
    val uriPath = uri.getPath
    log.debug(s"miniserver routing $uriPath")
    try {
      router.route(uri, params) getOrElse {
        //test hbs files in /html before simply 404-ing
        val possiblePath = s"/html${uriPath}.hbs"
        if(webInf.exists(possiblePath)) serveHandlebars(possiblePath, params)
        else notFoundResponse(uriPath)
      }
    } catch { case ex: Exception =>
      log.warn(ex)
      newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", ex.toString)
    }
  }

  private lazy val router = HttpRouter(
    "/assets/*"              -> serveStatic,
    "/partials/*"            -> serveHandlebars,
    "/perform-login"         -> performLogin,
    "/vulnerabilities"       -> serveVulns,
    "/rawScanResults.json"   -> serveRawScanResults,
    "/flatVulns.json"        -> serveFlatJsonVulns,
    "/vulns.json"            -> serveJsonVulns,
    "/debugForceNav"         -> debugForceNav,
  )
  //note: anything in /WEB-INF/html will typically be handled by the fallback in serve()


  def serveStatic(path: String, params: ParamSet): Response = {
    val mime = MimeType of path
    log.debug(s"miniserver serving static http://localhost:$port$path as $mime")
    val conn = WebInf.instance.openConnection(path)
    newFixedLengthResponse(Response.Status.OK, mime, conn.getInputStream, conn.getContentLengthLong)
  }

  def debugForceNav(path: String, params: ParamSet): Response = params.first("path") match {
    case Some(forcedPath) =>
      navigateTo(forcedPath, params)
      newFixedLengthResponse(Response.Status.OK, "text/plain", forcedPath)
    case None =>
      newFixedLengthResponse(Response.Status.OK, "text/plain", "debugForceNav: no path param supplied")
  }

  def performLogin(path: String, params: ParamSet): Response = {
    log.debug("Performing login")
    val redir = asyncAuthAndRedirectTo("/vulnerabilities", params)
    redirectTo("/logging-in")
  }

  def serveVulns(path: String, params: ParamSet): Response = {
    requireAuth {
      requireProjectId {
        requireScan(path, params) {
          serveHandlebars("/html/vulns.hbs", params)
        }
      }
    }
  }

  private[this] def safeScanResult: Seq[SnykVulnResponse] =
    pluginState.latestScanForSelectedProject getOrElse Seq.empty

  def serveRawScanResults(path: String, params: ParamSet): Response = {
    requireAuth {
      requireProjectId {
        requireScan(path, params) {
          import SnykVulnResponse.JsonCodecs._
          jsonResponse(safeScanResult)
        }
      }
    }
  }

  def serveFlatJsonVulns(path: String, params: ParamSet): Response = {
    requireAuth {
      requireProjectId {
        requireScan(path, params) {
          jsonResponse(safeScanResult.flatMap(vulnResponse => vulnResponse.flatMiniVulns))
        }
      }
    }
  }
  def serveJsonVulns(path: String, params: ParamSet): Response = {
    requireAuth {
      requireProjectId {
        requireScan(path, params) {
          jsonResponse(safeScanResult.flatMap(vulnResponse => vulnResponse.mergedMiniVulns).sortBy(_.spec))
        }
      }
    }
  }

  def serveHandlebars(path: String, params: ParamSet): Response = {
    log.debug(s"miniserver serving handlebars template http://localhost:$port$path")
    log.debug(s"params = $params")

    handlebarsEngine.compile(path) match {
      case Success(template) =>
        def latestScanResult = safeScanResult

        val selectedProjectId = pluginState.selectedProjectId.get
        val snykVulnResponseSeq = latestScanResult

        // It's main module if displayTargetFile contains only 'pom.xml'. Sub modules contains sub folders 'folder/pom.xml'.
        val isMainModule = latestScanResult.count(snykVulnResponse =>
          selectedProjectId.contains(snykVulnResponse.projectName.get)
            && snykVulnResponse.displayTargetFile.get.split(File.separator).length == 1) == 1

        // Check is main module. If main module it will use all vulnerabilities.
        // If not main module it will filter vulnerabilities by sub project.
        val filteredByProjectVulnerabilities = if (isMainModule) {
          snykVulnResponseSeq
        } else {
          latestScanResult.filter(snykVulnResponse => selectedProjectId.contains(snykVulnResponse.projectName.get))
        }

        val ctx = Map.newBuilder[String, Any]

        ctx ++= params.contextMap
        ctx ++= colorProvider.toMap.mapValues (_.hexRepr)
        ctx += "currentProject" -> selectedProjectId
        ctx += "projectIds" -> pluginState.rootProjectIds
        ctx += "miniVulns" -> filteredByProjectVulnerabilities.flatMap(vulnResponse => vulnResponse.mergedMiniVulns).sortBy (_.spec)
        ctx += "vulnerabilities" -> filteredByProjectVulnerabilities.flatMap(vulnResponse => vulnResponse.vulnerabilities)

        //TODO: should these just be added to state?
        val paramFlags = params.all ("flags").map (_.toLowerCase -> true).toMap
        ctx += "flags" -> (paramFlags ++ pluginState.flags.asStringMap)
        ctx += "localhost" -> s"http://localhost:$port"
        ctx += "apiAvailable" -> apiClient.isAvailable

        val body = template render ctx.result()
        newFixedLengthResponse (Response.Status.OK, "text/html", body)
      case Failure(_: IOException) => notFoundResponse(path)
      case Failure(x) =>
        redirectTo("/error?errmsg=" + URLEncoder.encode(x.getMessage, "UTF-8"))
    }
  }
}
