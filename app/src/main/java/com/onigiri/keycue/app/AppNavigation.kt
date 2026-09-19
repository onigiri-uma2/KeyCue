package com.onigiri.keycue.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    openedFromOverlay: Boolean = false,
    resetToHomeTrigger: Int = 0,
    onCloseSettings: () -> Unit = {},
    onSelectFileClick: () -> Unit = {},
    onFittingClick: () -> Unit = {},
    onStartSupportClick: () -> Unit = {}
) {
    // Overlay起動の新規Intent受信時にHOME_ROUTEまで強制的に戻す
    LaunchedEffect(resetToHomeTrigger) {
        if (resetToHomeTrigger > 0) {
            navController.popBackStack(AppDestinations.HOME_ROUTE, inclusive = false)
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppDestinations.HOME_ROUTE,
        modifier = modifier
    ) {
        composable(AppDestinations.HOME_ROUTE) {
            // オーバーレイから起動された場合のみルートでBackを押したときに設定画面を終了して直前アプリへ戻る
            BackHandler(enabled = openedFromOverlay) {
                onCloseSettings()
            }

            HomeScreen(
                openedFromOverlay = openedFromOverlay,
                onCloseClick = onCloseSettings,
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
