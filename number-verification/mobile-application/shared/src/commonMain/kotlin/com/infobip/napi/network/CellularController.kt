package com.infobip.napi.network

import com.infobip.napi.model.domain.CellularResult

interface CellularController {
    val supportsAutoCellular: Boolean
    suspend fun switchToCellular(): CellularResult
    suspend fun resetToDefault(): Boolean
}
