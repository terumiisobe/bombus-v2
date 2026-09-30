package com.bombus.colmeia.application.port.outbound

import com.bombus.colmeia.domain.ColmeiaSummary
import java.time.Instant

/**
 * Persistence for owner-scoped hive CRUD.
 * Soft-delete is append-status only; [excludeStatusId] (typically perdida) frees codes.
 */
interface OwnedColmeiaPort {

    fun listMeliponarioIdsByOwner(userId: Long): List<Long>

    fun listByOwner(
        userId: Long,
        excludeStatusId: Long?,
        limit: Int,
        offset: Int,
    ): List<ColmeiaSummary>

    fun findByIdForOwner(userId: Long, colmeiaId: Long): ColmeiaSummary?

    fun findByCodeForOwner(
        userId: Long,
        code: Int,
        meliponarioId: Long?,
    ): List<ColmeiaSummary>

    fun nextFreeCode(meliponarioId: Long, excludeStatusId: Long?): Int

    fun isCodeTaken(
        meliponarioId: Long,
        code: Int,
        excludeStatusId: Long?,
        exceptColmeiaId: Long? = null,
    ): Boolean

    fun insert(
        code: Int,
        speciesId: Long,
        meliponarioId: Long,
        startDate: Instant?,
        initialStatusId: Long,
    ): ColmeiaSummary

    fun update(
        colmeiaId: Long,
        speciesId: Long?,
        startDate: Instant?,
    ): ColmeiaSummary?

    fun appendStatus(colmeiaId: Long, statusId: Long, recordedAt: Instant): ColmeiaSummary?
}
