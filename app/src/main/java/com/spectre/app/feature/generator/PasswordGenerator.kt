package com.spectre.app.feature.generator

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

data class GeneratorConfig(
    val mode: GeneratorMode     = GeneratorMode.PASSWORD,
    val length: Int             = 16,
    val useUppercase: Boolean   = true,
    val useLowercase: Boolean   = true,
    val useNumbers: Boolean     = true,
    val useSymbols: Boolean     = true,
    val avoidAmbiguous: Boolean = false,
    val minUppercase: Int       = 1,
    val minLowercase: Int       = 1,
    val minNumbers: Int         = 1,
    val minSymbols: Int         = 0,
    val customSymbols: String   = "!@#\$%^&*",
    // Passphrase
    val wordCount: Int          = 4,
    val separator: String       = "-",
    val capitalizeWords: Boolean = false,
    val includeNumber: Boolean  = false,
)

enum class GeneratorMode { PASSWORD, PASSPHRASE, USERNAME }

data class GeneratedResult(
    val value: String,
    val strength: Int,   // 0-4
    val config: GeneratorConfig,
)

@Singleton
class PasswordGenerator @Inject constructor() {

    companion object {
        private const val UPPERCASE   = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val LOWERCASE   = "abcdefghijklmnopqrstuvwxyz"
        private const val NUMBERS     = "0123456789"
        private const val SYMBOLS     = "!@#\$%^&*"
        private const val AMBIGUOUS   = "0Ol1I"
        private val WORDLIST          = EFF_LARGE_WORDLIST
    }

    fun generate(config: GeneratorConfig): GeneratedResult {
        val value = when (config.mode) {
            GeneratorMode.PASSWORD   -> generatePassword(config)
            GeneratorMode.PASSPHRASE -> generatePassphrase(config)
            GeneratorMode.USERNAME   -> generateUsername()
        }
        return GeneratedResult(
            value    = value,
            strength = calculateStrength(value, config),
            config   = config,
        )
    }

    private fun generatePassword(config: GeneratorConfig): String {
        val charset = buildString {
            if (config.useUppercase) append(if (config.avoidAmbiguous) UPPERCASE.filter { it !in AMBIGUOUS } else UPPERCASE)
            if (config.useLowercase) append(if (config.avoidAmbiguous) LOWERCASE.filter { it !in AMBIGUOUS } else LOWERCASE)
            if (config.useNumbers)   append(if (config.avoidAmbiguous) NUMBERS.filter  { it !in AMBIGUOUS } else NUMBERS)
            if (config.useSymbols)   append(config.customSymbols)
        }

        if (charset.isEmpty()) return generatePassword(config.copy(useLowercase = true))

        val mandatory = buildList {
            repeat(config.minUppercase) { add(UPPERCASE.random()) }
            repeat(config.minLowercase) { add(LOWERCASE.random()) }
            repeat(config.minNumbers)   { add(NUMBERS.random()) }
            repeat(config.minSymbols)   { add(config.customSymbols.random()) }
        }.toMutableList()

        val remaining = config.length - mandatory.size
        if (remaining > 0) {
            repeat(remaining) { mandatory.add(charset.random()) }
        }

        return mandatory.shuffled().joinToString("")
    }

    private fun generatePassphrase(config: GeneratorConfig): String {
        val words = (0 until config.wordCount).map {
            val word = WORDLIST.random()
            if (config.capitalizeWords) word.replaceFirstChar { it.uppercase() } else word
        }.toMutableList()

        if (config.includeNumber) {
            val pos = (0 until words.size).random()
            words[pos] = words[pos] + Random.nextInt(10, 99).toString()
        }

        return words.joinToString(config.separator)
    }

    private fun generateUsername(): String {
        val adjectives = listOf("swift", "quiet", "brave", "dark", "silver", "hidden", "silent", "cipher")
        val nouns      = listOf("raven", "wolf", "ghost", "spectre", "shadow", "phantom", "cipher", "nexus")
        val adj  = adjectives.random()
        val noun = nouns.random()
        val num  = Random.nextInt(10, 999)
        return "$adj$noun$num"
    }

    private fun calculateStrength(password: String, config: GeneratorConfig): Int {
        if (config.mode == GeneratorMode.PASSPHRASE) {
            return when {
                config.wordCount >= 6 -> 4
                config.wordCount >= 4 -> 3
                config.wordCount >= 3 -> 2
                else                  -> 1
            }
        }
        var score = 0
        if (password.length >= 8)  score++
        if (password.length >= 12) score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return minOf(score - 1, 4).coerceAtLeast(0)
    }
}

// 100-word EFF short wordlist subset (full list is 7776 words; this is an illustrative sample)
private val EFF_LARGE_WORDLIST = listOf(
    "abacus","abdomen","abide","abject","ablaze","aboard","abode","abort","abound","abrasive",
    "abyss","acclaim","acorn","acre","acrid","actor","acute","adhere","adobe","adorn",
    "adult","affirm","afloat","afraid","agenda","agile","agony","agree","ajar","alarm",
    "alcove","alert","algae","alias","almond","aloft","alpine","alter","amber","amble",
    "ambush","amend","ample","anchor","angel","ankle","annex","anvil","apart","apex",
    "apply","apron","arbor","arcade","ardent","ardor","arena","arise","armor","aroma",
    "arrow","arson","ascend","ashen","aside","aspen","assert","asset","atlas","atone",
    "attic","audio","augur","avid","avoid","await","awoke","azure","badge","barge",
    "baron","beach","beady","beard","beast","bedew","begun","beige","bench","bevel",
    "birch","blade","bland","blaze","bleat","blend","bless","bliss","block","bloom",
    "blown","blunt","blurb","board","boggy","bolts","boost","border","bound","brave",
    "braze","bread","brine","broil","brood","brunt","brush","budget","build","bumpy",
    "burst","cable","camel","candy","canyon","cape","cargo","cedar","chair","chalk",
    "charm","chess","chief","chime","civic","clash","clean","clear","climb","cloak",
    "close","cloud","clove","coast","comet","coral","craft","crane","crave","crisp"
)
