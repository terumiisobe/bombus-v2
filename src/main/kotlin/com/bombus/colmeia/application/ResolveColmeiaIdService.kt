package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.ResolveColmeiaIdUseCase
import com.bombus.colmeia.application.port.inbound.ResolveColmeiaQuery
import com.bombus.colmeia.application.port.inbound.ResolvedColmeia
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaSummary
import org.springframework.stereotype.Service

@Service
class ResolveColmeiaIdService(
    private val ownedColmeiaPort: OwnedColmeiaPort,
    private val properties: ColmeiaCountProperties,
) : ResolveColmeiaIdUseCase {

    override fun resolve(query: ResolveColmeiaQuery): ResolvedColmeia {
        val matches = ownedColmeiaPort.findByCodeForOwner(query.userId, query.code)
        if (matches.isEmpty()) throw ColmeiaCommandError.ColmeiaNotFound()
        val chosen = if (query.statusId != null) matchStatus(matches, query.statusId) else preferActive(matches)
        return ResolvedColmeia(
            colmeiaId = chosen.id,
            inactive = properties.releasesCode(chosen.statusName),
            statusName = chosen.statusName ?: "sem status",
        )
    }

    private fun matchStatus(matches: List<ColmeiaSummary>, statusId: Long): ColmeiaSummary {
        val targeted = matches.filter { it.statusId == statusId }
        return when (targeted.size) {
            1 -> targeted.first()
            0 -> throw ColmeiaCommandError.ColmeiaNotFound()
            else -> throw ColmeiaCommandError.AmbiguousCode()
        }
    }

    private fun preferActive(matches: List<ColmeiaSummary>): ColmeiaSummary {
        val active = matches.filterNot { properties.releasesCode(it.statusName) }
        return when {
            active.size == 1 -> active.first()
            active.size > 1 -> throw ColmeiaCommandError.AmbiguousCode()
            matches.size == 1 -> matches.first()
            else -> throw ColmeiaCommandError.AmbiguousDisposition(
                matches.map { it.statusName ?: "sem status" }.distinct().sorted(),
            )
        }
    }
}
