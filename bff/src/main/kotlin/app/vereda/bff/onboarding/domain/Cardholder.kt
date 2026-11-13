package app.vereda.bff.onboarding.domain

import at.favre.lib.crypto.bcrypt.BCrypt
import java.util.UUID

@JvmInline
value class CardholderId(val value: String) {
    init {
        require(value.isNotBlank()) { "cardholder id is blank" }
    }

    companion object {
        fun newId(): CardholderId = CardholderId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        fun parse(raw: String): Email {
            val trimmed = raw.trim().lowercase()
            require(isValid(trimmed)) { "invalid email" }
            return Email(trimmed)
        }

        fun isValid(raw: String): Boolean {
            val trimmed = raw.trim()
            val at = trimmed.indexOf('@')
            if (at <= 0 || at != trimmed.lastIndexOf('@')) return false
            val domain = trimmed.substring(at + 1)
            val dot = domain.indexOf('.')
            return dot > 0 && dot < domain.lastIndex
        }
    }
}

data class PersonName(val first: String, val last: String) {
    init {
        require(first.isNotBlank()) { "first name is blank" }
        require(last.isNotBlank()) { "last name is blank" }
    }
}

@JvmInline
value class UserToken private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "user token is blank" }
    }

    companion object {
        fun of(raw: String): UserToken = UserToken(raw.trim())
    }
}

class Password private constructor(
    val hash: String,
) {
    fun matches(plain: String): Boolean =
        BCrypt.verifyer().verify(plain.toCharArray(), hash.toCharArray()).verified

    companion object {
        const val MIN_LENGTH = 8
        const val BCRYPT_COST = 10

        fun create(plain: String): Password {
            require(plain.length >= MIN_LENGTH) { "password is too short" }
            val encoded = BCrypt.withDefaults().hashToString(BCRYPT_COST, plain.toCharArray())
            return Password(encoded)
        }

        fun fromStored(hash: String): Password {
            require(hash.startsWith("\$2")) { "password hash is not bcrypt" }
            return Password(hash)
        }
    }
}

class Cardholder private constructor(
    val id: CardholderId,
    val name: PersonName,
    val email: Email,
    val password: Password,
    val userToken: UserToken,
) {
    companion object {
        fun register(
            id: CardholderId,
            name: PersonName,
            email: Email,
            password: Password,
            userToken: UserToken,
        ): Cardholder = Cardholder(id, name, email, password, userToken)

        fun restore(
            id: CardholderId,
            name: PersonName,
            email: Email,
            password: Password,
            userToken: UserToken,
        ): Cardholder = Cardholder(id, name, email, password, userToken)
    }
}

class CardholderAlreadyExists(val email: Email) : RuntimeException()

interface CardholderRepository {
    fun findByEmail(email: Email): Cardholder?
    fun findById(id: CardholderId): Cardholder?
    fun save(cardholder: Cardholder)
}

interface CardholderEnrollment {
    suspend fun enroll(name: PersonName, email: Email): UserToken
}
