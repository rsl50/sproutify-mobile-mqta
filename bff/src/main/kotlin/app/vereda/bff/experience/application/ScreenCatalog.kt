package app.vereda.bff.experience.application

import app.vereda.bff.funding.domain.Money
import app.vereda.bff.issuing.domain.IssuedCard
import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.sdui.ButtonComponent
import app.vereda.sdui.CardComponent
import app.vereda.sdui.ComponentAction
import app.vereda.sdui.FieldRule
import app.vereda.sdui.Screen
import app.vereda.sdui.TextComponent
import app.vereda.sdui.TextFieldComponent

class ScreenCatalog {
    fun signup(): Screen = Screen(
        id = "signup",
        title = "Criar conta",
        components = listOf(
            TextComponent(id = "heading", value = "Criar conta", style = "title"),
            TextComponent(
                id = "hint",
                value = "A senha fica neste serviço e não é enviada à Marqeta.",
                style = "caption",
            ),
            TextFieldComponent(
                id = "firstName",
                label = "Nome",
                validations = listOf(FieldRule("required", "Informe o nome")),
            ),
            TextFieldComponent(
                id = "lastName",
                label = "Sobrenome",
                validations = listOf(FieldRule("required", "Informe o sobrenome")),
            ),
            TextFieldComponent(
                id = "email",
                label = "E-mail",
                keyboard = "email",
                validations = listOf(
                    FieldRule("required", "Informe o e-mail"),
                    FieldRule("email", "E-mail inválido"),
                ),
            ),
            TextFieldComponent(
                id = "password",
                label = "Senha",
                keyboard = "password",
                validations = listOf(
                    FieldRule("required", "Informe a senha"),
                    FieldRule("minLength", "Mínimo de 8 caracteres", min = 8),
                ),
            ),
            ButtonComponent(
                id = "submit",
                label = "Criar conta",
                action = ComponentAction(type = "submit", endpoint = "actions/signup"),
            ),
            ButtonComponent(
                id = "go-signin",
                label = "Já tenho conta",
                style = "text",
                action = ComponentAction(type = "navigate", screenId = "signin"),
            ),
        ),
    )

    fun signIn(): Screen = Screen(
        id = "signin",
        title = "Entrar",
        components = listOf(
            TextComponent(id = "heading", value = "Entrar", style = "title"),
            TextComponent(
                id = "hint",
                value = "A senha fica só no serviço local desta demonstração. A Marqeta não a recebe.",
                style = "caption",
            ),
            TextFieldComponent(
                id = "email",
                label = "E-mail",
                keyboard = "email",
                validations = listOf(
                    FieldRule("required", "Informe o e-mail"),
                    FieldRule("email", "E-mail inválido"),
                ),
            ),
            TextFieldComponent(
                id = "password",
                label = "Senha",
                keyboard = "password",
                validations = listOf(
                    FieldRule("required", "Informe a senha"),
                    FieldRule("minLength", "Mínimo de 8 caracteres", min = 8),
                ),
            ),
            ButtonComponent(
                id = "submit",
                label = "Entrar",
                action = ComponentAction(type = "submit", endpoint = "actions/signin"),
            ),
            ButtonComponent(
                id = "go-signup",
                label = "Criar conta",
                style = "text",
                action = ComponentAction(type = "navigate", screenId = "signup"),
            ),
        ),
    )

    fun home(cardholder: Cardholder, card: IssuedCard, balance: Money): Screen = Screen(
        id = "home",
        title = "Início",
        components = listOf(
            TextComponent(
                id = "hello",
                value = "Olá, ${cardholder.name.first}",
                style = "title",
            ),
            CardComponent(
                id = "payment-card",
                holderName = "${cardholder.name.first} ${cardholder.name.last}",
                lastFour = card.last4.value,
                balanceLabel = "Saldo disponível",
                balanceText = balance.format(),
            ),
            ButtonComponent(
                id = "refresh",
                label = "Atualizar saldo",
                style = "text",
                action = ComponentAction(type = "refresh", screenId = "home"),
            ),
            ButtonComponent(
                id = "sign-out",
                label = "Sair",
                style = "text",
                action = ComponentAction(type = "signOut", endpoint = "actions/signout"),
            ),
        ),
    )
}

class UnknownScreen(val screenId: String) : RuntimeException()
