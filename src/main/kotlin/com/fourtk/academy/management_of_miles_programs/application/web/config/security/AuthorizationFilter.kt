package com.fourtk.academy.management_of_miles_programs.application.web.config.security

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fourtk.academy.management_of_miles_programs.application.web.config.parser.JsonParserBuilder
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class AuthorizationFilter(
    private val serviceKey: String
) : OncePerRequestFilter() {
    private val ignoredPaths: List<AntPathRequestMatcher> = IGNORE_AUTH_PATH_LIST.map { AntPathRequestMatcher(it) }


    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (verifyIgnoreAuthorization(request)) {
            filterChain.doFilter(request, response)
            return
        }

        request.getHeader(HttpHeaders.AUTHORIZATION)?.let {
            when (it) {
                this.serviceKey -> SecurityContextHolder.getContext().authentication = this.systemAuthorization()
            }
        }

        if (SecurityContextHolder.getContext().authentication == null) {
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.writer.use {
                JsonParserBuilder.default()
                    .registerModule(JavaTimeModule())
                    .writeValue(it, buildObject(request, response))
                it.flush()
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun buildObject(
        req: HttpServletRequest,
        res: HttpServletResponse,
    ) = mapOf(
        TIMESTAMP to OffsetDateTime.now(ZoneOffset.UTC),
        STATUS to res.status,
        ERROR to HttpStatus.valueOf(res.status),
        PATH to req.requestURI
    )

    private fun verifyIgnoreAuthorization(request: HttpServletRequest) = ignoredPaths.any { it.matches(request) }

    private fun systemAuthorization(): PreAuthenticatedAuthenticationToken {
        return PreAuthenticatedAuthenticationToken(
            this.serviceKey,
            null, Collections.singletonList(GrantedAuthority { ROLE_SYSTEM })
        )
    }

    fun ignoreAuthorizationPathList() = IGNORE_AUTH_PATH_LIST

    companion object {
        const val ROLE_SYSTEM = "ROLE_SYSTEM"
        private val IGNORE_AUTH_PATH_LIST =
            arrayOf(
                "/health-check",
                "/health-check/**",
                "/healthcheck",
                "/healthcheck/**",
                "/metrics",
                "/metrics/**",
                "/actuator",
                "/actuator/**",
                "/error",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/v3/api-docs.yaml",
                "/webjars/**",
                "/swagger-resources",
                "/swagger-resources/**",
                "/docs",
                "/info",
                "/cache",
                "/favicon.ico",
                "/swagger.yaml",
                "/csrf"
            )
    }
}