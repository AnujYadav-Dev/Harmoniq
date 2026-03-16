package dev.anujyadav.harmoniq.ui.components

import androidx.compose.foundation.layout.Box


import androidx.compose.foundation.layout.padding


import androidx.compose.material3.Text


import androidx.compose.material3.TextButton


import androidx.compose.runtime.Composable


import androidx.compose.ui.Modifier


import androidx.compose.ui.unit.dp


import dev.anujyadav.harmoniq.ui.helpers.ViewContext

import dev.anujyadav.harmoniq.utils.AppStrings

@Composable
fun ConfirmationDialog(
    context: ViewContext,
    title: @Composable () -> Unit,
    description: @Composable () -> Unit,
    onResult: (Boolean) -> Unit,
) {
    ScaffoldDialog(
        onDismissRequest = { onResult(false) },
        title = title,
        content = {
            Box(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                )
            ) {
                description()
            }
        },
        actions = {
            TextButton(onClick = { onResult(false) }) {
                Text(AppStrings.No)
            }
            TextButton(onClick = { onResult(true) }) {
                Text(AppStrings.Yes)
            }
        }
    )
}
