package com.bellogate_caliphate.chatgpthelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.bellogate_caliphate.chatgpthelper.ui.MainScreen
import com.bellogate_caliphate.chatgpthelper.ui.MainViewModel
import com.bellogate_caliphate.chatgpthelper.ui.theme.ChatGptHelperTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatGptHelperTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAccessibilityStatus()
    }
}
