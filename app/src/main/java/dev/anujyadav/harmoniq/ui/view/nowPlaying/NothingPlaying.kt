package dev.anujyadav.harmoniq.ui.view.nowPlaying

import androidx.compose.foundation.layout.Box


import androidx.compose.foundation.layout.fillMaxSize


import androidx.compose.foundation.layout.padding


import androidx.compose.material.icons.Icons


import androidx.compose.material.icons.filled.Headphones


import androidx.compose.material3.Icon


import androidx.compose.material3.Scaffold


import androidx.compose.material3.Text


import androidx.compose.runtime.Composable


import androidx.compose.ui.Modifier


import dev.anujyadav.harmoniq.ui.components.IconTextBody


import dev.anujyadav.harmoniq.ui.helpers.ViewContext

import dev.anujyadav.harmoniq.utils.AppStrings

@Composable
fun NothingPlaying(context: ViewContext) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            NowPlayingAppBar(context)
        },
        content = { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                NothingPlayingBody(context)
            }
        }
    )
}

@Composable
fun NothingPlayingBody(context: ViewContext) {
    IconTextBody(
        icon = { modifier ->
            Icon(
                Icons.Filled.Headphones,
                null,
                modifier = modifier
            )
        },
        content = {
            Text(AppStrings.NothingIsBeingPlayedRightNow)
        }
    )
}
