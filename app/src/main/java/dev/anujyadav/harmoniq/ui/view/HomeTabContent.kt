package dev.anujyadav.harmoniq.ui.view

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import dev.anujyadav.harmoniq.services.groove.Groove
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.home.ForYouView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabContent(context: ViewContext) {
    val isUpdating by context.symphony.groove.exposer.isUpdating.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isUpdating,
        onRefresh = { context.symphony.groove.fetch(Groove.FetchOptions()) },
        modifier = Modifier.fillMaxSize(),
        state = pullRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isUpdating,
                state = pullRefreshState,
                color = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    ) {
        ForYouView(context)
    }
}
