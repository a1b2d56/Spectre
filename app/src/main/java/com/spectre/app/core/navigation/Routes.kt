package com.spectre.app.core.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Route {
    @Serializable
    data object Vault : Route()

    @Serializable
    data class VaultDetail(val cipherId: String) : Route()

    @Serializable
    data class VaultEdit(val cipherId: String? = null, val type: Int = 1) : Route()

    @Serializable
    data object Generator : Route()

    @Serializable
    data object Watchtower : Route()

    @Serializable
    data object Send : Route()

    @Serializable
    data object Settings : Route()

    @Serializable
    data object About : Route()

    @Serializable
    data object Auth : Route()

    @Serializable
    data class Login(
        val serverLabel: String = "Bitwarden",
        val serverUrl: String = "https://api.bitwarden.com",
        val identityUrl: String = "https://identity.bitwarden.com",
        val useCustomServer: Boolean = false
    ) : Route()

    @Serializable
    data class Unlock(val accountId: String) : Route()

    @Serializable
    data object CreateLocalVault : Route()

    @Serializable
    data object KeePassLogin : Route()
}
