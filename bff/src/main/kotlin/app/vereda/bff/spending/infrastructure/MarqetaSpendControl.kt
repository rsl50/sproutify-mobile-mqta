package app.vereda.bff.spending.infrastructure

import app.vereda.bff.infrastructure.marqeta.MarqetaCallFailed
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.spending.domain.SpendControl
import app.vereda.bff.spending.domain.SpendLimit

class MarqetaSpendControl(
    private val api: MarqetaCoreApi,
) : SpendControl {
    override suspend fun save(userToken: UserToken, limit: SpendLimit) {
        try {
            write(userToken, limit, update = false)
        } catch (error: MarqetaCallFailed) {
            if (error.statusCode == 409) write(userToken, limit, update = true) else throw error
        }
    }

    private suspend fun write(userToken: UserToken, limit: SpendLimit, update: Boolean) {
        api.saveVelocityControl(
            token = limit.controlToken,
            userToken = userToken.value,
            amount = limit.amount.amount.toPlainString(),
            usageLimit = limit.usageLimit,
            window = limit.window.name,
            update = update,
        )
    }
}