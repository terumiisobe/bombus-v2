package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.RecordColmeiaLocationCommand
import com.bombus.colmeia.application.port.inbound.RecordColmeiaLocationUseCase
import com.bombus.colmeia.application.port.outbound.AppendLocalizacao
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.application.port.outbound.SaveLocalizacaoPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.LocalizacaoReading
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock

@Service
class RecordColmeiaLocationService(
    private val ownedColmeiaPort: OwnedColmeiaPort,
    private val saveLocalizacaoPort: SaveLocalizacaoPort,
    private val clock: Clock,
) : RecordColmeiaLocationUseCase {

    @Transactional
    override fun record(command: RecordColmeiaLocationCommand): LocalizacaoReading {
        validateCoordinates(command.latitude, command.longitude)
        ownedColmeiaPort.findByIdForOwner(command.userId, command.colmeiaId)
            ?: throw ColmeiaCommandError.ColmeiaNotFound()

        return saveLocalizacaoPort.append(
            AppendLocalizacao(
                userId = command.userId,
                colmeiaId = command.colmeiaId,
                latitude = command.latitude,
                longitude = command.longitude,
                accuracyMeters = command.accuracyMeters,
                recordedAt = clock.instant(),
            ),
        ) ?: throw ColmeiaCommandError.ColmeiaNotFound()
    }

    private fun validateCoordinates(latitude: BigDecimal, longitude: BigDecimal) {
        require(latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE) {
            "latitude must be between $MIN_LATITUDE and $MAX_LATITUDE"
        }
        require(longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE) {
            "longitude must be between $MIN_LONGITUDE and $MAX_LONGITUDE"
        }
    }

    private companion object {
        val MIN_LATITUDE: BigDecimal = BigDecimal("-90")
        val MAX_LATITUDE: BigDecimal = BigDecimal("90")
        val MIN_LONGITUDE: BigDecimal = BigDecimal("-180")
        val MAX_LONGITUDE: BigDecimal = BigDecimal("180")
    }
}
