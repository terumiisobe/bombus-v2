package com.bombus.colmeia.adapter.inbound.web

import com.bombus.colmeia.application.port.inbound.GetMeliponarioMapQuery
import com.bombus.colmeia.application.port.inbound.GetMeliponarioMapUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Map-UI farm snapshot. Identity is interim: [X-User-Id] must be the caller's `usuario.id`.
 * Authorization is membership SQL (`meliponario_membro`) inside the load port — non-members get 404.
 */
@RestController
@RequestMapping("/v1/meliponarios")
@Tag(name = "Meliponários", description = "Farm data for the frontend map")
class MeliponarioMapController(
    private val getMeliponarioMap: GetMeliponarioMapUseCase,
) {

    @GetMapping("/{meliponarioId}")
    @Operation(
        summary = "Get meliponário map snapshot",
        description = "Returns farm name, aggregate updatedAt, and hives with pin/status/inspection fields. " +
            "Active hives without a GPS pin are included with null lat/lng. " +
            "Disposed (sold/lost) hives are included only while they still have a live pin; " +
            "cleared or never-pinned disposed hives are omitted from hives[]. " +
            "Auth: interim X-User-Id header (usuario.id); access gated by meliponario_membro.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Farm snapshot",
        content = [Content(schema = Schema(implementation = MeliponarioMapResponse::class))],
    )
    @ApiResponse(responseCode = "401", description = "Missing or invalid X-User-Id")
    @ApiResponse(responseCode = "404", description = "Unknown yard or caller is not a member")
    fun getMap(
        @PathVariable meliponarioId: Long,
        @Parameter(
            name = USER_ID_HEADER,
            `in` = ParameterIn.HEADER,
            required = true,
            description = "Interim caller identity: usuario.id (membership checked in SQL). Replace with JWT later.",
        )
        @RequestHeader(name = USER_ID_HEADER, required = false) userIdHeader: String?,
    ): MeliponarioMapResponse {
        val userId = parseUserId(userIdHeader)
        return getMeliponarioMap
            .get(GetMeliponarioMapQuery(userId = userId, meliponarioId = meliponarioId))
            .toResponse()
    }

    private fun parseUserId(raw: String?): Long {
        if (raw.isNullOrBlank()) {
            throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Missing $USER_ID_HEADER header",
            )
        }
        return raw.toLongOrNull()
            ?: throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid $USER_ID_HEADER header",
            )
    }

    companion object {
        const val USER_ID_HEADER = "X-User-Id"
    }
}
