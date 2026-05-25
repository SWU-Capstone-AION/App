package com.example.aion_app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aion_app.ui.theme.BluePrimary
import com.example.aion_app.ui.theme.GrayBackground
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White

@Composable
fun AionPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) BluePrimary else GrayBackground,
            contentColor = if (isPrimary) White else TextPrimary,
            disabledContainerColor = BluePrimary.copy(alpha = 0.6f),
            disabledContentColor = White
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(text = text)
    }
}