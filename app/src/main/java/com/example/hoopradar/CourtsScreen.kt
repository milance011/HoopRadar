package com.example.hoopradar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun CourtsScreen() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text("Courts – list of basketball courts")
    }
}