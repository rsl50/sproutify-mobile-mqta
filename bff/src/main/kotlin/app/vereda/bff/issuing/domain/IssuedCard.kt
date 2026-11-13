package app.vereda.bff.issuing.domain

import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.onboarding.domain.UserToken

@JvmInline
value class CardToken private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "card token is blank" }
    }

    companion object {
        fun of(raw: String): CardToken = CardToken(raw.trim())
    }
}

@JvmInline
value class CardProductToken private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "card product token is blank" }
    }

    companion object {
        fun of(raw: String): CardProductToken = CardProductToken(raw.trim())
    }
}

@JvmInline
value class CardLast4 private constructor(val value: String) {
    init {
        require(value.length == 4 && value.all { it.isDigit() }) { "last4 must be four digits" }
    }

    companion object {
        fun of(raw: String): CardLast4 = CardLast4(raw.trim())
    }
}

class IssuedCard private constructor(
    val cardholderId: CardholderId,
    val token: CardToken,
    val last4: CardLast4,
    val productToken: CardProductToken,
) {
    companion object {
        fun issue(
            cardholderId: CardholderId,
            token: CardToken,
            last4: CardLast4,
            productToken: CardProductToken,
        ): IssuedCard = IssuedCard(cardholderId, token, last4, productToken)
    }
}

data class IssuedCardDraft(
    val token: CardToken,
    val last4: CardLast4,
    val productToken: CardProductToken,
)

class NoActiveCardProduct : RuntimeException()

interface IssuedCardRepository {
    fun findByCardholder(cardholderId: CardholderId): IssuedCard?

    fun save(card: IssuedCard)
}

interface CardIssuing {
    suspend fun issueFor(userToken: UserToken): IssuedCardDraft
}
