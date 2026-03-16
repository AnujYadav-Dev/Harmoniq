package dev.anujyadav.harmoniq.ui.view.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.anujyadav.harmoniq.R
import dev.anujyadav.harmoniq.services.AppMeta
import dev.anujyadav.harmoniq.ui.components.IconButtonPlaceholder
import dev.anujyadav.harmoniq.ui.components.TopAppBarMinimalTitle
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.serialization.Serializable

@Serializable
object AboutSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsView(context: ViewContext) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text(AppStrings.About)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            context.navController.popBackStack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButtonPlaceholder()
                },
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                AsyncImage(
                    R.drawable.ic_launcher_foreground,
                    null,
                    modifier = Modifier.size(96.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    AppMeta.appName,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    AppMeta.version,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    ),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "A modern, local music player.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Developed by ${AppMeta.author}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    ),
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    )
}
