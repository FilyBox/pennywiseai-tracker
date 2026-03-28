package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class MercadoPagoParserTest {

    private val parser = MercadoPagoParser()

    @TestFactory
    fun `mercado pago parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Payment to merchant",
                message = "Mercado Pago: Pagaste \$500.00 a Tienda Ejemplo. Ref: MP123456789",
                sender = "MERCADOPAGO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "Tienda Ejemplo",
                    reference = "MP123456789"
                )
            ),
            ParserTestCase(
                name = "Received money with balance",
                message = "Mercado Pago: Recibiste \$1,000.00 de Persona Ejemplo. Tu saldo: \$2,500.00",
                sender = "MercadoPago",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "MXN",
                    type = TransactionType.INCOME,
                    merchant = "Persona Ejemplo",
                    balance = BigDecimal("2500.00")
                )
            ),
            ParserTestCase(
                name = "QR payment at store",
                message = "Mercado Pago: Pago con QR \$350.00 en OXXO. Ref: QR987654321",
                sender = "MELI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("350.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "OXXO",
                    reference = "QR987654321"
                )
            ),
            ParserTestCase(
                name = "Transfer to Mercado Pago account",
                message = "Mercado Pago: Transferiste \$2,000.00 a cuenta Mercado Pago. Ref: TR456789012",
                sender = "MPAGO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    reference = "TR456789012"
                )
            ),
            ParserTestCase(
                name = "Card charge at merchant",
                message = "Mercado Pago: Cargo \$1,200.00 con tu tarjeta *4321 en MERCADO LIBRE",
                sender = "MP",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1200.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "MERCADO LIBRE",
                    accountLast4 = "4321",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Refund for purchase",
                message = "Mercado Pago: Devolucion \$750.00 por compra en Amazon. Ref: DEV123456",
                sender = "MERCADOPAGO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750.00"),
                    currency = "MXN",
                    type = TransactionType.INCOME,
                    merchant = "Amazon",
                    reference = "DEV123456"
                )
            ),
            ParserTestCase(
                name = "Subscription payment",
                message = "Mercado Pago: Pago recurrente \$99.00 a SPOTIFY cobrado de tu saldo",
                sender = "MERCADOPAGO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("99.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "SPOTIFY"
                )
            ),
            ParserTestCase(
                name = "Withdrawal to bank account",
                message = "Mercado Pago: Retiro \$3,000.00 a tu cuenta bancaria *5678",
                sender = "MERCADOPAGO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678"
                )
            ),
            ParserTestCase(
                name = "OTP message should be rejected",
                message = "Mercado Pago: Tu codigo de verificacion es 456789. No lo compartas.",
                sender = "MERCADOPAGO",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message should be rejected",
                message = "Mercado Pago: Aprovecha esta promocion exclusiva. 30% descuento en tu siguiente compra.",
                sender = "MERCADOPAGO",
                shouldParse = false
            )
        )

        val handleCases = listOf(
            Pair("MERCADOPAGO", true),
            Pair("MercadoPago", true),
            Pair("MELI", true),
            Pair("MP", true),
            Pair("MPAGO", true),
            Pair("mercadopago", true),
            Pair("MERCADO PAGO", true),
            Pair("OTHER", false),
            Pair("BBVA", false)
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases)
    }

    @TestFactory
    fun `factory resolves mercado pago`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Mercado Pago",
                sender = "MERCADOPAGO",
                currency = "MXN",
                message = "Mercado Pago: Pagaste \$500.00 a Tienda Ejemplo. Ref: MP123456789",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Mercado Pago",
                sender = "MELI",
                currency = "MXN",
                message = "Mercado Pago: Recibiste \$1,000.00 de Persona Ejemplo. Tu saldo: \$2,500.00",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "MXN",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Mercado Pago factory tests")
    }
}
