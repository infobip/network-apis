package com.infobip.napi.network

import com.infobip.napi.model.domain.CellularResult

class ManualCellularController : CellularController {
    override val supportsAutoCellular: Boolean = false
    override suspend fun switchToCellular(): CellularResult = CellularResult.MANUAL_REQUIRED
    override suspend fun resetToDefault(): Boolean = true
}
