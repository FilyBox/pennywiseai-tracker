package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class SantanderMexicoParserTest {

    private val parser = SantanderMexicoParser()

    @TestFactory
    fun `santander mexico parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Card purchase at store",
                message = "Santander: Compra \$450.00 con tarjeta *1234 en COSTCO MEXICO 25/Mar/26",
                sender = "SANTANDER",
                expected = ExpectedTransaction(
                    amount = BigDecimal("450.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "COSTCO MEXICO",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Card charge with available limit",
                message = "Santander: Cargo por \$3,500.00 en tu tarjeta *4321 MERCADO LIBRE 25/Mar/26 Limite disponible \$20,000.00",
                sender = "SantanderMx",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3500.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "MERCADO LIBRE",
                    accountLast4 = "4321",
                    availableLimit = BigDecimal("20000.00"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ATM withdrawal with balance",
                message = "Santander: Retiro \$2,000.00 cuenta *5678 ATM 25/Mar/26 Saldo disponible \$8,000.00",
                sender = "SANTANDER",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("8000.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "SPEI transfer sent",
                message = "Santander: Transferencia SPEI \$15,000.00 enviada desde cuenta *5678 25/Mar/26 Saldo \$5,000.00",
                sender = "Santander",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("5000.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Deposit received",
                message = "Santander: Deposito \$10,000.00 en tu cuenta *5678 25/Mar/26 Saldo \$18,000.00",
                sender = "SANTANDER",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "MXN",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678",
                    balance = BigDecimal("18000.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Direct debit with merchant",
                message = "Santander: Domiciliacion cargo \$599.00 cuenta *5678 TELMEX 25/Mar/26 Saldo \$7,400.00",
                sender = "SANTANDER",
                expected = ExpectedTransaction(
                    amount = BigDecimal("599.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "TELMEX",
                    accountLast4 = "5678",
                    balance = BigDecimal("7400.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Service payment with merchant",
                message = "Santander: Pago de servicio \$350.00 cuenta *5678 CFE 25/Mar/26 Saldo \$7,050.00",
                sender = "SANTANDER",
                expected = ExpectedTransaction(
                    amount = BigDecimal("350.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    merchant = "CFE",
                    accountLast4 = "5678",
                    balance = BigDecimal("7050.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "OTP message should be rejected",
                message = "Santander: Tu codigo de verificacion es 123456. No lo compartas.",
                sender = "SANTANDER",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message should be rejected",
                message = "Santander: Aprovecha nuestra oferta de credito personal con tasa preferencial.",
                sender = "SANTANDER",
                shouldParse = false
            )
        )

        val handleCases = listOf(
            Pair("SANTANDER", true),
            Pair("Santander", true),
            Pair("SantanderMx", true),
            Pair("santander", true),
            Pair("SANTANDER BANK", false),
            Pair("STANDARDCHARTERED", false),
            Pair("OTHER", false),
            Pair("BBVA", false)
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases)
    }

    @TestFactory
    fun `factory resolves santander mexico`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Santander Mexico",
                sender = "SANTANDER",
                currency = "MXN",
                message = "Santander: Compra \$450.00 con tarjeta *1234 en COSTCO MEXICO 25/Mar/26",
                expected = ExpectedTransaction(
                    amount = BigDecimal("450.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Santander Mexico",
                sender = "SantanderMx",
                currency = "MXN",
                message = "Santander: Deposito \$10,000.00 en tu cuenta *5678 25/Mar/26 Saldo \$18,000.00",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "MXN",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Santander Mexico factory tests")
    }
}
