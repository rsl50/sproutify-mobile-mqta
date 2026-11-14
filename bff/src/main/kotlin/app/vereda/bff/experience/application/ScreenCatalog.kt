package app.vereda.bff.experience.application

import app.vereda.bff.funding.domain.Money
import app.vereda.bff.issuing.domain.IssuedCard
import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.bff.transactions.domain.CardTransaction
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
                id = "transactions",
                label = "Transações",
                style = "text",
                action = ComponentAction(type = "navigate", screenId = "transactions"),
            ),
            ButtonComponent(
                id = "simulate",
                label = "Simular compra",
                action = ComponentAction(type = "navigate", screenId = "simulate-purchase"),
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

    fun transactions(items: List<CardTransaction>): Screen {
        val rows = if (items.isEmpty()) {
            listOf(TextComponent(id = "empty", value = "Nenhuma transação ainda.", style = "caption"))
        } else {
            items.map { item ->
                TextComponent(
                    id = item.token,
                    value = "${item.merchant.value} · ${item.amount.format()} · ${item.state}",
                )
            }
        }
        return Screen(
            id = "transactions",
            title = "Transações",
            components = rows + listOf(
                ButtonComponent(
                    id = "refresh",
                    label = "Atualizar",
                    style = "text",
                    action = ComponentAction(type = "refresh", screenId = "transactions"),
                ),
                ButtonComponent(
                    id = "back",
                    label = "Voltar",
                    style = "text",
                    action = ComponentAction(type = "navigate", screenId = "home"),
                ),
            ),
        )
    }

    fun simulatePurchase(): Screen = Screen(
        id = "simulate-purchase",
        title = "Simular compra",
        components = listOf(
            TextComponent(
                id = "hint",
                value = "Simula uma autorização no sandbox. O saldo da home é lido de novo depois.",
                style = "caption",
            ),
            TextFieldComponent(
                id = "amount",
                label = "Valor em USD",
                keyboard = "number",
                validations = listOf(
                    FieldRule("required", "Informe o valor"),
                    FieldRule("money", "Use um valor maior que zero, com até 2 casas."),
                ),
            ),
            TextFieldComponent(
                id = "merchant",
                label = "Estabelecimento",
                value = "Padaria da Esquina",
                validations = listOf(FieldRule("required", "Informe o estabelecimento")),
            ),
            ButtonComponent(
                id = "submit",
                label = "Simular compra",
                action = ComponentAction(type = "submit", endpoint = "actions/simulate-purchase"),
            ),
        ),
    )
}

class UnknownScreen(val screenId: String) : RuntimeException()
