package io.ktor.foodies.server.auth

import io.ktor.server.auth.openid.OidcPrincipal

data class UserPrincipal(
    val userId: String,
    val email: String?,
    val roles: Set<String>,
    val accessToken: String,
    val name: String? = null,
    val oidcPrincipal: OidcPrincipal.AccessToken? = null,
) {
    constructor(principal: OidcPrincipal.AccessToken) : this(
        userId = requireNotNull(principal.userInfo?.subject) { "subject claim is missing" },
        email = principal.userInfo?.email,
        roles = principal.realmRoles(),
        accessToken = principal.accessToken,
        name = principal.userInfo?.name ?: principal.userInfo?.preferredUsername,
        oidcPrincipal = principal,
    )
}

data class ServicePrincipal(
    val serviceAccountId: String,
    val clientId: String,
    val roles: Set<String>,
    val userContext: UserPrincipal? = null
)

private fun OidcPrincipal.AccessToken.realmRoles(): Set<String> {
    val roles = accessTokenClaims.claim("realm_access").asMap()["roles"] as? List<*>
    return roles?.filterIsInstance<String>()?.toSet() ?: emptySet()
}
