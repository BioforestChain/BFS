package info.bagen.dwebbrowser.microService.helper

import org.http4k.core.Status

fun Status.Companion.fromCode(code: Int) =
    when (code) {
        100 -> CONTINUE
        101 -> SWITCHING_PROTOCOLS
        200 -> OK
        201 -> CREATED
        202 -> ACCEPTED
        203 -> NON_AUTHORITATIVE_INFORMATION
        204 -> NO_CONTENT
        205 -> RESET_CONTENT
        206 -> PARTIAL_CONTENT
        300 -> MULTIPLE_CHOICES
        301 -> MOVED_PERMANENTLY
        302 -> FOUND
        303 -> SEE_OTHER
        304 -> NOT_MODIFIED
        305 -> USE_PROXY
        307 -> TEMPORARY_REDIRECT
        308 -> PERMANENT_REDIRECT
        400 -> BAD_REQUEST
        401 -> UNAUTHORIZED
        402 -> PAYMENT_REQUIRED
        403 -> FORBIDDEN
        404 -> NOT_FOUND
        405 -> METHOD_NOT_ALLOWED
        406 -> NOT_ACCEPTABLE
        407 -> PROXY_AUTHENTICATION_REQUIRED
        408 -> REQUEST_TIMEOUT
        409 -> CONFLICT
        410 -> GONE
        411 -> LENGTH_REQUIRED
        412 -> PRECONDITION_FAILED
        413 -> REQUEST_ENTITY_TOO_LARGE
        414 -> REQUEST_URI_TOO_LONG
        415 -> UNSUPPORTED_MEDIA_TYPE
        416 -> REQUESTED_RANGE_NOT_SATISFIABLE
        417 -> EXPECTATION_FAILED
        418 -> I_M_A_TEAPOT
        422 -> UNPROCESSABLE_ENTITY
        426 -> UPGRADE_REQUIRED
        429 -> TOO_MANY_REQUESTS
        500 -> INTERNAL_SERVER_ERROR
        501 -> NOT_IMPLEMENTED
        502 -> BAD_GATEWAY
        503 -> SERVICE_UNAVAILABLE
        503 -> CONNECTION_REFUSED
        503 -> UNKNOWN_HOST
        504 -> GATEWAY_TIMEOUT
        504 -> CLIENT_TIMEOUT
        505 -> HTTP_VERSION_NOT_SUPPORTED
        else -> Status(code, null)
    }
