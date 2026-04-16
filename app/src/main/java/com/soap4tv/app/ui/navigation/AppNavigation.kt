package com.soap4tv.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.soap4tv.app.data.model.AuthState
import com.soap4tv.app.ui.screen.detail.MovieDetailScreen
import com.soap4tv.app.ui.screen.detail.SeriesDetailScreen
import com.soap4tv.app.ui.screen.episodes.EpisodesScreen
import com.soap4tv.app.ui.screen.home.HomeScreen
import com.soap4tv.app.ui.screen.login.LoginScreen
import com.soap4tv.app.ui.screen.login.LoginViewModel
import com.soap4tv.app.ui.screen.player.PlayerScreen
import com.soap4tv.app.ui.screen.search.SearchScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val SERIES_DETAIL = "series/{slug}"
    const val EPISODES = "episodes/{slug}/{season}"
    const val MOVIE_DETAIL = "movie/{id}"
    const val PLAYER_SERIES = "player/series/{eid}/{sid}/{hash}/{slug}/{season}"
    const val PLAYER_MOVIE = "player/movie/{id}"
    const val SEARCH = "search"

    fun seriesDetail(slug: String) = "series/$slug"
    fun episodes(slug: String, season: Int) = "episodes/$slug/$season"
    fun movieDetail(id: Int) = "movie/$id"
    fun playerSeries(eid: String, sid: String, hash: String, slug: String, season: Int) = "player/series/$eid/$sid/$hash/$slug/$season"
    fun playerMovie(id: Int) = "player/movie/$id"
}

@Composable
fun AppNavigation(authState: AuthState) {
    val navController = rememberNavController()
    val startDestination = if (authState is AuthState.LoggedIn) Routes.HOME else Routes.LOGIN

    // React to auth state changes (session expired → login)
    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedOut) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onSeriesClick = { slug -> navController.navigate(Routes.seriesDetail(slug)) },
                onMovieClick = { id -> navController.navigate(Routes.movieDetail(id)) },
                onSearchClick = { navController.navigate(Routes.SEARCH) }
            )
        }

        composable(
            route = Routes.SERIES_DETAIL,
            arguments = listOf(navArgument("slug") { type = NavType.StringType })
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("slug") ?: return@composable
            SeriesDetailScreen(
                slug = slug,
                onSeasonClick = { s, season -> navController.navigate(Routes.episodes(s, season)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EPISODES,
            arguments = listOf(
                navArgument("slug") { type = NavType.StringType },
                navArgument("season") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("slug") ?: return@composable
            val season = backStackEntry.arguments?.getInt("season") ?: 1
            EpisodesScreen(
                slug = slug,
                season = season,
                onEpisodeClick = { eid, sid, hash ->
                    navController.navigate(Routes.playerSeries(eid, sid, hash, slug, season))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.MOVIE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: return@composable
            MovieDetailScreen(
                movieId = id,
                onPlayClick = { navController.navigate(Routes.playerMovie(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PLAYER_SERIES,
            arguments = listOf(
                navArgument("eid") { type = NavType.StringType },
                navArgument("sid") { type = NavType.StringType },
                navArgument("hash") { type = NavType.StringType },
                navArgument("slug") { type = NavType.StringType },
                navArgument("season") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            PlayerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PLAYER_MOVIE,
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) {
            PlayerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onSeriesClick = { slug -> navController.navigate(Routes.seriesDetail(slug)) },
                onMovieClick = { id -> navController.navigate(Routes.movieDetail(id)) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
