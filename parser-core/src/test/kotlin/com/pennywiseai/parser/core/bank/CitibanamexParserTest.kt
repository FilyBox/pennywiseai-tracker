package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class CitibanamexParserTest {

    private val parser = CitibanamexParser()

    @TestFactory
    fun `citibanamex parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Purchase at store",
                message = "Citibanamex: Compra por \$750.00 con tu tarjeta *4567 en LIVERPOOL POLANCO 25/Mar/26",
                sender = "Citibanamex",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "LIVERPOOL POLANCO",
                    accountLast4 = "4567",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Card charge with available limit",
                message = "Citibanamex: Cargo a tu tarjeta *1234 por \$2,100.00 en AMAZON.COM.MX 25/Mar/26 Limite disp. \$28,000.00",
                sender = "BANAMEX",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2100.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "AMAZON.COM.MX",
                    accountLast4 = "1234",
                    creditLimit = BigDecimal("28000.00"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ATM withdrawal with balance",
                message = "Citibanamex: Retiro \$5,000.00 de tu cuenta *5678 en cajero automatico 25/Mar/26 Saldo \$20,000.00",
                sender = "Citibanamex",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("20000.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "SPEI transfer sent",
                message = "Citibanamex: Se realizo transferencia SPEI por \$10,000.00 de tu cuenta *5678 25/Mar/26 Saldo \$15,000.00",
                sender = "CITIBANMX",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("15000.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Deposit received",
                message = "Citibanamex: Deposito por \$8,000.00 en tu cuenta *5678 25/Mar/26 Saldo \$28,000.00",
                sender = "Citibanamex",
                expected = ExpectedTransaction(
                    amount = BigDecimal("8000.00"),
                    currency = "MXN",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678",
                    balance = BigDecimal("28000.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Direct debit with merchant",
                message = "Citibanamex: Pago domiciliado \$199.00 de tu cuenta *5678 NETFLIX 25/Mar/26 Saldo \$14,500.00",
                sender = "BANAMEX",
                expected = ExpectedTransaction(
                    amount = BigDecimal("199.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "NETFLIX",
                    accountLast4 = "5678",
                    balance = BigDecimal("14500.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "OTP message should be rejected",
                message = "Citibanamex: Tu codigo de verificacion es 123456. No lo compartas.",
                sender = "Citibanamex",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message should be rejected",
                message = "Citibanamex: Aprovecha nuestra promocion de 18 meses sin intereses en compras mayores a 5000.",
                sender = "BANAMEX",
                shouldParse = false
            )
        )

        val handleCases = listOf(
            Pair("BANAMEX", true),
            Pair("Banamex", true),
            Pair("Citibanamex", true),
            Pair("CITIBANAMEX", true),
            Pair("CITIBANMX", true),
            Pair("citibanmx", true),
            Pair("OTHER", false),
            Pair("BBVA", false)
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases)
    }

    @TestFactory
    fun `factory resolves citibanamex`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Citibanamex",
                sender = "BANAMEX",
                currency = "MXN",
                message = "Citibanamex: Compra por \$750.00 con tu tarjeta *4567 en LIVERPOOL POLANCO 25/Mar/26",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Citibanamex",
                sender = "Citibanamex",
                currency = "MXN",
                message = "Citibanamex: Deposito por \$8,000.00 en tu cuenta *5678 25/Mar/26 Saldo \$28,000.00",
                expected = ExpectedTransaction(
                    amount = BigDecimal("8000.00"),
                    currency = "MXN",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Citibanamex factory tests")
    }
}
