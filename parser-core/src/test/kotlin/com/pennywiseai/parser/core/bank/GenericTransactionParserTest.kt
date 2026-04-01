package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class GenericTransactionParserTest {

    private val parser = GenericTransactionParser()

    @TestFactory
    fun `generic parser handles English transaction messages`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "English debit with Rs currency",
                message = "Rs.5,000.00 debited from A/c XX1234 on 25/03/26. Avl Bal: Rs.15,000.00. Ref 123456789",
                sender = "UNKNOWN-BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("15000.00")
                )
            ),
            ParserTestCase(
                name = "English credit notification",
                message = "INR 10,000.00 credited to A/c XX5678 on 25/03/26. Avl Bal: INR 25,000.00",
                sender = "VM-SOMEBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678",
                    balance = BigDecimal("25000.00")
                )
            ),
            ParserTestCase(
                name = "USD card charge",
                message = "Your card ending 4321 has been charged $45.99 at AMAZON.COM. Ref TXN123456",
                sender = "12345",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.99"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "4321",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Refund notification",
                message = "Refund of $25.00 credited to your account ending 9876. Ref RF789012",
                sender = "PAYMENTS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "USD",
                    type = TransactionType.INCOME,
                    accountLast4 = "9876"
                )
            ),
            ParserTestCase(
                name = "Subscription renewal",
                message = "Your subscription to Netflix has been renewed for $15.49. Card ending 1234",
                sender = "NETFLIX",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15.49"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "EUR payment",
                message = "Payment of EUR 120.00 at IKEA STORE on 25/03/26. Balance: EUR 3,500.00",
                sender = "EUROBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("120.00"),
                    currency = "EUR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("3500.00")
                )
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `generic parser handles Spanish transaction messages`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Spanish - cargo (charge)",
                message = "Cargo de $1,500.00 en tu tarjeta *4567 en WALMART 25/Mar/26 Saldo $12,000.00",
                sender = "BANCO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "4567",
                    balance = BigDecimal("12000.00"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Spanish - compra (purchase)",
                message = "Compra por $350.00 con tarjeta *1234 en OXXO 25/Mar/26",
                sender = "BANK-MX",
                expected = ExpectedTransaction(
                    amount = BigDecimal("350.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Spanish - deposito (deposit/income)",
                message = "Deposito de $5,000.00 en tu cuenta *5678 25/Mar/26 Saldo $17,000.00",
                sender = "MYBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "USD",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678",
                    balance = BigDecimal("17000.00")
                )
            ),
            ParserTestCase(
                name = "Spanish - pago recurrente (recurring payment)",
                message = "Pago recurrente $99.00 a SPOTIFY cobrado de tu saldo",
                sender = "WALLET",
                expected = ExpectedTransaction(
                    amount = BigDecimal("99.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE
                )
            ),
            ParserTestCase(
                name = "Spanish - transferencia (transfer)",
                message = "Transferencia SPEI $2,500.00 de tu cuenta *5678 25/Mar/26",
                sender = "SPEI-ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678"
                )
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `generic parser rejects non-transaction messages`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "OTP message rejected",
                message = "Your OTP is 123456. Do not share with anyone. Valid for 5 minutes.",
                sender = "BANK-OTP",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message rejected",
                message = "Congratulations! You have won a prize! Click here to claim.",
                sender = "PROMO",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Payment request rejected",
                message = "John has requested $50.00 from you. Approve or decline.",
                sender = "PAYAPP",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Due date reminder rejected",
                message = "Your credit card payment of $500.00 is due on 25/03/26. Please pay on time.",
                sender = "CARD-ALERT",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Plain text message rejected",
                message = "Hello! How are you doing today?",
                sender = "FRIEND",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Verification code rejected",
                message = "Tu codigo de verificacion es 456789. No lo compartas.",
                sender = "SERVICIO",
                shouldParse = false
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `generic parser handles multi-currency amounts`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "GBP payment",
                message = "Payment of GBP 50.00 charged to your card ending 1234 at TESCO",
                sender = "UK-BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "GBP",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "MXN amount",
                message = "Cargo MXN 2,500.00 en tu cuenta *9876. Saldo disponible: MXN 15,000.00",
                sender = "MX-BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.00"),
                    currency = "MXN",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "9876"
                )
            ),
            ParserTestCase(
                name = "Euro symbol amount",
                message = "You spent €75.50 at FNAC STORE on 25/03/26. Balance: €2,400.00",
                sender = "EUROBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75.50"),
                    currency = "EUR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("2400.00")
                )
            ),
            ParserTestCase(
                name = "INR rupee symbol",
                message = "₹2,500.00 debited from your account XX4567 on 25/03/26",
                sender = "IN-BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "4567"
                )
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `generic parser is the catch-all in factory`(): List<DynamicTest> {
        return listOf(
            DynamicTest.dynamicTest("Factory returns GenericTransactionParser for unknown sender") {
                val parser = BankParserFactory.getParser("COMPLETELY-UNKNOWN-SENDER-XYZ")
                org.junit.jupiter.api.Assertions.assertNotNull(parser, "Factory should return a parser for any sender")
                org.junit.jupiter.api.Assertions.assertEquals("Unknown", parser?.getBankName(), "Should be the GenericTransactionParser")
            }
        )
    }
}
