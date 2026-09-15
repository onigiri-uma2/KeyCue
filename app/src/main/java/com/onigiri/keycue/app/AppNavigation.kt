package com.onigiri.keycue.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.onigiri.keycue.ui.home.HomeScreen
import com.onigiri.keycue.ui.preview.PlaybackPreviewScreen

/**
 * ナビゲーション画面ルート定数。
 */
object AppDestinations {
    const val HOME_ROUTE = "home"
    const val PREVIEW_ROUTE = "preview"
    const val FITTING_ROUTE = "fitting"
}

/**
 * アプリの画面遷移をホストするコンポーザブル。
 * Navigation Compose を利用して Home, PlaybackPreview, Fitting の画面遷移を管理する。
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    onSelectFileClick: () -> Unit = {},
    onFittingClick: () -> Unit = {},
    onStartSupportClick: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.HOME_ROUTE,
        modifier = modifier
    ) {
        composable(AppDestinations.HOME_ROUTE) {
            HomeScreen(
                onSelectFileClick = onSelectFileClick,
                onFittingClick = {
                    onFittingClick()
                    navController.navigate(AppDestinations.FITTING_ROUTE)
                },
                onStartSupportClick = onStartSupportClick,
                onPreviewClick = {
                    navController.navigate(AppDestinations.PREVIEW_ROUTE)
                }
            )
        }

        composable(AppDestinations.PREVIEW_ROUTE) {
            PlaybackPreviewScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(AppDestinations.FITTING_ROUTE) {
            com.onigiri.keycue.ui.fitting.FittingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
