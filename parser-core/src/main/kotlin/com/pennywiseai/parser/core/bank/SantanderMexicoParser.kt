package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Santander México, a major Mexican bank.
 * Handles SMS notifications for card purchases, charges, withdrawals,
 * SPEI transfers, deposits, direct debits, and service payments.
 *
 * Example SMS formats:
 * - Purchase: "Santander: Compra $450.00 con tarjeta *1234 en COSTCO MEXICO 25/Mar/26"
 * - Card charge: "Santander: Cargo por $3,500.00 en tu tarjeta *4321 MERCADO LIBRE 25/Mar/26 Limite disponible $20,000.00"
 * - Withdrawal: "Santander: Retiro $2,000.00 cuenta *5678 ATM 25/Mar/26 Saldo disponible $8,000.00"
 * - SPEI transfer: "Santander: Transferencia SPEI $15,000.00 enviada desde cuenta *5678 25/Mar/26 Saldo $5,000.00"
 * - Deposit: "Santander: Deposito $10,000.00 en tu cuenta *5678 25/Mar/26 Saldo $18,000.00"
 * - Direct debit: "Santander: Domiciliacion cargo $599.00 cuenta *5678 TELMEX 25/Mar/26 Saldo $7,400.00"
 * - Service payment: "Santander: Pago de servicio $350.00 cuenta *5678 CFE 25/Mar/26 Saldo $7,050.00"
 */
class SantanderMexicoParser : BaseMexicanBankParser() {

    override fun getBankName() = "Santander Mexico"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        if (upperSender.contains("SANTANDER BANK") || upperSender.contains("STANDARDCHARTERED")) {
            return false
        }
        return upperSender.contains("SANTANDER")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val santanderAmountPatterns = listOf(
            // "Compra $450.00", "Retiro $2,000.00", "Deposito $10,000.00"
            Regex(
                """(?:Compra|Retiro|Deposito)\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "Cargo por $3,500.00"
            Regex(
                """Cargo\s+por\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "Transferencia SPEI $15,000.00"
            Regex(
                """Transferencia\s+(?:SPEI\s+)?\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "Domiciliacion cargo $599.00"
            Regex(
                """Domiciliacion\s+cargo\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "Pago de servicio $350.00"
            Regex(
                """Pago\s+de\s+servicio\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in santanderAmountPatterns) {
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
            // Expense keywords specific to Santander
            lowerMessage.contains("compra") -> TransactionType.EXPENSE
            lowerMessage.contains("cargo") -> TransactionType.EXPENSE
            lowerMessage.contains("retiro") -> TransactionType.EXPENSE
            lowerMessage.contains("transferencia") && lowerMessage.contains("enviada") -> TransactionType.EXPENSE
            lowerMessage.contains("domiciliacion") -> TransactionType.EXPENSE
            lowerMessage.contains("pago") -> TransactionType.EXPENSE

            // Income keywords specific to Santander
            lowerMessage.contains("deposito") -> TransactionType.INCOME

            // Fallback to base class
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Pattern 1: "en MERCHANT_NAME" (for purchases and charges)
        // Stops at date pattern (dd/Mon/yy or dd/mm/yy), "Saldo", "Limite", or end of string
        val enMerchantPattern = Regex(
            """\ben\s+(.+?)(?:\s+\d{2}/\w{3}/\d{2}|\s+\d{2}/\d{2}/\d{2}|\s+[Ss]aldo|\s+[Ll]imite|$)""",
            RegexOption.IGNORE_CASE
        )
        enMerchantPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            val filteredLower = merchant.lowercase()
            if (merchant.isNotEmpty() &&
                !filteredLower.startsWith("tu tarjeta") &&
                !filteredLower.startsWith("tu cuenta") &&
                isValidMerchantName(merchant)
            ) {
                return cleanMerchantName(merchant)
            }
        }

        // Pattern 2: "Domiciliacion cargo $XXX cuenta *XXXX MERCHANT_NAME dd/Mon/yy"
        if (lowerMessage.contains("domiciliacion")) {
            val domPattern = Regex(
                """Domiciliacion\s+cargo\s+\$\s?[0-9,]+(?:\.\d{1,2})?\s+cuenta\s+\*\d{4}\s+(.+?)(?:\s+\d{2}/\w{3}/\d{2}|\s+\d{2}/\d{2}/\d{2}|\s+[Ss]aldo|$)""",
                RegexOption.IGNORE_CASE
            )
            domPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty() && isValidMerchantName(merchant)) {
                    return cleanMerchantName(merchant)
                }
            }
        }

        // Pattern 3: "Pago de servicio $XXX cuenta *XXXX MERCHANT_NAME dd/Mon/yy"
        if (lowerMessage.contains("pago de servicio")) {
            val pagoPattern = Regex(
                """Pago\s+de\s+servicio\s+\$\s?[0-9,]+(?:\.\d{1,2})?\s+cuenta\s+\*\d{4}\s+(.+?)(?:\s+\d{2}/\w{3}/\d{2}|\s+\d{2}/\d{2}/\d{2}|\s+[Ss]aldo|$)""",
                RegexOption.IGNORE_CASE
            )
            pagoPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty() && isValidMerchantName(merchant)) {
                    return cleanMerchantName(merchant)
                }
            }
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractBalance(message: String): BigDecimal? {
        val balancePatterns = listOf(
            // "Saldo disponible $X,XXX.XX"
            Regex(
                """[Ss]aldo\s+disponible\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "Saldo $X,XXX.XX"
            Regex(
                """[Ss]aldo\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in balancePatterns) {
            pattern.find(message)?.let { match ->
                return parseMexicanAmount(match.groupValues[1])
            }
        }

        return super.extractBalance(message)
    }

    override fun extractAvailableLimit(message: String): BigDecimal? {
        // "Limite disponible $X,XXX.XX"
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
