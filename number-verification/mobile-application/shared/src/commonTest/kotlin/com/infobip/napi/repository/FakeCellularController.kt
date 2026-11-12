package com.infobip.napi.repository

import com.infobip.napi.model.domain.CellularResult
import com.infobip.napi.network.CellularController

class FakeCellularController(
    override val supportsAutoCellular: Boolean = true,
    private val switchResult: CellularResult = CellularResult.SUCCESS,
    private val resetResult: Boolean = true,
) : CellularController {

    var switchCount = 0
    var resetCount = 0

    override suspend fun switchToCellular(): CellularResult {
        switchCount++
        return switchResult
    }

    override suspend fun resetToDefault(): Boolean {
        resetCount++
        return resetResult
    }
}
