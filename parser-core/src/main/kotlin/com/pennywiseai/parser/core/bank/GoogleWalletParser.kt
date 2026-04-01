package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Google Wallet / Google Pay / Google Play transaction notifications.
 *
 * Handles SMS from Google for payments, purchases, subscriptions, refunds,
 * and card charges through Google Pay, Google Wallet, and Google Play.
 *
 * Supported SMS formats:
 * - Sent payment: "Google Pay: You sent $25.00 to John. Ref #GP1234567890"
 * - Received payment: "Google Pay: You received $50.00 from Jane. Balance: $150.00"
 * - Play purchase: "Google Play: You purchased Spotify Premium for $9.99. Order #GPA.1234-5678-9012-34567"
 * - Subscription renewal: "Google Play: Your subscription to Netflix $15.49 has been renewed. Order #GPA.1234-5678-9012-34568"
 * - Tap to pay: "Google Pay: Payment of $45.00 at STARBUCKS with card ending 1234"
 * - Refund: "Google Play: Refund of $4.99 for Calm Premium. Order #GPA.1234-5678-9012-34569"
 * - Card charge: "Google Wallet: $120.00 charged at WALMART with card ending 5678"
 */
class GoogleWalletParser : BankParser() {

    override fun getBankName() = "Google Wallet"

    override fun getCurrency() = "USD"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        if (upperSender.contains("GOOGLEVOICE") || upperSender.contains("GOOGLEFI")) {
            return false
        }
        return upperSender.contains("GOOGLE")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("sent") ||
                lowerMessage.contains("received") ||
                lowerMessage.contains("purchased") ||
                lowerMessage.contains("subscription") ||
                lowerMessage.contains("payment") ||
                lowerMessage.contains("refund") ||
                lowerMessage.contains("charged") ||
                lowerMessage.contains("renewed")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            // "sent $25.00", "received $50.00"
            Regex("""(?:sent|received)\s+\$([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "for $9.99" (purchases/subscriptions)
            Regex("""for\s+\$([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Payment of $45.00"
            Regex("""Payment\s+of\s+\$([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "Refund of $4.99"
            Regex("""Refund\s+of\s+\$([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            // "$120.00 charged"
            Regex("""\$([0-9,]+(?:\.\d{2})?)\s+charged""", RegexOption.IGNORE_CASE),
            // Netflix $15.49 has been renewed
            Regex("""\$([0-9,]+(?:\.\d{2})?)\s+has\s+been""", RegexOption.IGNORE_CASE),
            // Generic currency: "USD 25.00", "EUR 10.00"
            Regex("""[A-Z]{3}\s+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        return super.extractAmount(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val patterns = listOf(
            // "to John", "to MERCHANT" (after sent/payment)
            Regex("""(?:sent\s+\$[0-9,]+(?:\.\d{2})?)\s+to\s+(.+?)(?:\.\s*|$)""", RegexOption.IGNORE_CASE),
            // "from Jane" (after received)
            Regex("""(?:received\s+\$[0-9,]+(?:\.\d{2})?)\s+from\s+(.+?)(?:\.\s*|$)""", RegexOption.IGNORE_CASE),
            // "at STARBUCKS" / "at WALMART"
            Regex("""at\s+([A-Z][A-Za-z0-9\s&'.-]+?)(?:\s+with\s+|\.\s*|$)""", RegexOption.IGNORE_CASE),
            // "purchased PRODUCT_NAME for"
            Regex("""purchased\s+(.+?)\s+for\s+""", RegexOption.IGNORE_CASE),
            // "subscription to Netflix"
            Regex("""subscription\s+to\s+(.+?)\s+\$""", RegexOption.IGNORE_CASE),
            // "Refund of $X.XX for PRODUCT_NAME"
            Regex("""Refund\s+of\s+\$[0-9,]+(?:\.\d{2})?\s+for\s+(.+?)(?:\.\s*|$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (isValidMerchantName(merchant)) {
                    return cleanMerchantName(merchant)
                }
            }
        }
        return super.extractMerchant(message, sender)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME
            lowerMessage.contains("sent") -> TransactionType.EXPENSE
            lowerMessage.contains("purchased") -> TransactionType.EXPENSE
            lowerMessage.contains("subscription") -> TransactionType.EXPENSE
            lowerMessage.contains("payment") -> TransactionType.EXPENSE
            lowerMessage.contains("charged") -> TransactionType.EXPENSE
            lowerMessage.contains("renewed") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractReference(message: String): String? {
        val patterns = listOf(
            // "Ref #GP1234567890"
            Regex("""Ref\s+#([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE),
            // "Order #GPA.1234-5678-9012-34567"
            Regex("""Order\s+#(GPA\.[0-9-]+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        val balancePattern = Regex(
            """Balance:\s+\$([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    override fun extractAccountLast4(message: String): String? {
        val cardPattern = Regex("""card\s+ending\s+(\d{4})""", RegexOption.IGNORE_CASE)
        return cardPattern.find(message)?.let {
            extractLast4Digits(it.groupValues[1])
        }
    }

    override fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("card ending") ||
                lowerMessage.contains("card") ||
                super.detectIsCard(message)
    }

    override fun extractCurrency(message: String): String? {
        // Check for explicit currency codes before dollar amounts
        val currencyPatterns = listOf(
            Regex("""([A-Z]{3})\s+[0-9,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE),
            Regex("""\b(USD|EUR|GBP|CAD|AUD|INR|JPY)\b""", RegexOption.IGNORE_CASE)
        )

        for (pattern in currencyPatterns) {
            pattern.find(message)?.let { match ->
                val code = match.groupValues[1].uppercase()
                if (code.length == 3 && code.all { it.isLetter() }) {
                    return code
                }
            }
        }
        return null
    }
}
