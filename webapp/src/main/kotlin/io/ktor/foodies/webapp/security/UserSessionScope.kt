package io.ktor.foodies.webapp.security

import io.ktor.server.auth.openid.OidcPrincipal
import io.ktor.server.routing.RoutingContext

//fun interface UserSessionScope {
//    context(ctx: RoutingContext)
//    suspend fun userSession(): OidcPrincipal.IdToken
//}
//
//context(scope: UserSessionScope)
//suspend fun RoutingContext.userSession(): OidcPrincipal.IdToken = scope.userSession()
