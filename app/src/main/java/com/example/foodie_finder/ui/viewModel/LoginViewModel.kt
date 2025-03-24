package com.example.foodie_finder.ui.viewModel

import androidx.lifecycle.ViewModel
import com.example.foodie_finder.data.model.UserModel

class LoginViewModel : ViewModel() {

    fun signIn(
        email: String,
        password: String,
        callback: (Boolean, String?, List<String>?) -> Unit
    ) {
        UserModel.shared.signIn(email, password, callback)
    }

    fun isUserLoggedIn(): Boolean {
        return UserModel.shared.isUserLoggedIn()
    }
}