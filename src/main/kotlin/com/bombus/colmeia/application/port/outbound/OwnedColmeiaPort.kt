package com.bombus.colmeia.application.port.outbound

import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry
import com.bombus.colmeia.domain.ColmeiaSummary
import java.time.Instant

/**
 * Hive CRUD scoped to the meliponários a usuário is a member of.
 *
 * "Owner" in a method name means "member of the colmeia's meliponário".
 * meliponario.owner_id grants nothing.
 *
 * Methods taking `userId` enforce membership in their own SQL.
 * [isCodeTaken], [insert] and [appendStatus] trust their caller to have resolved the
 * meliponário or colmeia id through a `userId`-scoped method in the same use-case call.
 *
 * Delete is a hard DELETE of the colmeia row (cascades history/location).
 * Status changes append history via [appendStatus] (visit-oriented fields optional).
 * [listStatusHistory] re-checks membership for [colmeiaId].
 */
interface OwnedColmeiaPort {

    /** Ascending by id. The first element is the default target for create. */
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
     * Latest [limit] historico rows for [colmeiaId], newest first
     * (`recorded_at` DESC, `id` DESC). Empty if the hive is not accessible to [userId].
     */
    fun listStatusHistory(
        userId: Long,
        colmeiaId: Long,
        limit: Int,
    ): List<ColmeiaStatusHistoryEntry>

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

    fun appendStatus(append: AppendColmeiaStatus): ColmeiaSummary?

    fun deleteByIdForOwner(userId: Long, colmeiaId: Long): Boolean
}
