package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.text.Normalizer

/**
 * Base abstract class for Mexican bank parsers.
 * Handles common patterns across Mexican banks (MXN currency, Spanish-language
 * transaction keywords, peso amount formats, etc.).
 *
 * Mexican banking SMS messages typically use Spanish keywords such as "compra",
 * "cargo", "abono", "deposito", "retiro", "transferencia", and "pago".
 * Amounts use the format $1,234.56 (dollar sign, commas for thousands,
 * period for decimals) or "MXN 1,234.56".
 *
 * All keyword matching normalizes diacritics so that both accented
 * (e.g., "depósito") and unaccented (e.g., "deposito") SMS text is handled.
 */
abstract class BaseMexicanBankParser : BankParser() {

    override fun getCurrency() = "MXN"

    /**
     * Overrides the base parse to also extract available limit for EXPENSE-type
     * card transactions. Mexican banks include "Limite disponible" for credit card
     * charges that use EXPENSE type (not CREDIT type like Indian banks).
     */
    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val result = super.parse(smsBody, sender, timestamp) ?: return null

        // If the base parse didn't extract a credit limit but this is a card transaction,
        // try to extract it now (base only extracts for CREDIT type)
        if (result.creditLimit == null && result.isFromCard) {
            val limit = extractAvailableLimit(smsBody)
            if (limit != null) {
                return result.copy(creditLimit = limit)
            }
        }

        return result
    }

    /**
     * Strips diacritical marks from Spanish text so keyword matching works
     * regardless of whether the SMS contains accented characters.
     * For example, "depósito" becomes "deposito" and "domiciliación" becomes "domiciliacion".
     */
    private fun stripDiacritics(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}"), "")
    }

    /**
     * Checks if the message is a transaction message using Spanish banking keywords.
     * Filters out OTP, promotional, and payment request messages before checking
     * for Mexican transaction indicators.
     */
    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = stripDiacritics(message.lowercase())

        // Skip OTP messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("clave de verificacion") ||
            lowerMessage.contains("codigo de verificacion") ||
            lowerMessage.contains("nip dinamico")
        ) {
            return false
        }

        // Skip promotional messages
        if (lowerMessage.contains("promocion") ||
            lowerMessage.contains("oferta") ||
            lowerMessage.contains("descuento") ||
            lowerMessage.contains("sorteo")
        ) {
            return false
        }

        // Mexican transaction keywords in Spanish (unaccented for normalized matching)
        val transactionKeywords = listOf(
            "compra",
            "cargo",
            "abono",
            "deposito",
            "retiro",
            "transferencia",
            "pago",
            "recibiste",
            "domiciliacion",
            "spei"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }

    /**
     * Extracts the transaction type from Spanish-language banking keywords.
     * Maps Mexican banking terms to [TransactionType] values.
     */
    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = stripDiacritics(message.lowercase())

        return when {
            // Expense keywords
            lowerMessage.contains("compra") -> TransactionType.EXPENSE
            lowerMessage.contains("compraste") -> TransactionType.EXPENSE
            lowerMessage.contains("cargo") -> TransactionType.EXPENSE
            lowerMessage.contains("retiro") -> TransactionType.EXPENSE
            lowerMessage.contains("pago") -> TransactionType.EXPENSE
            lowerMessage.contains("pagaste") -> TransactionType.EXPENSE
            lowerMessage.contains("domiciliacion") -> TransactionType.EXPENSE
            lowerMessage.contains("transferiste") -> TransactionType.EXPENSE

            // Income keywords
            lowerMessage.contains("abono") -> TransactionType.INCOME
            lowerMessage.contains("deposito") -> TransactionType.INCOME
            lowerMessage.contains("recibiste") -> TransactionType.INCOME
            lowerMessage.contains("devolucion") -> TransactionType.INCOME
            lowerMessage.contains("reembolso") -> TransactionType.INCOME

            else -> super.extractTransactionType(message)
        }
    }

    /**
     * Extracts the transaction amount from Mexican peso formats.
     * Handles patterns like "$1,234.56", "MXN 1,234.56", and "$1234.56".
     */
    override fun extractAmount(message: String): BigDecimal? {
        val amountPatterns = listOf(
            // "MXN 1,234.56" or "MXN 1234.56"
            Regex("""MXN\s+([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "$1,234.56" or "$1234.56" (dollar sign prefix)
            Regex("""\$\s?([0-9,]+(?:\.\d{1,2})?)"""),
            // "monto de 1,234.56" or "por 1,234.56"
            Regex("""(?:monto\s+de|por)\s+\$?\s?([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in amountPatterns) {
            pattern.find(message)?.let { match ->
                return parseMexicanAmount(match.groupValues[1])
            }
        }

        return null
    }

    /**
     * Extracts the balance from Mexican banking SMS messages.
     * Handles patterns like "Saldo $1,234.56", "Saldo disponible $1,234.56",
     * and "Saldo: $1,234.56".
     */
    override fun extractBalance(message: String): BigDecimal? {
        val balancePatterns = listOf(
            // "Saldo disponible $1,234.56" or "Saldo disponible: $1,234.56"
            Regex("""[Ss]aldo\s+disponible:?\s*\$?\s?([0-9,]+(?:\.\d{1,2})?)"""),
            // "Saldo $1,234.56" or "Saldo: $1,234.56"
            Regex("""[Ss]aldo:?\s*\$?\s?([0-9,]+(?:\.\d{1,2})?)""")
        )

        for (pattern in balancePatterns) {
            pattern.find(message)?.let { match ->
                return parseMexicanAmount(match.groupValues[1])
            }
        }

        return null
    }

    /**
     * Extracts the available credit limit from Mexican banking SMS.
     * Handles patterns like "Limite disponible $X,XXX.XX" and "Limite disp. $X,XXX.XX".
     */
    override fun extractAvailableLimit(message: String): BigDecimal? {
        val limitPatterns = listOf(
            // "Limite disponible $X,XXX.XX"
            Regex("""[Ll]imite\s+disponible\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "Limite disp. $X,XXX.XX"
            Regex("""[Ll]imite\s+disp\.?\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in limitPatterns) {
            pattern.find(message)?.let { match ->
                return parseMexicanAmount(match.groupValues[1])
            }
        }

        return super.extractAvailableLimit(message)
    }

    /**
     * Extracts the last 4 digits of the account or card number.
     * Handles Mexican banking patterns like "*1234", "terminacion 1234",
     * "****1234", and "tarjeta *1234".
     */
    override fun extractAccountLast4(message: String): String? {
        val accountPatterns = listOf(
            // "terminacion 1234" or "terminación 1234"
            Regex("""terminaci[oó]n\s+(\d{4})""", RegexOption.IGNORE_CASE),
            // "tarjeta *1234" or "tarjeta **1234"
            Regex("""tarjeta\s+\*+(\d{4})""", RegexOption.IGNORE_CASE),
            // "cuenta *1234" or "cuenta **1234"
            Regex("""cuenta\s+\*+(\d{4})""", RegexOption.IGNORE_CASE),
            // "****1234" or "**1234"
            Regex("""\*{2,}(\d{4})"""),
            // "*1234" (single asterisk followed by 4 digits)
            Regex("""\*(\d{4})\b""")
        )

        for (pattern in accountPatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        return super.extractAccountLast4(message)
    }

    /**
     * Cleans merchant name by removing common Mexican banking suffixes and noise.
     */
    override fun cleanMerchantName(merchant: String): String {
        var cleaned = super.cleanMerchantName(merchant)

        // Remove common Mexican merchant suffixes
        cleaned = cleaned
            .replace(Regex("""\s*S\.?A\.?\s*DE\s*C\.?V\.?\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*S\.?A\.?P\.?I\.?\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*S\.?\s*DE\s*R\.?L\.?\s*$""", RegexOption.IGNORE_CASE), "")
            .trim()

        return cleaned
    }

    /**
     * Parses a Mexican-format amount string to [BigDecimal].
     * Handles formats like "1,234.56", "1234.56", and "1234".
     * Removes commas used as thousands separators.
     *
     * @param amountStr the raw amount string (e.g., "1,234.56")
     * @return parsed [BigDecimal] or null if the format is invalid
     */
    protected fun parseMexicanAmount(amountStr: String): BigDecimal? {
        val cleaned = amountStr.replace(",", "")
        return try {
            BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            null
        }
    }
}
