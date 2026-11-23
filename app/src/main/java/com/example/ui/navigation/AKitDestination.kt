package com.example.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R

enum class AKitDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    POWER(
        route = "power",
        labelRes = R.string.nav_power,
        selectedIcon = Icons.Rounded.Bolt,
        unselectedIcon = Icons.Outlined.Bolt,
        testTag = "nav_tab_power",
    ),
    SNAPCLEAN(
        route = "snapclean",
        labelRes = R.string.nav_snapclean,
        selectedIcon = Icons.Rounded.AutoDelete,
        unselectedIcon = Icons.Outlined.AutoDelete,
        testTag = "nav_tab_snapclean",
    ),
    DROP(
        route = "drop",
        labelRes = R.string.nav_drop,
        selectedIcon = Icons.Rounded.WifiTethering,
        unselectedIcon = Icons.Outlined.WifiTethering,
        testTag = "nav_tab_drop",
    ),
}
