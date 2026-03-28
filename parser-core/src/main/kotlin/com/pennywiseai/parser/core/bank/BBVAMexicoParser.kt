package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for BBVA México (BBVA Bancomer), the largest bank in Mexico.
 * Handles SMS notifications for card purchases, account debits, transfers,
 * deposits, SPEI transfers, direct debits, and credit card charges.
 *
 * Example SMS formats:
 * - Card purchase: "BBVA. Compra $500.00 con tc *1234 en TIENDA 25/03/26"
 * - Account debit: "BBVA. Retiro $2,000.00 de cuenta *5678 ATM 25/03/26 Saldo $15,432.10"
 * - Transfer out: "BBVA. Transferencia enviada $1,500.00 de cuenta *5678 a CLABE 012345 25/03/26"
 * - Deposit: "BBVA. Deposito recibido $3,000.00 en cuenta *5678 25/03/26 Saldo $18,000.00"
 * - SPEI transfer: "BBVA. SPEI enviado $5,000.00 de cuenta *5678 25/03/26 Saldo $8,000.00"
 * - Direct debit: "BBVA. Domiciliacion $299.00 de cuenta *5678 NETFLIX 25/03/26 Saldo $7,000.00"
 * - Credit card charge: "BBVA. Cargo $1,200.00 a tc *4321 en AMAZON.COM.MX 25/03/26 Limite disponible $15,000.00"
 */
class BBVAMexicoParser : BaseMexicanBankParser() {

    override fun getBankName() = "BBVA Mexico"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase().replace(Regex("\\s+"), "")
        return normalizedSender.contains("BBVA") ||
                normalizedSender.contains("BANCOMER")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // BBVA-specific patterns: keyword followed by amount
        val bbvaAmountPatterns = listOf(
            Regex(
                """(?:Compra|Retiro|Transferencia\s+enviada|Deposito\s+recibido|SPEI\s+enviado|Domiciliacion|Cargo)\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in bbvaAmountPatterns) {
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
            // Expense keywords specific to BBVA
            lowerMessage.contains("compra") -> TransactionType.EXPENSE
            lowerMessage.contains("retiro") -> TransactionType.EXPENSE
            lowerMessage.contains("transferencia enviada") -> TransactionType.EXPENSE
            lowerMessage.contains("spei enviado") -> TransactionType.EXPENSE
            lowerMessage.contains("domiciliacion") -> TransactionType.EXPENSE
            lowerMessage.contains("cargo") -> TransactionType.EXPENSE

            // Income keywords specific to BBVA
            lowerMessage.contains("deposito recibido") -> TransactionType.INCOME
            lowerMessage.contains("abono") -> TransactionType.INCOME

            // Fallback to base class
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Pattern 1: "en MERCHANT_NAME" (after amount/account for purchases and charges)
        // Stops at date pattern (dd/mm/yy), "Saldo", "Limite", or end of string
        val enMerchantPattern = Regex(
            """\ben\s+(.+?)(?:\s+\d{2}/\d{2}/\d{2}|\s+[Ss]aldo|\s+[Ll]imite|$)""",
            RegexOption.IGNORE_CASE
        )
        enMerchantPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty() && isValidMerchantName(merchant)) {
                return cleanMerchantName(merchant)
            }
        }

        // Pattern 2: "Domiciliacion $XXX ... MERCHANT_NAME dd/mm/yy"
        // Direct debit merchant appears after amount and account, before date
        if (lowerMessage.contains("domiciliacion")) {
            val domPattern = Regex(
                """Domiciliacion\s+\$\s?[0-9,]+(?:\.\d{1,2})?\s+de\s+cuenta\s+\*\d{4}\s+(.+?)(?:\s+\d{2}/\d{2}/\d{2}|\s+[Ss]aldo|$)""",
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
        // BBVA-specific: "Saldo $X,XXX.XX" at end of message
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
        // BBVA-specific: "Limite disponible $X,XXX.XX"
        val limitPattern = Regex(
            """[Ll]imite\s+disponible\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        limitPattern.find(message)?.let { match ->
            return parseMexicanAmount(match.groupValues[1])
        }

        return super.extractAvailableLimit(message)
    }

    override fun extractAccountLast4(message: String): String? {
        val accountPatterns = listOf(
            // "tc *1234" (tarjeta de credito)
            Regex("""tc\s+\*(\d{4})""", RegexOption.IGNORE_CASE),
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
        return lowerMessage.contains("tc *") ||
                lowerMessage.contains("tarjeta") ||
                super.detectIsCard(message)
    }
}
