package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BBVAMexicoParserTest {

    private val parser = BBVAMexicoParser()

    @TestFactory
    fun `bbva mexico parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Card purchase at store",
                message = "BBVA. Compra \$500.00 con tc *1234 en TIENDA EL EJEMPLO 25/03/26",
                sender = "BBVA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "TIENDA EL EJEMPLO",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ATM withdrawal with balance",
                message = "BBVA. Retiro \$2,000.00 de cuenta *5678 ATM 25/03/26 Saldo \$15,432.10",
                sender = "BBVA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("15432.10"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Outgoing transfer",
                message = "BBVA. Transferencia enviada \$1,500.00 de cuenta *5678 a CLABE 012345 25/03/26 Saldo \$10,000.00",
                sender = "BBVAMx",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("10000.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Deposit received",
                message = "BBVA. Deposito recibido \$3,000.00 en cuenta *5678 25/03/26 Saldo \$18,000.00",
                sender = "BBVA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000.00"),
                    currency = "MXN",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678",
                    balance = BigDecimal("18000.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "SPEI transfer sent",
                message = "BBVA. SPEI enviado \$5,000.00 de cuenta *5678 25/03/26 Saldo \$8,000.00",
                sender = "BBVA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("8000.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Direct debit with merchant",
                message = "BBVA. Domiciliacion \$299.00 de cuenta *5678 NETFLIX 25/03/26 Saldo \$7,000.00",
                sender = "BBVA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("299.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "NETFLIX",
                    accountLast4 = "5678",
                    balance = BigDecimal("7000.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Credit card charge with available limit",
                message = "BBVA. Cargo \$1,200.00 a tc *4321 en AMAZON.COM.MX 25/03/26 Limite disponible \$15,000.00",
                sender = "BBVA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1200.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "AMAZON.COM.MX",
                    accountLast4 = "4321",
                    creditLimit = BigDecimal("15000.00"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "OTP message should be rejected",
                message = "BBVA. Tu clave de verificacion es 123456. No la compartas.",
                sender = "BBVA",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message should be rejected",
                message = "BBVA. Aprovecha nuestra promocion de credito personal. Solicita ahora.",
                sender = "BBVA",
                shouldParse = false
            )
        )

        val handleCases = listOf(
            Pair("BBVA", true),
            Pair("BBVAMx", true),
            Pair("BANCOMER", true),
            Pair("BBVA_Mx", true),
            Pair("bbva", true),
            Pair("OTHER", false),
            Pair("HSBC", false)
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases)
    }

    @TestFactory
    fun `factory resolves bbva mexico`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "BBVA Mexico",
                sender = "BBVA",
                currency = "MXN",
                message = "BBVA. Compra \$500.00 con tc *1234 en TIENDA EL EJEMPLO 25/03/26",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "BBVA Mexico",
                sender = "BANCOMER",
                currency = "MXN",
                message = "BBVA. Deposito recibido \$3,000.00 en cuenta *5678 25/03/26 Saldo \$18,000.00",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000.00"),
                    currency = "MXN",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "BBVA Mexico factory tests")
    }
}
