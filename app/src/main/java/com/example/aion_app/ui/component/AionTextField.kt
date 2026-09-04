package com.example.aion_app.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.aion_app.ui.theme.GreyLightHover
import com.example.aion_app.ui.theme.RedError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = GreyLightHover,
            unfocusedContainerColor = GreyLightHover,
            errorContainerColor = GreyLightHover,
            focusedBorderColor = if (isError) RedError else Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            errorBorderColor = RedError
        )
    )
}