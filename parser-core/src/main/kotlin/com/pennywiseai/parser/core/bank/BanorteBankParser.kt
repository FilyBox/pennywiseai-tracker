package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Banorte, the second largest bank in Mexico.
 * Handles SMS notifications for card charges, purchases, withdrawals,
 * SPEI transfers, deposits, payments, and direct debits.
 *
 * Example SMS formats:
 * - Card charge: "Banorte: Se realizo un cargo de $1,500.00 en tu tarjeta terminacion 1234 en WALMART SUPERCENTER 25/Mar/26"
 * - Purchase: "Banorte: Compra con tarjeta *4567 por $350.00 en OXXO 25/Mar/26"
 * - Withdrawal: "Banorte: Retiro de $3,000.00 de tu cuenta *5678 en cajero 25/Mar/26 Saldo $12,000.00"
 * - SPEI transfer: "Banorte: Transferencia SPEI de $2,500.00 de tu cuenta *5678 25/Mar/26 Saldo $9,500.00"
 * - Deposit: "Banorte: Se abono $5,000.00 a tu cuenta *5678 por concepto de NOMINA 25/Mar/26 Saldo $17,000.00"
 * - Payment: "Banorte: Pago de $150.00 con tu tarjeta *1234 en SPOTIFY 25/Mar/26"
 * - Direct debit: "Banorte: Domiciliacion $499.00 de tu cuenta *5678 TELMEX 25/Mar/26 Saldo $11,500.00"
 */
class BanorteBankParser : BaseMexicanBankParser() {

    override fun getBankName() = "Banorte"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("BANORTE")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Banorte-specific patterns: keyword followed by amount
        val banorteAmountPatterns = listOf(
            // "cargo de $1,500.00", "Retiro de $3,000.00", "Transferencia SPEI de $2,500.00", "Pago de $150.00"
            Regex(
                """(?:cargo|retiro|transferencia(?:\s+SPEI)?|pago)\s+de\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "Compra con tarjeta *4567 por $350.00"
            Regex(
                """(?:Compra)\s+.+?por\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "Se abono $5,000.00"
            Regex(
                """(?:Se\s+abono|abono)\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "Domiciliacion $499.00"
            Regex(
                """Domiciliacion\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in banorteAmountPatterns) {
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
            // Expense keywords specific to Banorte
            lowerMessage.contains("cargo") -> TransactionType.EXPENSE
            lowerMessage.contains("compra") -> TransactionType.EXPENSE
            lowerMessage.contains("retiro") -> TransactionType.EXPENSE
            lowerMessage.contains("transferencia") -> TransactionType.EXPENSE
            lowerMessage.contains("pago") -> TransactionType.EXPENSE
            lowerMessage.contains("domiciliacion") -> TransactionType.EXPENSE

            // Income keywords specific to Banorte
            lowerMessage.contains("abono") -> TransactionType.INCOME

            // Fallback to base class
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val lowerMessage = message.lowercase()

        // Pattern 1: "en MERCHANT_NAME" (for purchases, charges, payments)
        // Stops at date pattern (dd/Mon/yy or dd/mm/yy), "Saldo", or end of string
        val enMerchantPattern = Regex(
            """\ben\s+(.+?)(?:\s+\d{2}/\w{3}/\d{2}|\s+\d{2}/\d{2}/\d{2}|\s+[Ss]aldo|$)""",
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

        // Pattern 2: "Domiciliacion $XXX ... MERCHANT_NAME dd/Mon/yy"
        // Direct debit merchant appears after amount and account, before date
        if (lowerMessage.contains("domiciliacion")) {
            val domPattern = Regex(
                """Domiciliacion\s+\$\s?[0-9,]+(?:\.\d{1,2})?\s+de\s+tu\s+cuenta\s+\*\d{4}\s+(.+?)(?:\s+\d{2}/\w{3}/\d{2}|\s+\d{2}/\d{2}/\d{2}|\s+[Ss]aldo|$)""",
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
        // Banorte-specific: "Saldo $X,XXX.XX" at end of message
        val balancePattern = Regex(
            """[Ss]aldo\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            return parseMexicanAmount(match.groupValues[1])
        }

        return super.extractBalance(message)
    }

    override fun extractAccountLast4(message: String): String? {
        val accountPatterns = listOf(
            // "tarjeta terminacion 1234"
            Regex("""tarjeta\s+terminacion\s+(\d{4})""", RegexOption.IGNORE_CASE),
            // "tarjeta *4567"
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
