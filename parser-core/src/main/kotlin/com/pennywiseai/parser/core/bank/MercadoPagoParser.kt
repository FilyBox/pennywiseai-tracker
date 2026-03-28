package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Mercado Pago, the most popular digital wallet/payment service
 * in Latin America (Mexico, Argentina, Brazil, Colombia, and others).
 *
 * Handles SMS notifications for payments, received money, QR payments,
 * transfers, card charges, refunds, subscriptions, and withdrawals.
 *
 * Example SMS formats:
 * - Payment:      "Mercado Pago: Pagaste $500.00 a Tienda Ejemplo. Ref: MP123456789"
 * - Received:     "Mercado Pago: Recibiste $1,000.00 de Persona Ejemplo. Tu saldo: $2,500.00"
 * - QR payment:   "Mercado Pago: Pago con QR $350.00 en OXXO. Ref: QR987654321"
 * - Transfer:     "Mercado Pago: Transferiste $2,000.00 a cuenta Mercado Pago. Ref: TR456789012"
 * - Card charge:  "Mercado Pago: Cargo $1,200.00 con tu tarjeta *4321 en MERCADO LIBRE"
 * - Refund:       "Mercado Pago: Devolucion $750.00 por compra en Amazon. Ref: DEV123456"
 * - Subscription: "Mercado Pago: Pago recurrente $99.00 a SPOTIFY cobrado de tu saldo"
 * - Withdrawal:   "Mercado Pago: Retiro $3,000.00 a tu cuenta bancaria *5678"
 *
 * Currency: MXN by default; detects ARS, BRL, COP from message content.
 */
class MercadoPagoParser : BankParser() {

    override fun getBankName() = "Mercado Pago"

    override fun getCurrency() = "MXN"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase().replace(Regex("\\s+"), "")
        return normalized.contains("MERCADOPAGO") ||
                normalized.contains("MERCADO PAGO".replace(" ", "")) ||
                normalized.contains("MELI") ||
                normalized == "MPAGO" ||
                normalized == "MP"
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("codigo de verificacion") ||
            lowerMessage.contains("clave de verificacion")
        ) {
            return false
        }

        // Skip promotional messages
        if (lowerMessage.contains("promocion") ||
            lowerMessage.contains("oferta") ||
            lowerMessage.contains("descuento")
        ) {
            return false
        }

        val transactionKeywords = listOf(
            "pagaste",
            "recibiste",
            "pago con qr",
            "transferiste",
            "cargo",
            "devolucion",
            "retiro",
            "pago recurrente"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Mercado Pago-specific patterns: keyword followed by $ amount
        val amountPatterns = listOf(
            // "Pagaste $500.00", "Recibiste $1,000.00", "Transferiste $2,000.00"
            Regex(
                """(?:Pagaste|Recibiste|Transferiste|Cargo|Devolucion|Retiro)\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "Pago con QR $350.00", "Pago recurrente $99.00"
            Regex(
                """Pago\s+(?:con\s+QR|recurrente)\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // Generic fallback: "$X,XXX.XX" after "Mercado Pago:"
            Regex(
                """Mercado\s+Pago:\s+\S+\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in amountPatterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("pagaste") -> TransactionType.EXPENSE
            lowerMessage.contains("pago con qr") -> TransactionType.EXPENSE
            lowerMessage.contains("transferiste") -> TransactionType.EXPENSE
            lowerMessage.contains("cargo") -> TransactionType.EXPENSE
            lowerMessage.contains("retiro") -> TransactionType.EXPENSE
            lowerMessage.contains("pago recurrente") -> TransactionType.EXPENSE

            lowerMessage.contains("recibiste") -> TransactionType.INCOME
            lowerMessage.contains("devolucion") -> TransactionType.INCOME

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Pattern 1: "por compra en MERCHANT" (refunds)
        val refundMerchantPattern = Regex(
            """por\s+compra\s+en\s+(.+?)(?:\.\s*Ref|\s*$)""",
            RegexOption.IGNORE_CASE
        )
        refundMerchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        // Pattern 2: "a MERCHANT cobrado" (subscriptions)
        val subscriptionMerchantPattern = Regex(
            """(?:Pagaste|Pago\s+recurrente)\s+\$\s?[0-9,]+(?:\.\d{1,2})?\s+a\s+(.+?)\s+cobrado""",
            RegexOption.IGNORE_CASE
        )
        subscriptionMerchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        // Pattern 3: "en MERCHANT" (QR payments and card charges)
        // Stops at period, "Ref:", end of string, or "Limite"
        val enMerchantPattern = Regex(
            """\ben\s+(.+?)(?:\.\s*Ref|\.\s*$|\s*$|\s+Limite)""",
            RegexOption.IGNORE_CASE
        )
        enMerchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        // Pattern 4: "a MERCHANT." or "a MERCHANT. Ref:" (payments and transfers)
        // Excludes "a tu cuenta" and "a cuenta" patterns
        val aMerchantPattern = Regex(
            """(?:Pagaste|Transferiste)\s+\$\s?[0-9,]+(?:\.\d{1,2})?\s+a\s+(.+?)(?:\.\s*Ref|\.\s*Tu|\.\s*$|\s*$)""",
            RegexOption.IGNORE_CASE
        )
        aMerchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            // Skip generic account references
            if (merchant.lowercase().startsWith("tu cuenta") ||
                merchant.lowercase().startsWith("cuenta")
            ) {
                return null
            }
            if (isValidMerchantName(merchant)) return merchant
        }

        // Pattern 5: "de PERSON" (received money)
        val deMerchantPattern = Regex(
            """Recibiste\s+\$\s?[0-9,]+(?:\.\d{1,2})?\s+de\s+(.+?)(?:\.\s*Tu|\.\s*$|\s*$)""",
            RegexOption.IGNORE_CASE
        )
        deMerchantPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        return null
    }

    override fun extractReference(message: String): String? {
        // Pattern: "Ref: XXXXXX" or "Ref:XXXXXX"
        val refPattern = Regex(
            """Ref:\s*([A-Za-z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        refPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        val balancePatterns = listOf(
            // "Tu saldo: $X,XXX.XX"
            Regex(
                """Tu\s+saldo:\s*\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            ),
            // "saldo de $X,XXX.XX"
            Regex(
                """saldo\s+de\s+\$\s?([0-9,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in balancePatterns) {
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

    override fun extractAccountLast4(message: String): String? {
        val accountPatterns = listOf(
            // "tarjeta *1234"
            Regex("""tarjeta\s+\*(\d{4})""", RegexOption.IGNORE_CASE),
            // "cuenta *5678" or "cuenta bancaria *5678"
            Regex("""cuenta\s+(?:bancaria\s+)?\*(\d{4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in accountPatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }

        return null
    }

    override fun extractCurrency(message: String): String? {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("ars") -> "ARS"
            lowerMessage.contains("brl") || lowerMessage.contains("r$") -> "BRL"
            lowerMessage.contains("cop") -> "COP"
            lowerMessage.contains("mxn") -> "MXN"
            else -> null
        }
    }

    override fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("tarjeta") ||
                lowerMessage.contains("tc *") ||
                super.detectIsCard(message)
    }
}
