package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.ReleaseColmeiaCodeCommand
import com.bombus.colmeia.application.port.inbound.ReleaseColmeiaCodeUseCase
import com.bombus.colmeia.application.port.outbound.AppendLocalizacaoClear
import com.bombus.colmeia.application.port.outbound.LoadLocalizacaoPort
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.application.port.outbound.SaveLocalizacaoPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.LocalizacaoReading
import com.bombus.colmeia.domain.presenceFromLatest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Visit 2 clear: only for disposition + live pin. Never-pinned / already-cleared
 * disposed hives are already free — [ColmeiaCommandError.NoLivePinToClear].
 */
@Service
class ReleaseColmeiaCodeService(
    private val ownedColmeiaPort: OwnedColmeiaPort,
    private val loadLocalizacaoPort: LoadLocalizacaoPort,
    private val saveLocalizacaoPort: SaveLocalizacaoPort,
    private val properties: ColmeiaCountProperties,
    private val clock: Clock,
) : ReleaseColmeiaCodeUseCase {

    @Transactional
    override fun release(command: ReleaseColmeiaCodeCommand): LocalizacaoReading {
        val existing = ownedColmeiaPort.findByIdForOwner(command.userId, command.colmeiaId)
            ?: throw ColmeiaCommandError.ColmeiaNotFound()

        if (!properties.isDispositionStatus(existing.statusName)) {
            throw ColmeiaCommandError.NotDispositionStatus()
        }

        val presence = presenceFromLatest(
            loadLocalizacaoPort.findCurrent(command.userId, command.colmeiaId),
        )
        if (!presence.hasLivePin) {
            throw ColmeiaCommandError.NoLivePinToClear()
        }

        return saveLocalizacaoPort.appendClear(
            AppendLocalizacaoClear(
                userId = command.userId,
                colmeiaId = command.colmeiaId,
                recordedAt = clock.instant(),
            ),
        ) ?: throw ColmeiaCommandError.ColmeiaNotFound()
    }
}
