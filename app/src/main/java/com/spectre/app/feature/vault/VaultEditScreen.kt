package com.spectre.app.feature.vault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spectre.app.core.data.models.*
import com.spectre.app.core.data.repository.VaultRepository
import com.spectre.app.core.security.VaultSession
import com.spectre.app.core.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject


data class EditUiState(
    val id: String                  = "",
    val isNew: Boolean              = true,
    val type: CipherType            = CipherType.LOGIN,
    val name: String                = "",
    val notes: String               = "",
    val favourite: Boolean          = false,
    val reprompt: Boolean           = false,
    val folderId: String?           = null,
    // Login
    val loginUsername: String       = "",
    val loginPassword: String       = "",
    val loginTotp: String           = "",
    val loginUris: List<String>     = listOf(""),
    // Card
    val cardHolder: String          = "",
    val cardBrand: String           = "",
    val cardNumber: String          = "",
    val cardExpMonth: String        = "",
    val cardExpYear: String         = "",
    val cardCode: String            = "",
    // Identity
    val idFirstName: String         = "",
    val idLastName: String          = "",
    val idEmail: String             = "",
    val idPhone: String             = "",
    val idCompany: String           = "",
    val idAddress1: String          = "",
    val idCity: String              = "",
    val idPostalCode: String        = "",
    val idCountry: String           = "",
    val isSaving: Boolean           = false,
    val isLoading: Boolean          = false,
    val error: String?              = null,
    val showPassword: Boolean       = false,
    val generatedPassword: String?  = null,
)

/**
 * ViewModel for adding or editing vault items.
 */
@HiltViewModel
class VaultEditViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val session: VaultSession,
    private val passwordGenerator: com.spectre.app.feature.generator.PasswordGenerator,
    private val prefs: com.spectre.app.core.data.datastore.SpectrePreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val cipherId = savedStateHandle.get<String>("cipherId")
    private val typeArg  = savedStateHandle.get<Int>("type") ?: 1

    private val _state = MutableStateFlow(EditUiState(
        isNew = cipherId == null || cipherId == "new",
        type  = CipherType.fromInt(typeArg),
        isLoading = cipherId != null && cipherId != "new",
    ))
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    init {
        if (cipherId != null && cipherId != "new") {
            viewModelScope.launch {
                vaultRepository.observeById(cipherId).firstOrNull()?.let { populateFromCipher(it) }
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun populateFromCipher(c: DecryptedCipher) {
        _state.update { s -> s.copy(
            id          = c.id,
            isNew       = false,
            type        = c.type,
            name        = c.name,
            notes       = c.notes ?: "",
            favourite   = c.favorite,
            reprompt    = c.reprompt,
            folderId    = c.folderId,
            loginUsername = c.loginData?.username ?: "",
            loginPassword = c.loginData?.password ?: "",
            loginTotp   = c.loginData?.totp ?: "",
            loginUris   = c.loginData?.uris?.map { it.uri ?: "" }?.ifEmpty { listOf("") } ?: listOf(""),
            cardHolder  = c.cardData?.cardholderName ?: "",
            cardBrand   = c.cardData?.brand ?: "",
            cardNumber  = c.cardData?.number ?: "",
            cardExpMonth = c.cardData?.expMonth ?: "",
            cardExpYear  = c.cardData?.expYear ?: "",
            cardCode    = c.cardData?.code ?: "",
            idFirstName = c.identityData?.firstName ?: "",
            idLastName  = c.identityData?.lastName ?: "",
            idEmail     = c.identityData?.email ?: "",
            idPhone     = c.identityData?.phone ?: "",
            idCompany   = c.identityData?.company ?: "",
            idAddress1  = c.identityData?.address1 ?: "",
            idCity      = c.identityData?.city ?: "",
            idPostalCode = c.identityData?.postalCode ?: "",
            idCountry   = c.identityData?.country ?: "",
        ) }
    }

    fun onNameChange(v: String)          = _state.update { it.copy(name = v, error = null) }
    fun onNotesChange(v: String)         = _state.update { it.copy(notes = v) }
    fun onFavouriteToggle()              = _state.update { it.copy(favourite = !it.favourite) }
    fun onRepromptToggle()               = _state.update { it.copy(reprompt = !it.reprompt) }
    fun onToggleShowPassword()           = _state.update { it.copy(showPassword = !it.showPassword) }
    fun onLoginUsernameChange(v: String) = _state.update { it.copy(loginUsername = v) }
    fun onLoginPasswordChange(v: String) = _state.update { it.copy(loginPassword = v) }
    fun onLoginTotpChange(v: String)     = _state.update { it.copy(loginTotp = v) }
    fun onCardHolderChange(v: String)    = _state.update { it.copy(cardHolder = v) }
    fun onCardBrandChange(v: String)     = _state.update { it.copy(cardBrand = v) }
    fun onCardNumberChange(v: String)    = _state.update { it.copy(cardNumber = v) }
    fun onCardExpMonthChange(v: String)  = _state.update { it.copy(cardExpMonth = v) }
    fun onCardExpYearChange(v: String)   = _state.update { it.copy(cardExpYear = v) }
    fun onCardCodeChange(v: String)      = _state.update { it.copy(cardCode = v) }
    fun onIdFirstNameChange(v: String)   = _state.update { it.copy(idFirstName = v) }
    fun onIdLastNameChange(v: String)    = _state.update { it.copy(idLastName = v) }
    fun onIdEmailChange(v: String)       = _state.update { it.copy(idEmail = v) }
    fun onIdPhoneChange(v: String)       = _state.update { it.copy(idPhone = v) }
    fun onIdCompanyChange(v: String)     = _state.update { it.copy(idCompany = v) }
    fun onIdAddress1Change(v: String)    = _state.update { it.copy(idAddress1 = v) }
    fun onIdCityChange(v: String)        = _state.update { it.copy(idCity = v) }
    fun onIdPostalCodeChange(v: String)  = _state.update { it.copy(idPostalCode = v) }
    fun onIdCountryChange(v: String)     = _state.update { it.copy(idCountry = v) }
    fun onUriChange(index: Int, v: String) = _state.update { s ->
        s.copy(loginUris = s.loginUris.toMutableList().also { it[index] = v })
    }
    fun addUri() = _state.update { it.copy(loginUris = it.loginUris + "") }
    fun removeUri(index: Int) = _state.update { s ->
        s.copy(loginUris = s.loginUris.toMutableList().also { if (it.size > 1) it.removeAt(index) })
    }

    fun generatePassword() {
        viewModelScope.launch {
            val settings = prefs.settings.first()
            val config = com.spectre.app.feature.generator.GeneratorConfig(
                length = settings.defaultGeneratorLength,
                useUppercase = settings.defaultGeneratorUppercase,
                useLowercase = settings.defaultGeneratorLowercase,
                useNumbers = settings.defaultGeneratorNumbers,
                useSymbols = settings.defaultGeneratorSymbols,
            )
            val result = passwordGenerator.generate(config)
            _state.update { it.copy(loginPassword = result.value, showPassword = true) }
        }
    }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) { _state.update { it.copy(error = "Name is required.") }; return }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }

            val accountId = session.activeAccountId ?: run {
                _state.update { it.copy(isSaving = false, error = "No active account.") }
                return@launch
            }

            val cipher = DecryptedCipher(
                id             = if (s.isNew) UUID.randomUUID().toString() else s.id,
                accountId      = accountId,
                organizationId = null,
                folderId       = s.folderId,
                type           = s.type,
                name           = s.name,
                notes          = s.notes.takeIf { it.isNotBlank() },
                favorite       = s.favourite,
                reprompt       = s.reprompt,
                deletedDate    = null,
                revisionDate   = java.time.Instant.now().toString(),
                creationDate   = null,
                loginData      = if (s.type == CipherType.LOGIN) LoginData(
                    username             = s.loginUsername.takeIf { it.isNotBlank() },
                    password             = s.loginPassword.takeIf { it.isNotBlank() },
                    passwordRevisionDate = null,
                    totp                 = s.loginTotp.takeIf { it.isNotBlank() },
                    uris                 = s.loginUris.filter { it.isNotBlank() }.map { LoginUri(it) },
                ) else null,
                cardData = if (s.type == CipherType.CARD) CardData(
                    cardholderName = s.cardHolder.takeIf { it.isNotBlank() },
                    brand          = s.cardBrand.takeIf { it.isNotBlank() },
                    number         = s.cardNumber.takeIf { it.isNotBlank() },
                    expMonth       = s.cardExpMonth.takeIf { it.isNotBlank() },
                    expYear        = s.cardExpYear.takeIf { it.isNotBlank() },
                    code           = s.cardCode.takeIf { it.isNotBlank() },
                ) else null,
                identityData = if (s.type == CipherType.IDENTITY) IdentityData(
                    title = null, firstName = s.idFirstName.takeIf { it.isNotBlank() },
                    middleName = null, lastName = s.idLastName.takeIf { it.isNotBlank() },
                    address1 = s.idAddress1.takeIf { it.isNotBlank() }, address2 = null, address3 = null,
                    city = s.idCity.takeIf { it.isNotBlank() }, state = null,
                    postalCode = s.idPostalCode.takeIf { it.isNotBlank() },
                    country = s.idCountry.takeIf { it.isNotBlank() },
                    company = s.idCompany.takeIf { it.isNotBlank() },
                    email = s.idEmail.takeIf { it.isNotBlank() },
                    phone = s.idPhone.takeIf { it.isNotBlank() },
                    ssn = null, username = null, passportNumber = null, licenseNumber = null,
                ) else null,
            )

            val result = if (s.isNew) vaultRepository.createCipher(cipher)
                         else vaultRepository.updateCipher(cipher)

            result.fold(
                onSuccess = { _saved.emit(Unit) },
                onFailure = { _state.update { st -> st.copy(isSaving = false, error = it.message) } },
            )
        }
    }
}

/**
 * Screen for entering and saving vault item details.
 */
@Composable
fun VaultEditScreen(
    cipherId: String?,
    type: Int,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    vm: VaultEditViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.saved.collect { onSaved() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) "New ${state.type.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }}"
                        else "Edit Item",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "Cancel") } },
                actions = {
                    TextButton(
                        onClick  = vm::save,
                        enabled  = !state.isSaving,
                    ) {
                        if (state.isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Save", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Common fields
                EditCard {
                    SpectreTextField("Name *", state.name, vm::onNameChange, leadingIcon = Icons.AutoMirrored.Filled.Label)
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.onFavouriteToggle() }.fillMaxWidth()) {
                            Checkbox(state.favourite, { vm.onFavouriteToggle() })
                            Text("Mark as favourite", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.onRepromptToggle() }.fillMaxWidth()) {
                            Checkbox(state.reprompt, { vm.onRepromptToggle() })
                            Text("Re-prompt master password", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Type-specific fields
                when (state.type) {
                    CipherType.LOGIN       -> LoginEditSection(state, vm)
                    CipherType.CARD        -> CardEditSection(state, vm)
                    CipherType.IDENTITY    -> IdentityEditSection(state, vm)
                    CipherType.SECURE_NOTE -> Unit
                }

                // Notes
                EditCard {
                    SpectreTextField(
                        label  = "Notes",
                        value  = state.notes,
                        onChange = vm::onNotesChange,
                        leadingIcon = Icons.AutoMirrored.Filled.Notes,
                        singleLine = false,
                        minLines   = 3,
                    )
                }

                // Error
                state.error?.let { err ->
                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LoginEditSection(state: EditUiState, vm: VaultEditViewModel) {
    EditCard {
        SectionHeader(title = "Login", modifier = Modifier.padding(0.dp))
        Spacer(Modifier.height(8.dp))
        SpectreTextField("Username / Email", state.loginUsername, vm::onLoginUsernameChange, Icons.Filled.Person, keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value         = state.loginPassword,
            onValueChange = vm::onLoginPasswordChange,
            label         = { Text("Password") },
            leadingIcon   = { Icon(Icons.Filled.Lock, null) },
            trailingIcon  = {
                Row {
                    IconButton(onClick = vm::onToggleShowPassword) {
                        Icon(if (state.showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                    }
                    IconButton(onClick = { vm.generatePassword() }) {
                        Icon(Icons.Filled.Casino, "Generate")
                    }
                }
            },
            visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        SpectreTextField("Authenticator Key (TOTP)", state.loginTotp, vm::onLoginTotpChange, Icons.Filled.QrCode)
    }

    // URIs
    EditCard {
        SectionHeader(title = "Website URIs", modifier = Modifier.padding(0.dp))
        Spacer(Modifier.height(4.dp))
        state.loginUris.forEachIndexed { index, uri ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value         = uri,
                    onValueChange = { vm.onUriChange(index, it) },
                    label         = { Text("URI ${index + 1}") },
                    leadingIcon   = { Icon(Icons.Filled.Link, null) },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                if (state.loginUris.size > 1) {
                    IconButton(onClick = { vm.removeUri(index) }) {
                        Icon(Icons.Filled.RemoveCircleOutline, "Remove URI", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        TextButton(onClick = vm::addUri, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add URI")
        }
    }
}

@Composable
private fun CardEditSection(state: EditUiState, vm: VaultEditViewModel) {
    EditCard {
        SectionHeader(title = "Card Details", modifier = Modifier.padding(0.dp))
        Spacer(Modifier.height(8.dp))
        SpectreTextField("Cardholder Name", state.cardHolder, vm::onCardHolderChange, Icons.Filled.Person)
        Spacer(Modifier.height(8.dp))
        SpectreTextField("Brand", state.cardBrand, vm::onCardBrandChange, Icons.Filled.CreditCard)
        Spacer(Modifier.height(8.dp))
        SpectreTextField("Card Number", state.cardNumber, vm::onCardNumberChange, Icons.Filled.Numbers, keyboardType = KeyboardType.Number)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(state.cardExpMonth, vm::onCardExpMonthChange, label = { Text("Exp. Month") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(state.cardExpYear, vm::onCardExpYearChange, label = { Text("Exp. Year") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Spacer(Modifier.height(8.dp))
        SpectreTextField("Security Code (CVV)", state.cardCode, vm::onCardCodeChange, Icons.Filled.Lock, keyboardType = KeyboardType.Number)
    }
}

@Composable
private fun IdentityEditSection(state: EditUiState, vm: VaultEditViewModel) {
    EditCard {
        SectionHeader(title = "Identity", modifier = Modifier.padding(0.dp))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(state.idFirstName, vm::onIdFirstNameChange, label = { Text("First Name") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true)
            OutlinedTextField(state.idLastName, vm::onIdLastNameChange, label = { Text("Last Name") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true)
        }
        Spacer(Modifier.height(8.dp))
        SpectreTextField("Email", state.idEmail, vm::onIdEmailChange, Icons.Filled.Email, keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(8.dp))
        SpectreTextField("Phone", state.idPhone, vm::onIdPhoneChange, Icons.Filled.Phone, keyboardType = KeyboardType.Phone)
        Spacer(Modifier.height(8.dp))
        SpectreTextField("Company", state.idCompany, vm::onIdCompanyChange, Icons.Filled.Business)
        Spacer(Modifier.height(8.dp))
        SpectreTextField("Address", state.idAddress1, vm::onIdAddress1Change, Icons.Filled.Home)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(state.idCity, vm::onIdCityChange, label = { Text("City") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true)
            OutlinedTextField(state.idPostalCode, vm::onIdPostalCodeChange, label = { Text("Postcode") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true)
        }
        Spacer(Modifier.height(8.dp))
        SpectreTextField("Country", state.idCountry, vm::onIdCountryChange, Icons.Filled.Public)
    }
}

@Composable
private fun EditCard(content: @Composable ColumnScope.() -> Unit) {
    SpectreCard(modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun SpectreTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    val isMultiLine = minLines > 1 || !singleLine
    OutlinedTextField(
        value           = value,
        onValueChange   = onChange,
        label           = { Text(label) },
        leadingIcon     = leadingIcon?.let {
            {
                if (isMultiLine) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Icon(it, null)
                    }
                } else {
                    Icon(it, null)
                }
            }
        },
        singleLine      = singleLine,
        minLines        = minLines,
        modifier        = Modifier
            .fillMaxWidth()
            .then(if (isMultiLine) Modifier.height(IntrinsicSize.Min) else Modifier),
        shape           = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}


