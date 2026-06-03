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
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinInstant

val KeycloakOidcProviderKey = AttributeKey<OidcProvider<OidcPrincipal.IdToken>>("KeycloakOidcProvider")

suspend fun Application.security(
    config: Config.Security,
    httpClient: HttpClient,
    sessionStorage: SessionStorage
): OidcProvider<OidcPrincipal.IdToken> {
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
        issuer = config.issuer
        accessToken {
            audiences = setOf(config.audience)
        }
        bearer()
        oauth {
            clientId = config.clientId
            clientSecret = config.clientSecret
            config.stateEncryptionKey?.takeIf { it.isNotBlank() }?.let {
                val key = Base64.getDecoder().decode(it)
                stateEncryptionKey = OidcStateEncryptionKey.of(key)
            }
            scopes = listOf("openid", "profile", "email", "offline_access")
            loginUri { path("login") }
            redirectUri { path("oauth", "callback") }
            postLogoutRedirectUri { path("/") }
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
            cookie {
                cookie.secure = false
            }
        }
    }

    attributes.put(KeycloakOidcProviderKey, keycloak)
    return keycloak
}

private fun OidcPrincipal.IdToken.shouldRefresh(buffer: Duration = 60.seconds): Boolean {
    if (refreshToken == null) return false
    val expiresAt = accessTokenClaims?.expiresAt ?: idTokenClaims.expiresAt ?: return false
    return Clock.System.now() + buffer >= expiresAt.toKotlinInstant()
}
