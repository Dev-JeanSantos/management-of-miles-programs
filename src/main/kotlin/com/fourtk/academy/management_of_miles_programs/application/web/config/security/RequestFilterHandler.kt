package com.fourtk.academy.management_of_miles_programs.application.web.config.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.fourtk.academy.management_of_miles_programs.domain.commons.exceptions.UnauthorizationException
import com.fourtk.academy.management_of_miles_programs.domain.commons.tags.CORRELATION_HEADER_NAME
import de.huxhorn.sulky.ulid.ULID

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.apache.http.entity.ContentType

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.OffsetDateTime
import java.time.ZoneOffset
const val TIMESTAMP = "timestamp"
const val STATUS = "status"
const val ERROR = "error"
const val MESSAGE = "message"
const val PATH = "path"

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestFilterHandler(val mapper: ObjectMapper) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val correlationId = retrieveCorrelation(request)

        handleAndFilterServlerRequest(request, response, filterChain, correlationId)
    }

    private fun retrieveCorrelation(request: HttpServletRequest) =
        request.getHeader(CORRELATION_HEADER_NAME) ?: ULID().nextULID()

    private fun handleAndFilterServlerRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filter: FilterChain,
        correlationId: String
    ) {
        try {
            val requestWrapper =
                MutableHttpServletRequestWrapper(request)
            requestWrapper.addHeader(CORRELATION_HEADER_NAME, correlationId)
            response.setHeader(CORRELATION_HEADER_NAME, correlationId)
            response.contentType = ContentType.APPLICATION_JSON.toString()

            filter.doFilter(requestWrapper, response)
        } catch (ex: UnauthorizationException) {
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.writer.use {
                mapper.writeValue(it, buildObject(request, response, ex))
            }
        }
    }

    private fun buildObject(
        req: HttpServletRequest,
        res: HttpServletResponse,
        ex: Exception
    ) = mapOf(
        TIMESTAMP to OffsetDateTime.now(ZoneOffset.UTC),
        STATUS to res.status,
        ERROR to HttpStatus.valueOf(res.status),
        MESSAGE to ex.message,
        PATH to req.requestURI
    )

    class MutableHttpServletRequestWrapper(request: HttpServletRequest) : HttpServletRequestWrapper(request) {

        private val headerMap = HashMap<String, String>()

        fun addHeader(
            name: String,
            value: String
        ) {
            if (super.getHeader(name).isNullOrBlank()) {
                headerMap[name] = value
            }
        }
    }
}