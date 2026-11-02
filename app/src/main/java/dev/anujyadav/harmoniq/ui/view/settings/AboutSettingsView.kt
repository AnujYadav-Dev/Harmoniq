package dev.anujyadav.harmoniq.ui.view.settings

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.anujyadav.harmoniq.R
import dev.anujyadav.harmoniq.services.AppMeta
import dev.anujyadav.harmoniq.ui.components.IconButtonPlaceholder
import dev.anujyadav.harmoniq.ui.components.TopAppBarMinimalTitle
import dev.anujyadav.harmoniq.ui.components.settings.SettingsLinkTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSideHeading
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSimpleTile
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.ActivityUtils
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            ) {
                AboutHeader()

                SettingsSideHeading(AppStrings.App)
                SettingsSimpleTile(
                    icon = { Icon(Icons.Filled.Info, null) },
                    title = { Text(AppStrings.Version) },
                    subtitle = { Text(AppMeta.version) },
                )
                SettingsSimpleTile(
                    icon = { Icon(Icons.Filled.Build, null) },
                    title = { Text(AppStrings.BuildType) },
                    subtitle = { Text(AppMeta.buildType) },
                )
                SettingsSimpleTile(
                    icon = { Icon(Icons.Filled.Android, null) },
                    title = { Text(AppStrings.PackageName) },
                    subtitle = { Text(context.activity.packageName) },
                )
                UpdateCheckTile(context)

                SettingsSideHeading(AppStrings.Project)
                AboutLinkTile(context, Icons.Filled.Code, AppStrings.SourceCode, AppMeta.githubRepositoryUrl)
                AboutLinkTile(context, Icons.Filled.NewReleases, AppStrings.Releases, AppMeta.githubReleasesUrl)
                AboutLinkTile(context, Icons.Filled.BugReport, AppStrings.ReportAnIssue, AppMeta.githubIssuesUrl)
                AboutLinkTile(
                    context,
                    Icons.Filled.VolunteerActivism,
                    AppStrings.Contributing,
                    AppMeta.contributingUrl,
                )
                AboutLinkTile(
                    context,
                    Icons.Filled.Gavel,
                    "${AppStrings.License} (${AppMeta.licenseName})",
                    AppMeta.licenseUrl,
                )
                AboutLinkTile(context, Icons.Filled.Forum, AppStrings.Discord, AppMeta.discordUrl)
                AboutLinkTile(context, Icons.Filled.Forum, AppStrings.Reddit, AppMeta.redditUrl)
                AboutLinkTile(context, Icons.Filled.Shop, AppStrings.FDroid, AppMeta.fdroidUrl)
                AboutLinkTile(context, Icons.Filled.Shop, AppStrings.IzzyOnDroid, AppMeta.izzyOnDroidUrl)
                AboutLinkTile(context, Icons.Filled.Shop, AppStrings.PlayStore, AppMeta.playStoreUrl)

                SettingsSideHeading(AppStrings.Developer)
                AboutLinkTile(context, Icons.Filled.Person, AppMeta.author, AppMeta.githubProfileUrl)

                SettingsSideHeading(AppStrings.Credits)
                AboutLinkTile(
                    context,
                    Icons.Filled.Favorite,
                    AppStrings.BasedOnXByY(AppMeta.originalProjectName, AppMeta.originalProjectAuthor),
                    AppMeta.originalProjectUrl,
                )
                AboutLinkTile(
                    context,
                    Icons.Filled.LibraryMusic,
                    AppStrings.MetadataPoweredByX(AppMeta.taglibName),
                    AppMeta.taglibUrl,
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    )
}

@Composable
private fun AboutHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
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
            AppMeta.appDescription,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            AppStrings.DevelopedByX(AppMeta.author),
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// links that are not configured in AppMeta are hidden
@Composable
private fun AboutLinkTile(context: ViewContext, icon: ImageVector, title: String, url: String) {
    if (url.isBlank()) {
        return
    }
    SettingsLinkTile(
        context,
        icon = { Icon(icon, null) },
        title = { Text(title) },
        url = url,
    )
}

private sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data object Failed : UpdateCheckState
    data class Available(val version: String) : UpdateCheckState
}

// only runs when tapped, so the app never reaches the network on its own
@Composable
private fun UpdateCheckTile(context: ViewContext) {
    val coroutineScope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }

    SettingsSimpleTile(
        icon = { Icon(Icons.Filled.Update, null) },
        title = { Text(AppStrings.CheckForUpdates) },
        subtitle = {
            Text(
                when (val current = state) {
                    UpdateCheckState.Idle -> AppStrings.TapToCheck
                    UpdateCheckState.Checking -> AppStrings.CheckingForUpdates
                    UpdateCheckState.UpToDate -> AppStrings.UpToDate
                    UpdateCheckState.Failed -> AppStrings.UpdateCheckFailed
                    is UpdateCheckState.Available -> AppStrings.NewVersionAvailableX(current.version)
                }
            )
        },
        onClick = {
            when (state) {
                UpdateCheckState.Checking -> {}
                is UpdateCheckState.Available -> ActivityUtils.startBrowserActivity(
                    context.activity,
                    Uri.parse(AppMeta.githubLatestReleaseUrl),
                )

                else -> coroutineScope.launch {
                    state = UpdateCheckState.Checking
                    val latest = withContext(Dispatchers.IO) {
                        AppMeta.fetchLatestVersion()
                    }
                    state = when {
                        latest == null -> UpdateCheckState.Failed
                        AppMeta.isUpdateAvailable(latest) -> UpdateCheckState.Available(latest)
                        else -> UpdateCheckState.UpToDate
                    }
                }
            }
        },
    )
}
