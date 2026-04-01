package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Citibanamex (formerly Banamex), a major bank in Mexico.
 * Handles SMS notifications for card purchases, charges, withdrawals,
 * SPEI transfers, deposits, and direct debits (pago domiciliado).
 *
 * Example SMS formats:
 * - Purchase: "Citibanamex: Compra por $750.00 con tu tarjeta *4567 en LIVERPOOL POLANCO 25/Mar/26"
 * - Card charge: "Citibanamex: Cargo a tu tarjeta *1234 por $2,100.00 en AMAZON.COM.MX 25/Mar/26 Limite disp. $28,000.00"
 * - Withdrawal: "Citibanamex: Retiro $5,000.00 de tu cuenta *5678 en cajero automatico 25/Mar/26 Saldo $20,000.00"
 * - SPEI transfer: "Citibanamex: Se realizo transferencia SPEI por $10,000.00 de tu cuenta *5678 25/Mar/26 Saldo $15,000.00"
 * - Deposit: "Citibanamex: Deposito por $8,000.00 en tu cuenta *5678 25/Mar/26 Saldo $28,000.00"
 * - Direct debit: "Citibanamex: Pago domiciliado $199.00 de tu cuenta *5678 NETFLIX 25/Mar/26 Saldo $14,500.00"
 */
class CitibanamexParser : BaseMexicanBankParser() {

    override fun getBankName() = "Citibanamex"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender.contains("BANAMEX") ||
                upperSender.contains("CITIBANAMEX") ||
                upperSender.contains("CITIBANMX")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Citibanamex-specific patterns
        val citibanamexAmountPatterns = listOf(
            // "Compra por $750.00", "Cargo...por $2,100.00", "Deposito por $8,000.00", "transferencia...por $10,000.00"
            Regex(
                """(?:Compra|Cargo|Deposito|transferencia(?:\s+SPEI)?)\s+.+?por\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "Retiro $5,000.00"
            Regex(
                """Retiro\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "Pago domiciliado $199.00"
            Regex(
                """Pago\s+domiciliado\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in citibanamexAmountPatterns) {
            pattern.find(message)?.let { match ->
                return parseMexicanAmount(match.groupValues[1])
            }
        }

        // Fallback to base class amount extraction
        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            // Expense keywords specific to Citibanamex
            lowerMessage.contains("compra") -> TransactionType.EXPENSE
            lowerMessage.contains("cargo") -> TransactionType.EXPENSE
            lowerMessage.contains("retiro") -> TransactionType.EXPENSE
            lowerMessage.contains("transferencia") -> TransactionType.EXPENSE
            lowerMessage.contains("pago domiciliado") -> TransactionType.EXPENSE
            lowerMessage.contains("pago") -> TransactionType.EXPENSE

            // Income keywords specific to Citibanamex
            lowerMessage.contains("deposito") -> TransactionType.INCOME

            // Fallback to base class
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Pattern 1: "en MERCHANT_NAME" (for purchases, charges)
        // Stops at date pattern (dd/Mon/yy or dd/mm/yy), "Saldo", "Limite", or end of string
        val enMerchantPattern = Regex(
            """\ben\s+(.+?)(?:\s+\d{2}/\w{3}/\d{2}|\s+\d{2}/\d{2}/\d{2}|\s+[Ss]aldo|\s+[Ll]imite|$)""",
            RegexOption.IGNORE_CASE
        )
        enMerchantPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            // Filter out non-merchant matches like "en tu tarjeta", "en cajero", "en tu cuenta"
            val filteredLower = merchant.lowercase()
            if (merchant.isNotEmpty() &&
                !filteredLower.startsWith("tu tarjeta") &&
                !filteredLower.startsWith("tu cuenta") &&
                !filteredLower.startsWith("cajero") &&
                isValidMerchantName(merchant)
            ) {
                return cleanMerchantName(merchant)
            }
        }

        // Pattern 2: "Pago domiciliado $XXX ... MERCHANT_NAME dd/Mon/yy"
        // Direct debit merchant appears after amount and account, before date
        if (lowerMessage.contains("pago domiciliado")) {
            val domPattern = Regex(
                """Pago\s+domiciliado\s+\$\s?[0-9,]+(?:\.\d{1,2})?\s+de\s+tu\s+cuenta\s+\*\d{4}\s+(.+?)(?:\s+\d{2}/\w{3}/\d{2}|\s+\d{2}/\d{2}/\d{2}|\s+[Ss]aldo|$)""",
                RegexOption.IGNORE_CASE
            )
            domPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty() && isValidMerchantName(merchant)) {
                    return cleanMerchantName(merchant)
                }
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Citibanamex-specific: "Saldo $X,XXX.XX"
        val balancePattern = Regex(
            """[Ss]aldo\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            return parseMexicanAmount(match.groupValues[1])
        }

        return super.extractBalance(message)
    }

    override fun extractAvailableLimit(message: String): BigDecimal? {
        // Citibanamex-specific: "Limite disp. $X,XXX.XX" or "Limite disponible $X,XXX.XX"
        val limitPattern = Regex(
            """[Ll]imite\s+(?:disp\.?|disponible)\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        limitPattern.find(message)?.let { match ->
            return parseMexicanAmount(match.groupValues[1])
        }

        return super.extractAvailableLimit(message)
    }

    override fun extractAccountLast4(message: String): String? {
        val accountPatterns = listOf(
            // "tarjeta *1234"
            Regex("""tarjeta\s+\*(\d{4})""", RegexOption.IGNORE_CASE),
            // "cuenta *5678"
            Regex("""cuenta\s+\*(\d{4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in accountPatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        // Fallback to base class patterns
        return super.extractAccountLast4(message)
    }

    override fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("tarjeta") ||
                super.detectIsCard(message)
    }
}
