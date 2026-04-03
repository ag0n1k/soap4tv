package com.soap4tv.app.ui.screen.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soap4tv.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    var username by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun onUsernameChange(value: String) {
        username = value
        error = null
    }

    fun onPasswordChange(value: String) {
        password = value
        error = null
    }

    fun login(onSuccess: () -> Unit) {
        if (username.isBlank() || password.isBlank()) {
            error = "Enter username and password"
            return
        }
        viewModelScope.launch {
            isLoading = true
            error = null
            authRepository.login(username, password)
                .onSuccess { onSuccess() }
                .onFailure { error = it.message }
            isLoading = false
        }
    }
}
