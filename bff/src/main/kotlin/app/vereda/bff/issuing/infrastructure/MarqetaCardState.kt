package app.vereda.bff.issuing.infrastructure

import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.issuing.domain.CardState
import app.vereda.bff.issuing.domain.CardStateControl
import app.vereda.bff.issuing.domain.CardToken

class MarqetaCardState(
    private val api: MarqetaCoreApi,
) : CardStateControl {
    override suspend fun transition(cardToken: CardToken, state: CardState): CardState {
        val applied = api.transitionCard(cardToken.value, state.name)
        return CardState.parse(applied)
    }
}