package com.example.aion_app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.aion_app.ui.theme.BlueLight
import com.example.aion_app.ui.theme.GreenSuccess
import com.example.aion_app.ui.theme.RedError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AionPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isError: Boolean = false,
    errorMessage: String? = null,
    helperText: String? = "영문, 숫자 포함 8자 이상 입력해 주세요.",
    showValidCheck: Boolean = false,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = isError,
            trailingIcon = {
                Row {
                    if (showValidCheck && !isError) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "유효함",
                            tint = GreenSuccess
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BlueLight,
                unfocusedContainerColor = BlueLight,
                errorContainerColor = BlueLight,
                focusedBorderColor = if (isError) RedError else Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                errorBorderColor = RedError
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isError && errorMessage != null) errorMessage else (helperText ?: ""),
            color = if (isError) RedError else Color.Gray,
            style = MaterialTheme.typography.labelSmall
        )
    }
}