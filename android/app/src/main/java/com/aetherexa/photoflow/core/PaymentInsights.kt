package com.aetherexa.photoflow.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Offline OCR is heuristic evidence, NOT a bank statement or payment verification. */
enum class PaymentProvider(val label: String) {
    GPAY("Google Pay"), PHONEPE("PhonePe"), PAYTM("Paytm"), AMAZON_PAY("Amazon Pay"),
    BHIM("BHIM"), UPI_OTHER("Other UPI"), UNKNOWN("Unknown app")
}
enum class PaymentDirection(val label: String) {
    OUTGOING("Paid"), INCOMING("Received"), UNKNOWN("Direction unknown")
}
enum class PaymentStatus(val label: String) {
    SUCCESS("Successful"), FAILED("Failed"), PENDING("Pending"), UNKNOWN("Unverified")
}

data class PaymentInsight(
    val provider: PaymentProvider,
    val amountPaise: Long?,
    val payee: String?,
    val maskedUpiId: String?,
    val direction: PaymentDirection,
    val status: PaymentStatus,
    val referenceLast4: String?,
    val confidence: Float,
    val needsReview: Boolean
) {
    /** UI indexing never contains raw OCR, complete UPI IDs or full payment references. */
    val searchableTags: Set<String> get() = buildSet {
        add("Payment screenshot")
        add(provider.label)
        add(direction.label)
        add(status.label)
        if (direction == PaymentDirection.OUTGOING) add("Expense screenshot")
        if (direction == PaymentDirection.INCOMING) add("Incoming payment")
        if (needsReview) add("Review payment details")
        amountPaise?.let { add(PaymentInsightParser.formatAmount(it)) }
        payee?.let { add("Payee: $it") }
    }

    /** Only clearly outgoing, confirmed, high-confidence amounts contribute to the estimate. */
    val estimatedExpensePaise: Long get() =
        if (!needsReview && direction == PaymentDirection.OUTGOING &&
            status == PaymentStatus.SUCCESS) amountPaise ?: 0L else 0L
}

/** Pure parser: app identity, payee and amounts are extracted from OCR lines without sending data anywhere. */
object PaymentInsightParser {
    private val money = Regex("""(?i)(?:₹|\bINR|\bRs\.?)\s*([0-9]{1,10}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)""")
    private val payee = Regex("""(?i)^\s*(?:paid\s+to|sent\s+to|transferred\s+to|payee|merchant|received\s+from|from|to)\s*[:\-–]?\s*(.*?)\s*$""")
    private val reference = Regex("""(?i)(?:upi\s*(?:transaction\s*)?(?:ref(?:erence)?|id)|utr|transaction\s*(?:id|ref)|ref(?:erence)?\s*(?:no|id))\s*(?:no\.?|number)?\s*[:#-]?\s*([0-9\s-]{8,25})""")
    private val upi = Regex("""(?i)\b[a-z0-9_.-]{2,}@(okaxis|okhdfcbank|oksbi|okicici|ybl|ibl|axl|upi|paytm|hdfcbank|icici|sbi|axisbank|federal|yesbank)\b""")
    private val financial = Regex("""(?i)\b(upi|transaction|payment|paid to|sent to|received from|debited|credited|utr|ref no|transfer successful)\b""")
    private val success = Regex("""(?i)\b(successful|successfully|completed|payment done|payment made|money sent|transaction success|paid to|transferred to)\b""")
    private val failure = Regex("""(?i)\b(failed|failure|unsuccessful|declined|cancelled|rejected|not successful)\b""")
    private val pending = Regex("""(?i)\b(pending|processing|in progress|awaiting confirmation|initiated)\b""")
    private val inlineOutgoing = Regex("""(?i)\b(?:paid|sent|transferred)\s+(?:₹|inr|rs\.?)\s*[0-9,]+(?:\.[0-9]{1,2})?\s+to\s+(.+)""")
    private val incoming = Regex("""(?i)\b(received from|you received|money received|payment received|amount credited|credited to)\b""")
    private val outgoing = Regex("""(?i)\b(paid to|you paid|sent to|you sent|transferred to|amount debited|debited from|payment sent)\b""")
    private val promo = Regex("""(?i)\b(cashback|discount|coupon|offer|reward|voucher|earn up to|scratch card)\b""")

    fun parse(lines: List<String>): PaymentInsight? {
        val clean = lines.map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }.take(150)
        if (clean.isEmpty()) return null
        val all = clean.joinToString("\n")
        val folded = all.lowercase(Locale.ROOT)
        val provider = when {
            Regex("""\b(phonepe|phone pe)\b""").containsMatchIn(folded) -> PaymentProvider.PHONEPE
            Regex("""\b(google\s*pay|gpay|g\s+pay)\b""").containsMatchIn(folded) -> PaymentProvider.GPAY
            Regex("""\bpaytm\b""").containsMatchIn(folded) -> PaymentProvider.PAYTM
            Regex("""\bamazon\s*pay\b""").containsMatchIn(folded) -> PaymentProvider.AMAZON_PAY
            Regex("""\bbhim\b""").containsMatchIn(folded) -> PaymentProvider.BHIM
            Regex("""\bupi\b""").containsMatchIn(folded) -> PaymentProvider.UPI_OTHER
            else -> PaymentProvider.UNKNOWN
        }
        val hasFinancialCue = financial.containsMatchIn(folded)
        val hasStrongTransaction = success.containsMatchIn(folded) || inlineOutgoing.containsMatchIn(all) ||
            failure.containsMatchIn(folded) ||
            pending.containsMatchIn(folded) || outgoing.containsMatchIn(folded) || incoming.containsMatchIn(folded)
        val amounts = clean.mapIndexedNotNull { index, line ->
            money.find(line)?.groupValues?.getOrNull(1)?.let { raw ->
                parseAmount(raw)?.let { amount ->
                    // Avoid balances, rewards and unrelated promotional figures.
                    val penalized = if (Regex("""(?i)(available|account|wallet)\s+balance|cashback|reward|discount|offer""")
                            .containsMatchIn(line)) -100 else 0
                    val priority = when {
                        Regex("""(?i)\b(total\s+paid|amount\s+paid|transaction amount|amount sent|paid|sent|transferred|received)\b""").containsMatchIn(line) -> 4
                        Regex("""(?i)\b(amount|total)\b""").containsMatchIn(line) -> 3
                        else -> 1
                    }
                    Triple(amount, priority + penalized, index)
                }
            }
        }
        val amount = amounts.sortedWith(compareByDescending<Triple<Long, Int, Int>> { it.second }.thenBy { it.third })
            .firstOrNull { it.second >= 0 }?.first

        // An advertisement or random text mentioning an app and money is not proof of a transaction.
        if (!hasFinancialCue || (!hasStrongTransaction && !reference.containsMatchIn(all)) ||
            (amount == null && provider == PaymentProvider.UNKNOWN)) return null
        if (promo.containsMatchIn(folded) && !hasStrongTransaction && !reference.containsMatchIn(all)) return null

        val direction = when {
            incoming.containsMatchIn(folded) && !outgoing.containsMatchIn(folded) -> PaymentDirection.INCOMING
            (outgoing.containsMatchIn(folded) || inlineOutgoing.containsMatchIn(all)) &&
                !incoming.containsMatchIn(folded) -> PaymentDirection.OUTGOING
            else -> PaymentDirection.UNKNOWN
        }
        val status = when {
            failure.containsMatchIn(folded) -> PaymentStatus.FAILED
            pending.containsMatchIn(folded) -> PaymentStatus.PENDING
            (success.containsMatchIn(folded) || inlineOutgoing.containsMatchIn(all)) && !Regex("""(?i)\b(success rate|successful offers?)\b""").containsMatchIn(folded) -> PaymentStatus.SUCCESS
            else -> PaymentStatus.UNKNOWN
        }
        val recipient = extractPayee(clean, direction)
        val maskedVpa = upi.find(all)?.value?.let { id -> "••••@" + id.substringAfter('@') }
        val refLast4 = reference.find(all)?.groupValues?.getOrNull(1)
            ?.filter(Char::isDigit)?.takeIf { it.length in 8..20 }?.takeLast(4)
        var points = 0
        if (provider != PaymentProvider.UNKNOWN) points++
        if (amount != null) points++
        if (direction != PaymentDirection.UNKNOWN) points++
        if (status != PaymentStatus.UNKNOWN) points++
        if (recipient != null || maskedVpa != null) points++
        if (refLast4 != null) points++
        val confidence = (points / 6f).coerceIn(0f, 1f)
        val needsReview = amount == null || direction == PaymentDirection.UNKNOWN ||
            status != PaymentStatus.SUCCESS || confidence < 0.66f
        return PaymentInsight(provider, amount, recipient, maskedVpa,
            direction, status, refLast4, confidence, needsReview)
    }

    private fun extractPayee(lines: List<String>, direction: PaymentDirection): String? {
        for ((index, line) in lines.withIndex()) {
            val inline = inlineOutgoing.find(line)?.groupValues?.getOrNull(1)
            if (inline != null && direction == PaymentDirection.OUTGOING) {
                sanitizePayee(inline)?.let { return it }
            }
            val match = payee.matchEntire(line) ?: continue
            val prefix = line.substringBefore(match.groupValues[1]).lowercase(Locale.ROOT)
            if (direction == PaymentDirection.INCOMING && !prefix.contains("from")) continue
            if (direction == PaymentDirection.OUTGOING && prefix.contains("from")) continue
            val candidate = match.groupValues[1].ifBlank { lines.getOrNull(index + 1).orEmpty() }
            sanitizePayee(candidate)?.let { return it }
        }
        return null
    }

    private fun sanitizePayee(raw: String): String? {
        val candidate = raw.substringBefore("  ").trim(' ', ':', '-', '–')
            .replace(Regex("""\s*(?:\||\bUPI\b|₹|\bRs\.).*""", RegexOption.IGNORE_CASE), "")
            .take(72).trim()
        if (candidate.length < 2 || '@' in candidate || candidate.all(Char::isDigit) ||
            candidate.startsWith("bank", true) || candidate.startsWith("account", true) ||
            candidate.startsWith("success", true) || candidate.startsWith("transaction", true) ||
            candidate.startsWith("amount", true)) return null
        // A payee name may be followed by an account or mobile number: mask it before indexing.
        return candidate.replace(Regex("""[0-9]{6,}"""), "••••")
    }

    fun parseAmount(raw: String): Long? = try {
        val decimal = BigDecimal(raw.replace(",", "")).setScale(2, RoundingMode.UNNECESSARY)
        decimal.takeIf { it > BigDecimal.ZERO && it <= BigDecimal("100000000") }
            ?.movePointRight(2)?.longValueExact()
    } catch (_: Exception) { null }

    fun formatAmount(paise: Long): String {
        val rupees = paise / 100
        val decimals = (paise % 100).toString().padStart(2, '0')
        val grouped = rupees.toString().reversed().chunked(3).joinToString(",").reversed()
        return "₹$grouped.$decimals"
    }
}
