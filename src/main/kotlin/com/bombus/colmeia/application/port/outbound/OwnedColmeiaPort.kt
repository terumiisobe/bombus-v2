package com.bombus.colmeia.application.port.outbound

import com.bombus.colmeia.domain.ColmeiaSummary
import java.time.Instant

/**
 * Persistence for owner-scoped hive CRUD.
 * Delete is a hard DELETE of the colmeia row (cascades history/location).
 */
interface OwnedColmeiaPort {

    fun listMeliponarioIdsByOwner(userId: Long): List<Long>

    fun listByOwner(
        userId: Long,
        excludeStatusId: Long?,
        limit: Int,
        offset: Int,
    ): List<ColmeiaSummary>

    fun findByCodeForOwner(
        userId: Long,
        code: Int,
    ): List<ColmeiaSummary>

    fun isCodeTaken(
        meliponarioId: Long,
        code: Int,
        exceptColmeiaId: Long? = null,
    ): Boolean

    fun insert(
        code: Int?,
        speciesId: Long,
        meliponarioId: Long,
        startDate: Instant?,
        initialStatusId: Long,
    ): ColmeiaSummary

    fun appendStatus(colmeiaId: Long, statusId: Long, recordedAt: Instant): ColmeiaSummary?

    fun deleteByIdForOwner(userId: Long, colmeiaId: Long): Boolean
}
