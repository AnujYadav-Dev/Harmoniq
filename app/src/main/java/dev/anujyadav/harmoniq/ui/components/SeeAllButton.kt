package dev.anujyadav.harmoniq.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A trailing "see all" link placed below a section that only shows a few items.
 */
@Composable
fun SeeAllButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onClick) {
            Text(label)
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        }
    }
}
