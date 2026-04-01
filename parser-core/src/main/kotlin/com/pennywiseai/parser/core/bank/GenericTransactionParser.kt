package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Generic fallback parser for transaction SMS from unknown senders.
 *
 * This parser uses broad multilingual patterns to detect transaction messages
 * from banks, digital wallets, payment services, subscription platforms, and
 * other financial services that don't yet have a dedicated parser.
 *
 * It handles:
 * - English transaction keywords (debited, credited, charged, paid, etc.)
 * - Spanish transaction keywords (cargo, compra, pago, transferencia, etc.)
 * - Multi-currency amounts ($, €, £, ₹, Rs, USD, EUR, MXN, etc.)
 * - Common merchant extraction patterns
 * - Subscription/recurring charge notifications
 * - Digital wallet notifications (Apple Pay, Samsung Pay, etc.)
 *
 * This parser should be registered LAST in BankParserFactory as a catch-all.
 */
class GenericTransactionParser : BankParser() {

    override fun getBankName() = "Unknown"

    /**
     * Accepts any sender - this is the catch-all fallback parser.
     * BankParserFactory tries specific parsers first; this one is last.
     */
    override fun canHandle(sender: String): Boolean = true

    override fun getCurrency(): String = "USD"

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        if (!isTransactionMessage(smsBody)) {
            return null
        }

        val amount = extractAmount(smsBody) ?: return null
        val type = extractTransactionType(smsBody) ?: return null

        val rawAccountLast4 = extractAccountLast4(smsBody)
        val safeAccountLast4 = rawAccountLast4?.let { extractLast4Digits(it) } ?: rawAccountLast4

        val currency = extractCurrencyFromMessage(smsBody) ?: "USD"

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = extractMerchant(smsBody, sender),
            reference = extractReference(smsBody),
            accountLast4 = safeAccountLast4,
            balance = extractBalance(smsBody),
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = "Unknown",
            isFromCard = detectIsCard(smsBody),
            currency = currency
        )
    }

    // =========================================================================
    // Transaction detection
    // =========================================================================

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP / verification messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code") ||
            lowerMessage.contains("codigo de verificacion") ||
            lowerMessage.contains("clave de verificacion") ||
            lowerMessage.contains("nip dinamico") ||
            lowerMessage.contains("pin code")
        ) {
            return false
        }

        // Skip promotional messages
        if (lowerMessage.contains("win ") ||
            lowerMessage.contains("sorteo") ||
            lowerMessage.contains("congratulations! you") ||
            (lowerMessage.contains("offer") && !lowerMessage.contains("charged")) ||
            (lowerMessage.contains("promocion") && !lowerMessage.contains("cargo"))
        ) {
            return false
        }

        // Skip payment request / reminder messages
        if (lowerMessage.contains("has requested") ||
            lowerMessage.contains("payment request") ||
            lowerMessage.contains("collect request") ||
            lowerMessage.contains("is due") ||
            lowerMessage.contains("min amount due") ||
            lowerMessage.contains("is overdue") ||
            lowerMessage.contains("ignore if paid")
        ) {
            return false
        }

        // Must contain at least one transaction keyword (multilingual)
        return TRANSACTION_KEYWORDS.any { lowerMessage.contains(it) }
    }

    // =========================================================================
    // Amount extraction (multi-currency)
    // =========================================================================

    override fun extractAmount(message: String): BigDecimal? {
        for (pattern in AMOUNT_PATTERNS) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1]
                    .replace(",", "")
                    .replace(" ", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        return null
    }

    // =========================================================================
    // Transaction type detection
    // =========================================================================

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Expense indicators (English)
        val expenseKeywordsEn = listOf(
            "debited", "withdrawn", "spent", "charged", "paid",
            "purchase", "deducted", "payment of", "sent"
        )

        // Expense indicators (Spanish)
        val expenseKeywordsEs = listOf(
            "cargo", "compra", "retiro", "pago", "pagaste",
            "domiciliacion", "transferiste", "spei enviado",
            "compraste"
        )

        // Income indicators (English)
        val incomeKeywordsEn = listOf(
            "credited", "deposited", "received", "refund",
            "cashback"
        )

        // Income indicators (Spanish)
        val incomeKeywordsEs = listOf(
            "abono", "deposito", "recibiste", "devolucion",
            "reembolso"
        )

        // Subscription / renewal (always expense)
        if (lowerMessage.contains("subscription") ||
            lowerMessage.contains("renewed") ||
            lowerMessage.contains("recurring") ||
            lowerMessage.contains("pago recurrente") ||
            lowerMessage.contains("suscripcion")
        ) {
            return TransactionType.EXPENSE
        }

        // Check income first so refund beats paid
        if (incomeKeywordsEn.any { lowerMessage.contains(it) }) {
            return TransactionType.INCOME
        }
        if (incomeKeywordsEs.any { lowerMessage.contains(it) }) {
            return TransactionType.INCOME
        }

        if (expenseKeywordsEn.any { lowerMessage.contains(it) }) {
            return TransactionType.EXPENSE
        }
        if (expenseKeywordsEs.any { lowerMessage.contains(it) }) {
            return TransactionType.EXPENSE
        }

        return null
    }

    // =========================================================================
    // Merchant extraction
    // =========================================================================

    override fun extractMerchant(message: String, sender: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            pattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                val cleaned = cleanMerchantName(merchant)
                if (isValidMerchantName(cleaned)) {
                    return cleaned
                }
            }
        }
        return null
    }

    // =========================================================================
    // Balance extraction
    // =========================================================================

    override fun extractBalance(message: String): BigDecimal? {
        for (pattern in BALANCE_PATTERNS) {
            pattern.find(message)?.let { match ->
                val balanceStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(balanceStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        return null
    }

    // =========================================================================
    // Reference extraction
    // =========================================================================

    override fun extractReference(message: String): String? {
        for (pattern in REFERENCE_PATTERNS) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    // =========================================================================
    // Account extraction
    // =========================================================================

    override fun extractAccountLast4(message: String): String? {
        for (pattern in ACCOUNT_PATTERNS) {
            pattern.find(message)?.let { match ->
                val raw = match.groupValues[1]
                val digits = raw.filter { it.isDigit() }.takeLast(4)
                if (digits.length >= 3) return digits
            }
        }
        return super.extractAccountLast4(message)
    }

    // =========================================================================
    // Currency detection
    // =========================================================================

    private fun extractCurrencyFromMessage(message: String): String? {
        for ((pattern, currency) in CURRENCY_PATTERNS) {
            if (pattern.containsMatchIn(message)) {
                return currency
            }
        }
        return null
    }

    // =========================================================================
    // Compiled patterns
    // =========================================================================

    companion object {

        /**
         * Multilingual transaction keywords used to decide if a message
         * is a financial transaction notification.
         */
        private val TRANSACTION_KEYWORDS = listOf(
            // English
            "debited", "credited", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid",
            "charged", "purchase", "deducted", "refund",
            "payment of", "sent",
            "subscription", "renewed", "recurring",
            // Spanish
            "cargo", "compra", "abono", "deposito",
            "retiro", "transferencia", "pago", "recibiste",
            "domiciliacion", "spei", "pagaste", "compraste",
            "transferiste", "devolucion", "reembolso",
            "pago recurrente", "suscripcion",
            // French
            "debit", "credit", "virement", "paiement",
            "retrait", "versement",
            // Portuguese
            "debito", "credito", "transferencia", "pagamento",
            "saque", "deposito",
            // German
            "abbuchung", "gutschrift", "uberweisung", "zahlung"
        )

        /**
         * Multi-currency amount patterns, ordered from most specific to generic.
         */
        private val AMOUNT_PATTERNS = listOf(
            // Specific currency-prefixed amounts
            Regex("""(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:USD|US\$)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:EUR|€)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:GBP|£)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:MXN)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:AED|SAR|ARS|BRL|COP|KES)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // Dollar sign (generic - many currencies use $)
            Regex("""\$\s?([0-9,]+(?:\.\d{1,2})?)"""),
            // Amount after transaction keywords
            Regex("""(?:debited|credited|charged|paid|spent|sent|received|purchase|deducted)\s+(?:for\s+)?(?:Rs\.?|INR|₹|USD|\$|EUR|€|GBP|£|MXN|AED|SAR)?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // Spanish keyword + amount
            Regex("""(?:cargo|compra|pago|retiro|transferencia|deposito|abono)\s+(?:de\s+|por\s+)?\$?\s?([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        )

        /**
         * Merchant extraction patterns, ordered by specificity.
         */
        private val MERCHANT_PATTERNS = listOf(
            // "at MERCHANT" (English)
            Regex("""\bat\s+([^.\n]+?)(?:\s+on\s+|\s+Ref|\s+UPI|\.\s|$)""", RegexOption.IGNORE_CASE),
            // "to MERCHANT" (English)
            Regex("""\bto\s+([^.\n]+?)(?:\s+on\s+|\s+Ref|\s+UPI|\.\s|$)""", RegexOption.IGNORE_CASE),
            // "from MERCHANT" (English)
            Regex("""\bfrom\s+([^.\n]+?)(?:\s+on\s+|\s+Ref|\s+UPI|\.\s|$)""", RegexOption.IGNORE_CASE),
            // "en MERCHANT" (Spanish - at/in)
            Regex("""\ben\s+([^.\n]+?)(?:\s+\d{2}/|\s+Saldo|\s+Limite|$)""", RegexOption.IGNORE_CASE),
            // "a MERCHANT" (Spanish - to) after payment keyword
            Regex("""(?:pagaste|transferiste|pago)\s+.+?\s+a\s+([^.\n]+?)(?:\.\s|$)""", RegexOption.IGNORE_CASE),
            // "for MERCHANT" (subscription/service)
            Regex("""\bfor\s+([^.\n]+?)(?:\s+on|\s+at|\s+Ref|\.\s|$)""", RegexOption.IGNORE_CASE),
            // Subscription names: "subscription to MERCHANT"
            Regex("""subscription\s+to\s+([^.\n]+?)(?:\s+\$|\s+has|\.\s|$)""", RegexOption.IGNORE_CASE)
        )

        /**
         * Balance patterns (multilingual, multi-currency).
         */
        private val BALANCE_PATTERNS = listOf(
            Regex("""(?:Bal|Balance|Avl Bal|Available Balance|Saldo|Saldo disponible)[:\s]+(?:Rs\.?|INR|₹|USD|\$|EUR|€|GBP|£|MXN)?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:Updated Balance|Remaining Balance)[:\s]+(?:Rs\.?|INR|₹|USD|\$|EUR|€|GBP|£|MXN)?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Tu\s+saldo:\s*\$?\s?([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        )

        /**
         * Reference / transaction ID patterns.
         */
        private val REFERENCE_PATTERNS = listOf(
            Regex("""(?:Ref|Reference|Txn|Transaction)(?:\s+No)?[:\s#]+([A-Z0-9]+)""", RegexOption.IGNORE_CASE),
            Regex("""UPI[:\s]+([0-9]+)""", RegexOption.IGNORE_CASE),
            Regex("""Order\s*#?\s*([A-Z0-9.:-]+)""", RegexOption.IGNORE_CASE)
        )

        /**
         * Account / card last-4 patterns.
         */
        private val ACCOUNT_PATTERNS = listOf(
            Regex("""(?:card|tarjeta|cuenta|account|a/c|acct)[\s*]+\*{0,4}(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""(?:ending|terminacion|terminaci.n)\s+(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""\*{2,}(\d{4})"""),
            Regex("""(?:tc|cc|dc)\s+\*(\d{4})""", RegexOption.IGNORE_CASE)
        )

        /**
         * Currency detection patterns mapped to ISO codes.
         * Ordered from most specific to least specific.
         * The bare `$` symbol defaults to USD as a fallback; specific
         * currency-prefixed patterns (MXN, ARS, COP, BRL) are checked first.
         */
        private val CURRENCY_PATTERNS = listOf(
            Regex("""(?:Rs\.?|₹|INR)""", RegexOption.IGNORE_CASE) to "INR",
            Regex("""MXN""", RegexOption.IGNORE_CASE) to "MXN",
            Regex("""(?:€|EUR)""", RegexOption.IGNORE_CASE) to "EUR",
            Regex("""(?:£|GBP)""", RegexOption.IGNORE_CASE) to "GBP",
            Regex("""AED""", RegexOption.IGNORE_CASE) to "AED",
            Regex("""SAR""", RegexOption.IGNORE_CASE) to "SAR",
            Regex("""KES""", RegexOption.IGNORE_CASE) to "KES",
            Regex("""ARS""", RegexOption.IGNORE_CASE) to "ARS",
            Regex("""BRL""", RegexOption.IGNORE_CASE) to "BRL",
            Regex("""COP""", RegexOption.IGNORE_CASE) to "COP",
            Regex("""(?:USD|US\$)""", RegexOption.IGNORE_CASE) to "USD",
            Regex("""\$""") to "USD"
        )
    }
}
