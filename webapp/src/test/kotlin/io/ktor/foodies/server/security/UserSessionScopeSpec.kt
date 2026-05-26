package io.ktor.foodies.server.security

//@Serializable
//private data class TokenResponse(
//    @SerialName("access_token")
//    val accessToken: String,
//    @SerialName("id_token")
//    val idToken: String,
//    @SerialName("expires_in")
//    val expiresIn: Long,
//    @SerialName("refresh_token")
//    val refreshToken: String
//)
//
//@Serializable
//private data class SessionPayload(
//    val idToken: String,
//    val accessToken: String,
//    val refreshToken: String
//)

//context(ctx: ServiceContext)
//private suspend fun fetchTokens(username: String, password: String): TokenResponse {
//    val tokenEndpoint = "${ctx.keycloakContainer().authServerUrl}/realms/foodies-keycloak/protocol/openid-connect/token"
//    val client = HttpClient(Apache5) { install(ContentNegotiation) { json() } }
//    return client.use {
//        it.submitForm(
//            url = tokenEndpoint,
//            formParameters = parameters {
//                append("grant_type", "password")
//                append("client_id", "foodies")
//                append("client_secret", "foodies_client_secret")
//                append("username", username)
//                append("password", password)
//                append("scope", "openid profile email offline_access")
//            }
//        ).body()
//    }
//}

//private fun SessionPayload.toPrincipal(): OidcPrincipal.IdToken =
//    OidcPrincipal.IdToken(
//        idToken = idToken,
//        accessToken = accessToken,
//        refreshToken = refreshToken,
//        userInfo = OidcPrincipal.UserInfo(subject = "test-user")
//    )

//private fun expiredJwt(): String {
//    val encoder = Base64.getUrlEncoder().withoutPadding()
//    fun part(json: String): String = encoder.encodeToString(json.encodeToByteArray())
//    return "${part("""{"alg":"none","typ":"JWT"}""")}.${part("""{"sub":"expired","exp":0}""")}."
//}

//val userSessionScopeSpec by ctxSuite(context = { serviceContext() }) {
//    testWebAppService("returns 401 and HX-Redirect when no session") {
//        application {
//            val provider = attributes[KeycloakOidcProviderKey]
//            routing {
//                authenticateWith(
//                    provider.sessions,
//                    onUnauthorized = {
//                        call.response.headers.append("HX-Redirect", "/login")
//                        call.respond(HttpStatusCode.Unauthorized)
//                    }
//                ) {
//                    get("/protected") { call.respondText("OK") }
//                }
//            }
//        }
//
//        val response = client.get("/protected")
//
//        assertEquals(HttpStatusCode.Unauthorized, response.status)
//        assertEquals("/login", response.headers["HX-Redirect"])
//    }
//
//    testWebAppService("returns 200 and session data when session exists") {
//        val tokens = fetchTokens("food_lover", "password")
//
//        application {
//            val provider = attributes[KeycloakOidcProviderKey]
//            routing {
//                provider.sessions.installSessionsPlugin(this)
//                post("/test/session") {
//                    val payload = call.receive<SessionPayload>()
//                    call.sessions.set(provider.sessions, payload.toPrincipal())
//                    call.respondText("Session set")
//                }
//                route("/protected") {
//                    authenticateWith(provider.sessions) {
//                        get {
//                            val session = principal
//                            call.respondText("Hello ${session.accessToken}")
//                        }
//                    }
//                }
//            }
//        }
//
//        val client = createClient {
//            install(ContentNegotiation) { json() }
//            install(HttpCookies)
//        }
//
//        val setResp = client.post("/test/session") {
//            contentType(ContentType.Application.Json)
//            setBody(
//                SessionPayload(
//                    idToken = tokens.idToken,
//                    accessToken = tokens.accessToken,
//                    refreshToken = tokens.refreshToken
//                )
//            )
//        }
//        assertEquals(HttpStatusCode.OK, setResp.status)
//
//        val response = client.get("/protected")
//        assertEquals(HttpStatusCode.OK, response.status)
//        assertEquals("Hello ${tokens.accessToken}", response.bodyAsText())
//    }
//
//    testWebAppService("propagates session access token to downstream calls") {
//        val tokens = fetchTokens("food_lover", "password")
//        val downstreamServer = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
//            routing {
//                get("/echo-auth") {
//                    call.respondText(call.request.headers[HttpHeaders.Authorization].orEmpty())
//                }
//            }
//        }.start(wait = false)
//        val downstreamPort = downstreamServer.engine.resolvedConnectors().first().port
//        val downstreamUrl = "http://127.0.0.1:$downstreamPort/echo-auth"
//
//        try {
//            application {
//                val provider = attributes[KeycloakOidcProviderKey]
//                routing {
//                    provider.sessions.installSessionsPlugin(this)
//                    post("/test/session") {
//                        val payload = call.receive<SessionPayload>()
//                        call.sessions.set(provider.sessions, payload.toPrincipal())
//                        call.respondText("Session set")
//                    }
//                    route("/protected") {
//                        authenticateWith(provider.sessions) {
//                            get("/downstream") {
//                                val downstreamClient = HttpClient(Apache5) { install(AuthContextPlugin) }
//                                downstreamClient.use {
//                                    val authHeader = withContext(AuthContext(requireNotNull(principal.accessToken))) {
//                                        it.get(downstreamUrl).bodyAsText()
//                                    }
//                                    call.respondText(authHeader)
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//
//            val client = createClient {
//                install(ContentNegotiation) { json() }
//                install(HttpCookies)
//            }
//
//            val setResp = client.post("/test/session") {
//                contentType(ContentType.Application.Json)
//                setBody(
//                    SessionPayload(
//                        idToken = tokens.idToken,
//                        accessToken = tokens.accessToken,
//                        refreshToken = tokens.refreshToken
//                    )
//                )
//            }
//            assertEquals(HttpStatusCode.OK, setResp.status)
//
//            val response = client.get("/protected/downstream")
//            assertEquals(HttpStatusCode.OK, response.status)
//            assertEquals("Bearer ${tokens.accessToken}", response.bodyAsText())
//        } finally {
//            downstreamServer.stop(gracePeriodMillis = 0, timeoutMillis = 0)
//        }
//    }
//
//    testWebAppService("refreshes tokens when session is near expiry") {
//        val tokens = fetchTokens("food_lover", "password")
//        val expiredAccessToken = expiredJwt()
//
//        application {
//            val provider = attributes[KeycloakOidcProviderKey]
//            routing {
//                provider.sessions.installSessionsPlugin(this)
//                post("/test/session") {
//                    val payload = call.receive<SessionPayload>()
//                    call.sessions.set(provider.sessions, payload.toPrincipal())
//                    call.respondText("Session set")
//                }
//                route("/protected") {
//                    authenticateWith(provider.sessions) {
//                        get {
//                            val session = principal
//                            call.respondText(session.accessToken.orEmpty())
//                        }
//                    }
//                }
//            }
//        }
//
//        val client = createClient {
//            install(ContentNegotiation) { json() }
//            install(HttpCookies)
//        }
//
//        val setResp = client.post("/test/session") {
//            contentType(ContentType.Application.Json)
//            setBody(
//                SessionPayload(
//                    idToken = tokens.idToken,
//                    accessToken = expiredAccessToken,
//                    refreshToken = tokens.refreshToken
//                )
//            )
//        }
//        assertEquals(HttpStatusCode.OK, setResp.status)
//
//        val response = client.get("/protected")
//        assertEquals(HttpStatusCode.OK, response.status)
//        assertNotEquals(expiredAccessToken, response.bodyAsText())
//    }
//}
