package com.example.aion_app.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.aion_app.R
import com.example.aion_app.ui.theme.BlueLight
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 유효성 표시 아이콘 (유효=초록 체크 / 에러=회색)
                    if (showValidCheck) {
                        Image(
                            painter = painterResource(
                                if (isError) R.drawable.pw_incorrect else R.drawable.pw_correct
                            ),
                            contentDescription = if (isError) "유효하지 않음" else "유효함",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    // 비밀번호 표시/숨김 토글
                    IconButton(onClick = { visible = !visible }) {
                        Image(
                            painter = painterResource(
                                if (visible) R.drawable.pw_visible else R.drawable.pw_masked
                            ),
                            contentDescription = if (visible) "비밀번호 숨기기" else "비밀번호 보기",
                            modifier = Modifier.size(22.dp)
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