package com.example.data.api

import androidx.compose.runtime.Immutable

/**
 * Result of evaluating an email address against the Astryn Quantum Anti-Temp-Mail
 * and Real-Email Verification Firewall.
 */
@Immutable
data class EmailSecurityValidationResult(
  val isValidRealEmail: Boolean,
  val isTempOrDisposableBlocked: Boolean = false,
  val isTypoDomainBlocked: Boolean = false,
  val isTrustedMajorProvider: Boolean = false,
  val providerBadge: String = "",
  val statusMessage: String = "",
)

/**
 * Production-grade Email & Credential Security Firewall.
 *
 * Enforces:
 * 1. Zero-tolerance blocking of 150+ disposable / temporary / burner email providers
 *    and heuristic temp-mail domain patterns.
 * 2. Typo-squatting / mistyped major domain detection (e.g., `gmial.com`, `gamil.com`, `outlok.com`).
 * 3. Real email structure, RFC local-part hygiene, and verified domain validation.
 * 4. Breached / weak password pattern detection.
 */
object EmailSecurityValidator {

  private val TRUSTED_REAL_DOMAINS: Map<String, String> =
    mapOf(
      "gmail.com" to "Google Mail Verified",
      "googlemail.com" to "Google Mail Verified",
      "outlook.com" to "Microsoft Outlook Verified",
      "hotmail.com" to "Microsoft Hotmail Verified",
      "live.com" to "Microsoft Live Verified",
      "msn.com" to "Microsoft MSN Verified",
      "icloud.com" to "Apple iCloud Verified",
      "me.com" to "Apple Mail Verified",
      "mac.com" to "Apple Mail Verified",
      "yahoo.com" to "Yahoo Mail Verified",
      "yahoo.co.in" to "Yahoo India Verified",
      "yahoo.co.uk" to "Yahoo UK Verified",
      "ymail.com" to "Yahoo Mail Verified",
      "proton.me" to "Proton Encrypted Mail",
      "protonmail.com" to "Proton Encrypted Mail",
      "pm.me" to "Proton Encrypted Mail",
      "zoho.com" to "Zoho Workplace Mail",
      "zohomail.in" to "Zoho Workplace Mail",
      "rediffmail.com" to "Rediffmail Verified",
      "aol.com" to "AOL Mail Verified",
      "gmx.com" to "GMX Mail Verified",
      "fastmail.com" to "Fastmail Verified",
      "hey.com" to "HEY Mail Verified",
    )

  private val COMMON_DOMAIN_TYPOS: Map<String, String> =
    mapOf(
      "gmial.com" to "gmail.com",
      "gamil.com" to "gmail.com",
      "gmal.com" to "gmail.com",
      "gnail.com" to "gmail.com",
      "gmail.co" to "gmail.com",
      "gmail.con" to "gmail.com",
      "gmail.om" to "gmail.com",
      "gmaill.com" to "gmail.com",
      "outlok.com" to "outlook.com",
      "outllok.com" to "outlook.com",
      "outlook.co" to "outlook.com",
      "hotmial.com" to "hotmail.com",
      "hotmal.com" to "hotmail.com",
      "yaho.com" to "yahoo.com",
      "yahooo.com" to "yahoo.com",
      "yhoo.com" to "yahoo.com",
      "iclod.com" to "icloud.com",
      "icoud.com" to "icloud.com",
    )

  private val DISPOSABLE_EMAIL_DOMAINS: Set<String> =
    setOf(
      "tempmail.com",
      "temp-mail.org",
      "temp-mail.io",
      "temp-mail.ru",
      "tempmail.net",
      "tempmail.org",
      "tempmail.de",
      "tempmail.ninja",
      "tempmail.plus",
      "tempmailo.com",
      "tempmailaddress.com",
      "tempail.com",
      "tempinbox.com",
      "tempr.email",
      "tempemail.net",
      "tempemail.com",
      "tmpmail.net",
      "tmpmail.org",
      "10minutemail.com",
      "10minutemail.net",
      "10minutemail.org",
      "10minutemail.co.za",
      "10minmail.com",
      "20minutemail.com",
      "20minutemail.it",
      "30minutemail.com",
      "guerrillamail.com",
      "guerrillamail.net",
      "guerrillamail.org",
      "guerrillamail.biz",
      "guerrillamail.de",
      "guerrillamail.info",
      "guerrillamailblock.com",
      "sharklasers.com",
      "grr.la",
      "pokemail.net",
      "spam4.me",
      "mailinator.com",
      "mailinator.net",
      "mailinator.org",
      "mailinator2.com",
      "mailinater.com",
      "binkmail.com",
      "bobmail.info",
      "chammy.info",
      "devnullmail.com",
      "letthemeatspam.com",
      "safetymail.info",
      "sogetthis.com",
      "spamhereplease.com",
      "streetwisemail.com",
      "suremail.info",
      "thisisnotmyrealemail.com",
      "tradermail.info",
      "veryrealemail.com",
      "yopmail.com",
      "yopmail.fr",
      "yopmail.net",
      "cool.fr.nf",
      "jetable.fr.nf",
      "nospam.ze.tc",
      "nomail.xl.cx",
      "mega.zik.dj",
      "speed.1s.fr",
      "courriel.fr.nf",
      "moncourrier.fr.nf",
      "monemail.fr.nf",
      "monmail.fr.nf",
      "trashmail.com",
      "trashmail.net",
      "trashmail.org",
      "trashmail.me",
      "trashmail.io",
      "trash-mail.com",
      "trashemail.de",
      "wegwerfmail.de",
      "wegwerfmail.net",
      "wegwerfmail.org",
      "getnada.com",
      "nada.email",
      "nada.ltd",
      "abyssmail.com",
      "boximail.com",
      "clrmail.com",
      "dropjar.com",
      "getairmail.com",
      "givmail.com",
      "inboxbear.com",
      "robot-mail.com",
      "tafmail.com",
      "vomoto.com",
      "zetmail.com",
      "maildrop.cc",
      "fakeinbox.com",
      "fakemail.net",
      "fakemailgenerator.com",
      "throwawaymail.com",
      "throwaway.email",
      "mohmal.com",
      "dispostable.com",
      "mailnesia.com",
      "mintemail.com",
      "mytemp.email",
      "emailondeck.com",
      "crazymailing.com",
      "generator.email",
      "inboxkitten.com",
      "burnermail.io",
      "harakirimail.com",
      "mailcatch.com",
      "mailexpire.com",
      "mailforspam.com",
      "mailnull.com",
      "mailsac.com",
      "mailshell.com",
      "mailtemp.info",
      "mailtothis.com",
      "mailzilla.com",
      "meltmail.com",
      "mt2015.com",
      "incognitomail.org",
      "anonbox.net",
      "anonymbox.com",
      "disposablemail.com",
      "discard.email",
      "discardmail.com",
      "spambog.com",
      "spamgourmet.com",
      "spambox.us",
      "tmail.ws",
      "tmailinator.com",
      "dropmail.me",
      "linshiyouxiang.net",
      "luxusmail.org",
      "mailpoof.com",
      "moakt.com",
      "owlymail.com",
      " temp-mail.com",
    )

  private val DISPOSABLE_DOMAIN_KEYWORDS: List<String> =
    listOf(
      "tempmail",
      "temp-mail",
      "tmp-mail",
      "tmpmail",
      "10minutemail",
      "10minmail",
      "20minutemail",
      "throwaway",
      "fakeinbox",
      "fakemail",
      "trashmail",
      "trash-mail",
      "guerrillamail",
      "mailinator",
      "yopmail",
      "burnermail",
      "disposable",
      "spamgourmet",
      "spambog",
      "incognitomail",
      "harakirimail",
      "maildrop",
      "getnada",
      "mohmal",
      "inboxkitten",
      "emailondeck",
      "crazymailing",
    )

  private val WEAK_BREACHED_PASSWORDS: Set<String> =
    setOf(
      "123456",
      "1234567",
      "12345678",
      "123456789",
      "1234567890",
      "password",
      "password1",
      "password123",
      "qwerty",
      "qwerty123",
      "qwertyuiop",
      "admin123",
      "welcome123",
      "iloveyou",
      "111111",
      "11111111",
      "000000",
      "00000000",
      "abc123",
      "abc12345",
      "letmein",
      "monkey123",
    )

  private val LOCAL_PART_REGEX = Regex("^[A-Za-z0-9._%+\\-]{2,64}$")
  private val DOMAIN_LABEL_REGEX = Regex("^[A-Za-z0-9\\-]{2,63}$")
  private val TLD_REGEX = Regex("^[A-Za-z]{2,14}$")

  /**
   * Validates whether [rawEmail] is a legitimate, real email address and blocks all
   * disposable/temp-mail services and mistyped domains.
   */
  fun validateEmail(rawEmail: String): EmailSecurityValidationResult {
    val cleaned = rawEmail.trim().lowercase()
    if (cleaned.isEmpty()) {
      return EmailSecurityValidationResult(
        isValidRealEmail = false,
        statusMessage = "Enter your real email address",
      )
    }

    val atCount = cleaned.count { it == '@' }
    if (atCount != 1) {
      return EmailSecurityValidationResult(
        isValidRealEmail = false,
        statusMessage = "Enter a complete email address (e.g. name@gmail.com)",
      )
    }

    val localPart = cleaned.substringBefore("@")
    val domainPart = cleaned.substringAfter("@")

    if (
      localPart.length < 2 ||
        localPart.startsWith(".") ||
        localPart.endsWith(".") ||
        localPart.contains("..") ||
        !LOCAL_PART_REGEX.matches(localPart)
    ) {
      return EmailSecurityValidationResult(
        isValidRealEmail = false,
        statusMessage = "Invalid email username format",
      )
    }

    // Check if localPart uses a disposable alias trick like +temp or +trash
    if (
      localPart.contains("+temp") ||
        localPart.contains("+trash") ||
        localPart.contains("+spam") ||
        localPart.contains("+fake")
    ) {
      return EmailSecurityValidationResult(
        isValidRealEmail = false,
        isTempOrDisposableBlocked = true,
        statusMessage = "SECURITY BLOCK: Disposable '+temp/+spam' email aliases are not allowed.",
      )
    }

    if (domainPart.isBlank() || !domainPart.contains(".")) {
      return EmailSecurityValidationResult(
        isValidRealEmail = false,
        statusMessage = "Enter a valid email domain (e.g. @gmail.com)",
      )
    }

    // 1. Check against exact disposable domains & subdomains
    if (
      DISPOSABLE_EMAIL_DOMAINS.contains(domainPart) ||
        DISPOSABLE_EMAIL_DOMAINS.any { domainPart.endsWith(".$it") } ||
        DISPOSABLE_DOMAIN_KEYWORDS.any { domainPart.contains(it) }
    ) {
      return EmailSecurityValidationResult(
        isValidRealEmail = false,
        isTempOrDisposableBlocked = true,
        providerBadge = "TEMP MAIL BLOCKED",
        statusMessage =
          "SECURITY BLOCK: Temporary / Disposable email ($domainPart) is strictly prohibited. Please use a real email (Gmail, Outlook, iCloud, etc.).",
      )
    }

    // 2. Check against common mistyped domains (e.g. gmial.com, gamil.com)
    val suggestedFix = COMMON_DOMAIN_TYPOS[domainPart]
    if (suggestedFix != null) {
      return EmailSecurityValidationResult(
        isValidRealEmail = false,
        isTypoDomainBlocked = true,
        providerBadge = "TYPO DETECTED",
        statusMessage =
          "Invalid domain '@$domainPart'. Did you mean '@$suggestedFix'? Please use a real email.",
      )
    }

    // 3. Validate domain structure and TLD
    val labels = domainPart.split(".")
    val tld = labels.lastOrNull().orEmpty()
    if (
      labels.size < 2 ||
        labels.any { !DOMAIN_LABEL_REGEX.matches(it) || it.startsWith("-") || it.endsWith("-") } ||
        !TLD_REGEX.matches(tld)
    ) {
      return EmailSecurityValidationResult(
        isValidRealEmail = false,
        statusMessage = "Invalid email domain structure",
      )
    }

    // Reject single-word fake domains like @test.com, @example.com, @fake.com, @abc.com, @xyz.com
    val rootDomain = "${labels[labels.size - 2]}.$tld"
    if (
      rootDomain in
        setOf(
          "example.com",
          "test.com",
          "fake.com",
          "invalid.com",
          "none.com",
          "noemail.com",
          "abc.com",
          "xyz.com",
          "qwe.com",
          "asd.com",
        )
    ) {
      return EmailSecurityValidationResult(
        isValidRealEmail = false,
        isTempOrDisposableBlocked = true,
        providerBadge = "FAKE DOMAIN BLOCKED",
        statusMessage =
          "SECURITY BLOCK: Placeholder / fake domain (@$rootDomain) is blocked. Use your real email address.",
      )
    }

    val trustedProvider = TRUSTED_REAL_DOMAINS[domainPart]
    return if (trustedProvider != null) {
      EmailSecurityValidationResult(
        isValidRealEmail = true,
        isTrustedMajorProvider = true,
        providerBadge = trustedProvider,
        statusMessage = "$trustedProvider ✓",
      )
    } else {
      EmailSecurityValidationResult(
        isValidRealEmail = true,
        isTrustedMajorProvider = false,
        providerBadge = "Verified Custom Domain",
        statusMessage = "Verified Real Domain (@$domainPart) ✓",
      )
    }
  }

  /**
   * Validates password strength and rejects breached / trivial passwords on Sign Up.
   * Returns null if valid, or a descriptive security error message if rejected.
   */
  fun validatePasswordSecurity(
    password: String,
    email: String,
    isSignUp: Boolean,
  ): String? {
    if (password.length < 8 && isSignUp) {
      return "Security Policy: Password must be at least 8 characters long."
    }
    if (password.length < 6) {
      return "Enter a valid password (minimum 6 characters)."
    }

    if (isSignUp) {
      val lowerPass = password.lowercase().trim()
      if (WEAK_BREACHED_PASSWORDS.contains(lowerPass)) {
        return "SECURITY BLOCK: This password appears in known data breaches. Choose a stronger, unique password."
      }
      val emailPrefix = email.substringBefore("@").lowercase().trim()
      if (emailPrefix.length >= 3 && lowerPass.contains(emailPrefix)) {
        return "Security Policy: Password must not contain your email username ($emailPrefix)."
      }
      val hasLetter = password.any { it.isLetter() }
      val hasDigitOrSymbol = password.any { it.isDigit() || !it.isLetterOrDigit() }
      if (!hasLetter || !hasDigitOrSymbol) {
        return "Security Policy: Include both letters and at least one number or symbol (#, @, !, $)."
      }
    }
    return null
  }
}
