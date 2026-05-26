package io.ktor.foodies.webapp.security

import io.ktor.client.*
import io.ktor.foodies.webapp.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.application.*
import io.ktor.server.auth.openid.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

val KeycloakOidcProviderKey = AttributeKey<OidcProvider<OidcPrincipal.IdToken>>("KeycloakOidcProvider")

suspend fun Application.security(
    config: Config.Security,
    httpClient: HttpClient,
    sessionStorage: SessionStorage
): OidcProvider<OidcPrincipal.IdToken> {

//    install(Sessions) {
//        cookie<OidcPrincipal.IdToken>("USER_SESSION", sessionStorage) {
//            cookie.secure = false
//            cookie.httpOnly = true
//            cookie.extensions["SameSite"] = "Lax"
//        }
//    }
//
//    authentication {
//        session<OidcPrincipal.IdToken>(UserSessionAuth) {
//            validate { session -> session }
//            challenge {
//                call.response.headers.append("HX-Redirect", "/login")
//                call.respond(Unauthorized)
//            }
//        }
//    }

    val oidc = openIdConnect {
        this.httpClient = httpClient
    }

    lateinit var keycloak: OidcProvider<OidcPrincipal.IdToken>

    keycloak = oidc.provider(
        name = "keycloak",
        transformPrincipal = transform@{ principal ->
            val user = (principal as? OidcPrincipal.IdToken)?.takeIf {
                it.accessToken != null && it.refreshToken != null
            } ?: return@transform null
            if (!user.shouldRefresh()) {
                return@transform user
            }
            val result = keycloak.refreshToken(user.refreshToken!!)
            result.principal
        }
    ) {
        bearer()
        oauth {
            scopes = listOf("openid", "profile", "email", "offline_access")
            loginUri { path("login") }
            redirectUri { path("oauth", "callback") }
            onSuccess {
                call.respondRedirect("/")
            }
            onFailure {
                call.response.headers.append("HX-Redirect", "/login")
                call.respond(Unauthorized)
            }
        }
        sessions {
            storage = sessionStorage
            logoutUri = { path("logout") }
        }
    }

//    routing {
//        get("/logout") {
//            val session = call.sessions.get<OidcPrincipal.IdToken>() ?: return@get call.respondRedirect("/")
//            call.sessions.clear<OidcPrincipal.IdToken>()
//            val endSessionEndpoint = keycloak.currentMetadata().endSessionEndpoint
//                ?: return@get call.respondRedirect("/")
//            call.respondRedirect(URLBuilder(endSessionEndpoint).apply {
//                parameters.append("id_token_hint", session.idToken)
//                parameters.append("post_logout_redirect_uri", call.requestUrl("/"))
//            }.buildString())
//        }
//    }

//    attributes[RefresherKey] =
//        TokenRefresher(httpClient, keycloak, config.clientId, config.clientSecret)

    attributes.put(KeycloakOidcProviderKey, keycloak)
    return keycloak
}

//fun Route.userSession(
//    provider: OidcProvider<OidcPrincipal.IdToken>,
//    build: context(OidcSessionsContext<OidcPrincipal.IdToken>) Route.() -> Unit
//): Route =
//    authenticateWith(provider.sessions.optional()) {
//        install(createRouteScopedPlugin("SecureUserSession") {
//            route!!.intercept(ApplicationCallPipeline.Call) {
//                val user = call.principal<OidcPrincipal.IdToken>()
//                if (user == null) {
//                    call.response.headers.append("HX-Redirect", "/login")
//                    call.respond(Unauthorized)
//                } else {
//                    user.refresh()
//                }
//            }
//        })
//        with(UserSessionScope {
//            requireNotNull(contextOf<RoutingContext>().call.sessions.get<OidcPrincipal.IdToken>()) {
//                "OidcPrincipal.IdToken not found - route not properly secured with userSession"
//            }
//        }) { build() }
//    }

//@Serializable
//private data class RefreshTokenResponse(
//    @SerialName("access_token")
//    val accessToken: String,
//    @SerialName("id_token")
//    val idToken: String? = null,
//    @SerialName("expires_in")
//    val expiresIn: Long,
//    @SerialName("refresh_token")
//    val refreshToken: String? = null,
//    @SerialName("token_type")
//    val tokenType: String = "Bearer"
//)

//private val RefresherKey = AttributeKey<TokenRefresher>("refresher")
//private val TokenResponseJson = Json { ignoreUnknownKeys = true }
//
//private class TokenRefresher(
//    private val httpClient: HttpClient,
//    private val provider: OidcProvider<OidcPrincipal>,
//    private val clientId: String,
//    private val clientSecret: String
//) {
//    private suspend fun refresh(refreshToken: String): RefreshTokenResponse {
//        logger.debug("Refreshing access token")
//        val response = httpClient.submitForm(
//            url = provider.currentMetadata().tokenEndpoint,
//            formParameters = parameters {
//                append("grant_type", "refresh_token")
//                append("refresh_token", refreshToken)
//                append("client_id", clientId)
//                append("client_secret", clientSecret)
//            }
//        )
//        return TokenResponseJson.decodeFromString<RefreshTokenResponse>(response.bodyAsText())
//    }
//
//    suspend fun refreshSession(session: OidcPrincipal.IdToken): OidcPrincipal.IdToken {
//        logger.debug("Token expiring soon, refreshing...")
//        val refreshToken = requireNotNull(session.refreshToken) {
//            "Refresh token not found in OIDC session"
//        }
//        val response = refresh(refreshToken)
//        val refreshedSession = OidcPrincipal.IdToken(
//            idToken = response.idToken ?: session.idToken,
//            accessToken = response.accessToken,
//            refreshToken = response.refreshToken ?: refreshToken,
//            userInfo = session.userInfo
//        )
//        logger.debug("Token refreshed successfully")
//        return refreshedSession
//    }
//}


//context(pipeline: PipelineContext<Unit, PipelineCall>)
//private suspend fun OidcPrincipal.IdToken.refresh(): Unit {
//    val token = accessToken ?: return pipeline.call.respond(Unauthorized)
//    if (shouldRefresh()) {
//        val session = pipeline.call.application.attributes[RefresherKey].refreshSession(this)
//        pipeline.call.sessions.set(session)
//        withContext(AuthContext(session.accessToken ?: token)) { pipeline.proceed() }
//    } else {
//        withContext(AuthContext(token)) { pipeline.proceed() }
//    }
//}

private fun OidcPrincipal.IdToken.shouldRefresh(bufferSeconds: Long = 60): Boolean {
    if (refreshToken == null) return false
    val expiresAt = accessTokenClaims?.expiresAt ?: idTokenClaims.expiresAt ?: return false
    return Instant.now().plus(bufferSeconds.seconds.toJavaDuration()) >= expiresAt
}
