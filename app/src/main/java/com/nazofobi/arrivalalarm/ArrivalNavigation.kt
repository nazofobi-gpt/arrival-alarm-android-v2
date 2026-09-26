package com.nazofobi.arrivalalarm

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

enum class ArrivalScreen {
    HOME,
    SEARCH,
    ROUTES,
    JOURNEY,
    LIVE,
    STATION,
    SETTINGS,
}

private data class NavigationDestination(
    val screen: ArrivalScreen,
    val labelRes: Int,
    val testTag: String,
)

@Composable
fun ArrivalNavigationBar(
    currentScreen: ArrivalScreen,
    onScreenSelected: (ArrivalScreen) -> Unit,
) {
    val journeySelected = currentScreen in setOf(
        ArrivalScreen.ROUTES,
        ArrivalScreen.JOURNEY,
        ArrivalScreen.LIVE,
    )
    val destinations = listOf(
        NavigationDestination(ArrivalScreen.HOME, R.string.nav_home, "nav-home"),
        NavigationDestination(ArrivalScreen.SEARCH, R.string.nav_search, "nav-search"),
        NavigationDestination(ArrivalScreen.JOURNEY, R.string.nav_journey, "nav-journey"),
        NavigationDestination(ArrivalScreen.STATION, R.string.nav_departures, "nav-departures"),
        NavigationDestination(ArrivalScreen.SETTINGS, R.string.nav_settings, "nav-settings"),
    )

    NavigationBar(modifier = Modifier.testTag("primary-navigation")) {
        destinations.forEach { destination ->
            val selected = if (destination.screen == ArrivalScreen.JOURNEY) {
                journeySelected
            } else {
                currentScreen == destination.screen
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onScreenSelected(destination.screen) },
                icon = { Text("•") },
                label = { Text(stringResource(destination.labelRes)) },
                modifier = Modifier.testTag(destination.testTag),
            )
        }
    }
}
