package app.vereda.bff.experience.application

import app.vereda.bff.issuing.domain.IssuedCard
import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.sdui.ButtonComponent
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
        ),
    )

    fun registered(cardholder: Cardholder, card: IssuedCard): Screen = Screen(
        id = "registered",
        title = "Conta criada",
        components = listOf(
            TextComponent(id = "heading", value = "Conta criada", style = "title"),
            TextComponent(
                id = "hello",
                value = "Olá, ${cardholder.name.first} ${cardholder.name.last}",
            ),
            TextComponent(id = "last4", value = "Final ${card.last4.value}", style = "caption"),
        ),
    )
}

class UnknownScreen(val screenId: String) : RuntimeException()
