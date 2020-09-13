package docspell.restserver.auth

import docspell.backend.auth._
import docspell.common.AccountId
import docspell.restserver.Config

import org.http4s._
import org.http4s.util._

case class CookieData(auth: AuthToken) {
  def accountId: AccountId = auth.account
  def asString: String     = auth.asString

  def asCookie(cfg: Config, host: Option[String]): ResponseCookie = {
    val domain = CookieData.getDomain(cfg, host)
    val sec    = cfg.baseUrl.scheme.exists(_.endsWith("s"))
    val path   = cfg.baseUrl.path / "api" / "v1" / "sec"
    ResponseCookie(
      CookieData.cookieName,
      asString,
      domain = domain,
      path = Some(path.asString),
      httpOnly = true,
      secure = sec
    )
  }
}
object CookieData {
  val cookieName = "docspell_auth"
  val headerName = "X-Docspell-Auth"

  private def getDomain(cfg: Config, remote: Option[String]): Option[String] =
    if (cfg.baseUrl.isLocal) remote.orElse(cfg.baseUrl.host)
    else cfg.baseUrl.host

  def authenticator[F[_]](r: Request[F]): Either[String, String] =
    fromCookie(r).orElse(fromHeader(r))

  def fromCookie[F[_]](req: Request[F]): Either[String, String] =
    for {
      header <- headers.Cookie.from(req.headers).toRight("Cookie parsing error")
      cookie <-
        header.values.toList
          .find(_.name == cookieName)
          .toRight("Couldn't find the authcookie")
    } yield cookie.content

  def fromHeader[F[_]](req: Request[F]): Either[String, String] =
    req.headers
      .get(CaseInsensitiveString(headerName))
      .map(_.value)
      .toRight("Couldn't find an authenticator")

  def deleteCookie(cfg: Config, remoteHost: Option[String]): ResponseCookie =
    ResponseCookie(
      cookieName,
      "",
      domain = getDomain(cfg, remoteHost),
      path = Some(cfg.baseUrl.path / "api" / "v1" / "sec").map(_.asString),
      httpOnly = true,
      secure = cfg.baseUrl.scheme.exists(_.endsWith("s")),
      maxAge = Some(-1)
    )

}
