package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BanorteBankParserTest {

    private val parser = BanorteBankParser()

    @TestFactory
    fun `banorte parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Card charge at store",
                message = "Banorte: Se realizo un cargo de \$1,500.00 en tu tarjeta terminacion 1234 en WALMART SUPERCENTER 25/Mar/26",
                sender = "BANORTE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "WALMART SUPERCENTER",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Purchase with card",
                message = "Banorte: Compra con tarjeta *4567 por \$350.00 en OXXO 25/Mar/26",
                sender = "Banorte",
                expected = ExpectedTransaction(
                    amount = BigDecimal("350.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "OXXO",
                    accountLast4 = "4567",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ATM withdrawal with balance",
                message = "Banorte: Retiro de \$3,000.00 de tu cuenta *5678 en cajero 25/Mar/26 Saldo \$12,000.00",
                sender = "BANORTE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("12000.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "SPEI transfer sent",
                message = "Banorte: Transferencia SPEI de \$2,500.00 de tu cuenta *5678 25/Mar/26 Saldo \$9,500.00",
                sender = "BanorteMovil",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("9500.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Deposit received",
                message = "Banorte: Se abono \$5,000.00 a tu cuenta *5678 por concepto de NOMINA 25/Mar/26 Saldo \$17,000.00",
                sender = "BANORTE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "MXN",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678",
                    balance = BigDecimal("17000.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Payment at merchant",
                message = "Banorte: Pago de \$150.00 con tu tarjeta *1234 en SPOTIFY 25/Mar/26",
                sender = "BANORTE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "SPOTIFY",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Direct debit with merchant",
                message = "Banorte: Domiciliacion \$499.00 de tu cuenta *5678 TELMEX 25/Mar/26 Saldo \$11,500.00",
                sender = "BANORTE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("499.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "TELMEX",
                    accountLast4 = "5678",
                    balance = BigDecimal("11500.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "OTP message should be rejected",
                message = "Banorte: Tu clave de verificacion es 987654. No la compartas con nadie.",
                sender = "BANORTE",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message should be rejected",
                message = "Banorte: Aprovecha nuestra promocion de credito personal con tasa preferencial.",
                sender = "BANORTE",
                shouldParse = false
            )
        )

        val handleCases = listOf(
            Pair("BANORTE", true),
            Pair("Banorte", true),
            Pair("BanorteMovil", true),
            Pair("banorte", true),
            Pair("BANORTE_MX", true),
            Pair("OTHER", false),
            Pair("BBVA", false)
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases)
    }

    @TestFactory
    fun `factory resolves banorte`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Banorte",
                sender = "BANORTE",
                currency = "MXN",
                message = "Banorte: Se realizo un cargo de \$1,500.00 en tu tarjeta terminacion 1234 en WALMART SUPERCENTER 25/Mar/26",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Banorte",
                sender = "BanorteMovil",
                currency = "MXN",
                message = "Banorte: Se abono \$5,000.00 a tu cuenta *5678 por concepto de NOMINA 25/Mar/26 Saldo \$17,000.00",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "MXN",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Banorte factory tests")
    }
}
