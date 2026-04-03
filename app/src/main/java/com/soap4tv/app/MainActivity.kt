package com.soap4tv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.soap4tv.app.data.model.AuthState
import com.soap4tv.app.ui.navigation.AppNavigation
import com.soap4tv.app.ui.screen.login.AuthViewModel
import com.soap4tv.app.ui.theme.Soap4TvTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Soap4TvTheme {
                val authViewModel: AuthViewModel = hiltViewModel()
                val authState by authViewModel.authState.collectAsState()
                // Wait for session check before navigating
                if (authState != AuthState.Unknown) {
                    AppNavigation(authState = authState)
                }
            }
        }
    }
}
