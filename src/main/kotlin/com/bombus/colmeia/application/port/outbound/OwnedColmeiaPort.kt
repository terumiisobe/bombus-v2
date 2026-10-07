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
        excludeStatusIds: Collection<Long>,
        limit: Int,
        offset: Int,
    ): List<ColmeiaSummary>

    fun findByCodeForOwner(
        userId: Long,
        code: Int,
    ): List<ColmeiaSummary>

    /**
     * Whether [code] is held by another hive in [meliponarioId].
     * Hives whose current status is in [ignoreStatusIds] do not block reuse
     * (code-releasing statuses keep their code on the row).
     */
    fun isCodeTaken(
        meliponarioId: Long,
        code: Int,
        exceptColmeiaId: Long? = null,
        ignoreStatusIds: Collection<Long> = emptyList(),
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
