package app.vereda.bff.jit.domain

import java.math.BigDecimal

interface AccountLedger {
    fun balances(userToken: String): LedgerState

    fun open(userToken: String, amount: BigDecimal): LedgerState
}
