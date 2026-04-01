package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class GoogleWalletParserTest {

    private val parser = GoogleWalletParser()

    @TestFactory
    fun `google wallet parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Google Pay sent payment",
                message = "Google Pay: You sent \$25.00 to John. Ref #GP1234567890",
                sender = "Google",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "John",
                    reference = "GP1234567890"
                )
            ),
            ParserTestCase(
                name = "Google Pay received payment with balance",
                message = "Google Pay: You received \$50.00 from Jane. Balance: \$150.00",
                sender = "GooglePay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "USD",
                    type = TransactionType.INCOME,
                    merchant = "Jane",
                    balance = BigDecimal("150.00")
                )
            ),
            ParserTestCase(
                name = "Google Play purchase",
                message = "Google Play: You purchased Spotify Premium for \$9.99. Order #GPA.1234-5678-9012-34567",
                sender = "Google",
                expected = ExpectedTransaction(
                    amount = BigDecimal("9.99"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "Spotify Premium",
                    reference = "GPA.1234-5678-9012-34567"
                )
            ),
            ParserTestCase(
                name = "Google Play subscription renewal",
                message = "Google Play: Your subscription to Netflix \$15.49 has been renewed. Order #GPA.1234-5678-9012-34568",
                sender = "GOOGLE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15.49"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "Netflix",
                    reference = "GPA.1234-5678-9012-34568"
                )
            ),
            ParserTestCase(
                name = "Google Pay tap to pay at store",
                message = "Google Pay: Payment of \$45.00 at STARBUCKS with card ending 1234",
                sender = "Google Pay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "STARBUCKS",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Google Play refund",
                message = "Google Play: Refund of \$4.99 for Calm Premium. Order #GPA.1234-5678-9012-34569",
                sender = "Google",
                expected = ExpectedTransaction(
                    amount = BigDecimal("4.99"),
                    currency = "USD",
                    type = TransactionType.INCOME,
                    merchant = "Calm Premium",
                    reference = "GPA.1234-5678-9012-34569"
                )
            ),
            ParserTestCase(
                name = "Google Wallet card charge",
                message = "Google Wallet: \$120.00 charged at WALMART with card ending 5678",
                sender = "GOOGLE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("120.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "WALMART",
                    accountLast4 = "5678",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "OTP message should be rejected",
                message = "Google: Your verification code is 123456. Do not share this code.",
                sender = "Google",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message should be rejected",
                message = "Google: Check out the latest deals on Google Store. Shop now!",
                sender = "Google",
                shouldParse = false
            )
        )

        val handleCases = listOf(
            Pair("Google", true),
            Pair("GOOGLE", true),
            Pair("GooglePay", true),
            Pair("GOOGLEPAY", true),
            Pair("Google Pay", true),
            Pair("google", true),
            Pair("GOOGLEVOICE", false),
            Pair("GOOGLEFI", false),
            Pair("OTHER", false),
            Pair("PAYPAL", false)
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases)
    }

    @TestFactory
    fun `factory resolves google wallet`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Google Wallet",
                sender = "Google",
                currency = "USD",
                message = "Google Pay: You sent \$25.00 to John. Ref #GP1234567890",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Google Wallet",
                sender = "GooglePay",
                currency = "USD",
                message = "Google Pay: You received \$50.00 from Jane. Balance: \$150.00",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "USD",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Google Wallet factory tests")
    }
}
