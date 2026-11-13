package app.vereda.bff.issuing.infrastructure

import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.issuing.domain.CardIssuing
import app.vereda.bff.issuing.domain.CardLast4
import app.vereda.bff.issuing.domain.CardProductToken
import app.vereda.bff.issuing.domain.CardToken
import app.vereda.bff.issuing.domain.IssuedCardDraft
import app.vereda.bff.issuing.domain.NoActiveCardProduct
import app.vereda.bff.onboarding.domain.UserToken

class MarqetaCardIssuing(
    private val api: MarqetaCoreApi,
) : CardIssuing {
    override suspend fun issueFor(userToken: UserToken): IssuedCardDraft {
        val product = api.listCardProducts().firstOrNull { it.active } ?: throw NoActiveCardProduct()
        val remote = api.createCard(userToken.value, product.token)
        return IssuedCardDraft(
            token = CardToken.of(remote.token),
            last4 = CardLast4.of(remote.lastFour),
            productToken = CardProductToken.of(product.token),
        )
    }
}
