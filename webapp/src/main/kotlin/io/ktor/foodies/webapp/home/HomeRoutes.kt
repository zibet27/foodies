package io.ktor.foodies.webapp.home

import io.ktor.foodies.webapp.basket.*
import io.ktor.foodies.webapp.menu.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.openid.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.html.*

fun Route.homeRoutes(provider: OidcProvider<OidcPrincipal.IdToken>) {
    staticResources("/static", "static")
    home(provider)
}

@OptIn(ExperimentalKtorApi::class)
fun Route.home(provider: OidcProvider<OidcPrincipal.IdToken>) = authenticateWith(provider.bearer.optional()) {
    get("/") {
        val userOrNull = call.principal<OidcPrincipal.IdToken>()
        val isLoggedIn = userOrNull != null

        call.respondHtml(HttpStatusCode.OK) {
            lang = "en"

            head {
                meta { charset = "utf-8" }
                meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
                title { +"Foodies - Discover the menu" }
                link(rel = "stylesheet", href = "/static/home.css")
                script(src = "https://unpkg.com/htmx.org@1.9.12") {}
                script(src = "https://unpkg.com/htmx-ext-intersect@2.0.0/intersect.js") {}
            }

            body {
                attributes["hx-ext"] = "intersect"
                header {
                    a(href = "/", classes = "logo") { +"Foodies" }
                    div(classes = "actions") {
                        basketBadgeLink()
                        if (isLoggedIn) {
                            a(href = "/logout", classes = "button secondary") { +"Log out" }
                        } else {
                            a(href = "/login", classes = "button primary") { +"Log in" }
                        }
                    }
                }

                main {
                    section(classes = "hero") {
                        h1 { +"Your favorite dishes, one click away." }


                        div(classes = "menu-grid") {
                            id = "menu-feed"

                            div(classes = "sentinel") {
                                id = "feed-sentinel"
                                attributes["hx-get"] = "/menu?offset=0&limit=${DefaultMenuPageSize}"
                                attributes["hx-trigger"] =
                                    MenuIntersectTrigger
                                attributes["hx-swap"] = "outerHTML"
                                attributes["hx-indicator"] = "#feed-spinner"
                                span { +"Loading menu..." }
                            }
                        }

                        div(classes = "feed-status") {
                            span {
                                id = "feed-status"
                                attributes["role"] = "status"
                                attributes["aria-live"] = "polite"
                            }
                            div(classes = "spinner htmx-indicator") { id = "feed-spinner" }
                        }
                    }
                }

                div(classes = "toast-container") {
                    div { id = "toast" }
                }
            }
        }
    }
}
