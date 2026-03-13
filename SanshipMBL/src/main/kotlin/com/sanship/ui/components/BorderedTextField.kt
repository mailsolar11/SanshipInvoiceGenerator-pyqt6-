package com.sanship.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// This component creates the "Box with a Title" look used in shipping forms
@Composable
fun BorderedSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .border(1.dp, Color.Black) // The black border
            .padding(4.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            ),
            modifier = Modifier.padding(bottom = 2.dp)
        )
        // This box takes up the remaining space for the input
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            content()
        }
    }
}

// This is the actual typing area
@Composable
fun SimpleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(fontSize = 12.sp, color = Color.Black),
        modifier = modifier.fillMaxSize()
    )
}