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
    private val reservedJitProduct: String? = null,
) : CardIssuing {
    override suspend fun issueFor(userToken: UserToken): IssuedCardDraft {
        val product = api.listCardProducts().firstOrNull { it.active && it.token != reservedJitProduct }
            ?: throw NoActiveCardProduct()
        return issue(userToken, product.token)
    }

    override suspend fun issueOnProduct(userToken: UserToken, productToken: CardProductToken): IssuedCardDraft =
        issue(userToken, productToken.value)

    private suspend fun issue(userToken: UserToken, productToken: String): IssuedCardDraft {
        val remote = api.createCard(userToken.value, productToken)
        return IssuedCardDraft(
            token = CardToken.of(remote.token),
            last4 = CardLast4.of(remote.lastFour),
            productToken = CardProductToken.of(productToken),
        )
    }
}
