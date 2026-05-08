package com.spectre.app.core.domain

import com.spectre.app.core.data.models.CipherType
import com.spectre.app.core.data.models.DecryptedCipher
import com.spectre.app.feature.watchtower.ExtendedWatchtowerReport
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchtowerUseCase @Inject constructor() {

    private val sitesSupporting2fa = setOf(
        "google.com", "github.com", "facebook.com", "twitter.com", "microsoft.com",
        "apple.com", "amazon.com", "dropbox.com", "slack.com", "discord.com",
        "reddit.com", "linkedin.com", "instagram.com", "coinbase.com", "binance.com",
        "paypal.com", "stripe.com", "gitlab.com", "bitbucket.org", "heroku.com"
    )

    fun analyze(
        settings: com.spectre.app.core.data.datastore.SpectreSettings,
        allCiphers: List<DecryptedCipher>,
        ignoredItems: List<com.spectre.app.core.data.database.entities.IgnoredWatchtowerItemEntity>
    ): ExtendedWatchtowerReport {
        val logins = allCiphers.filter { it.type == CipherType.LOGIN && !it.isInTrash }
        val cards  = allCiphers.filter { it.type == CipherType.CARD && !it.isInTrash }

        fun isIgnored(cipherId: String, type: String) = ignoredItems.any { it.cipherId == cipherId && it.issueType == type }

        // Password analysis
        val passwords = logins.mapNotNull { it.loginData?.password?.takeIf { p -> p.isNotBlank() } }
        val pwCounts = passwords.groupingBy { it }.eachCount()

        val weakPasswords = if (settings.watchtowerScanWeak) {
            logins.filter { isWeak(it.loginData?.password) && !isIgnored(it.id, "weak") }
        } else emptyList()

        val reusedPasswords = if (settings.watchtowerScanReused) {
            logins.filter { (pwCounts[it.loginData?.password] ?: 0) > 1 && !it.loginData?.password.isNullOrBlank() && !isIgnored(it.id, "reused") }
        } else emptyList()

        val oldPasswords = logins.filter { isOlderThan(it.loginData?.passwordRevisionDate, 365) && !isIgnored(it.id, "old") }
        
        val noTotp = if (settings.watchtowerScan2fa) {
            logins.filter { it.loginData?.totp.isNullOrBlank() && !isIgnored(it.id, "no_totp") }
        } else emptyList()

        // Inactive 2FA detection
        val inactive2fa = noTotp.filter { cipher ->
            val domain = cipher.loginData?.uris?.firstOrNull()?.uri?.let { extractDomain(it) }
            sitesSupporting2fa.contains(domain) && !isIgnored(cipher.id, "inactive_2fa")
        }

        val insecureUris = logins.filter { c -> 
            c.loginData?.uris?.any { it.uri?.startsWith("http://") == true } == true && !isIgnored(c.id, "insecure_uri") 
        }

        val incompleteItems = logins.filter { c ->
            val ld = c.loginData
            (ld == null || ld.password.isNullOrBlank() || ld.uris.isEmpty()) && !isIgnored(c.id, "incomplete")
        }

        // Card expiry
        val expiredCards = cards.filter { isCardExpired(it.cardData) && !isIgnored(it.id, "expired") }

        // Score calculation
        val issues = (weakPasswords.size * 10) +
                (reusedPasswords.size * 5) +
                (inactive2fa.size * 8) +
                (insecureUris.size * 3) +
                (expiredCards.size * 8)
        
        val score = maxOf(0, 100 - issues)

        return ExtendedWatchtowerReport(
            weakPasswords = weakPasswords,
            reusedPasswords = reusedPasswords,
            oldPasswords = oldPasswords,
            noTotp = noTotp,
            insecureUris = insecureUris,
            incompleteItems = incompleteItems,
            expiredCards = expiredCards,
            totalScore = score
        )
    }

    private fun isWeak(p: String?): Boolean {
        if (p == null || p.length < 8) return true
        val hasUpper = p.any { it.isUpperCase() }
        val hasLower = p.any { it.isLowerCase() }
        val hasDigit = p.any { it.isDigit() }
        return !(hasUpper && hasLower && hasDigit)
    }

    private fun isOlderThan(dateStr: String?, days: Int): Boolean {
        if (dateStr == null) return false
        return try {
            val date = Instant.parse(dateStr)
            val cutoff = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
            date.isBefore(cutoff)
        } catch (_: Exception) { false }
    }

    private fun isCardExpired(card: com.spectre.app.core.data.models.CardData?): Boolean {
        if (card == null) return false
        val month = card.expMonth?.toIntOrNull() ?: return false
        val year = card.expYear?.toIntOrNull() ?: return false
        val now = java.time.YearMonth.now()
        val cardExpiry = java.time.YearMonth.of(year, month)
        return cardExpiry.isBefore(now)
    }

    private fun extractDomain(uri: String): String {
        return try {
            val clean = uri.removePrefix("http://").removePrefix("https://").removePrefix("androidapp://")
            clean.split("/").first().split(":").first().lowercase().removePrefix("www.")
        } catch (_: Exception) { "" }
    }
}
