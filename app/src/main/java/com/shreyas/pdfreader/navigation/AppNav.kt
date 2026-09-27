package com.shreyas.pdfreader.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shreyas.pdfreader.AppContainer
import com.shreyas.pdfreader.ui.cover.CoverScreen
import com.shreyas.pdfreader.ui.cover.CoverViewModel
import com.shreyas.pdfreader.ui.library.LibraryScreen
import com.shreyas.pdfreader.ui.library.importErrorMessage
import com.shreyas.pdfreader.ui.pages.PagesScreen
import com.shreyas.pdfreader.ui.pages.PagesViewModel
import com.shreyas.pdfreader.ui.reader.ReaderScreen
import com.shreyas.pdfreader.ui.reader.ReaderViewModel
import com.shreyas.pdfreader.ui.search.SearchScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.receiveAsFlow

private const val LIBRARY = "library"
private const val READER = "reader"
private const val SEARCH = "search"
private const val COVER = "cover"
private const val PAGES = "pages"

@Composable
fun AppNav(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // PDFs from "Open with" and the Share Sheet go into the library and open at once.
    LaunchedEffect(container, navController) {
        container.incomingDocuments.receiveAsFlow().collect { uri ->
            try {
                navController.openReader(container.repository.import(uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, importErrorMessage(e), Toast.LENGTH_LONG).show()
            }
        }
    }

    NavHost(navController, startDestination = LIBRARY) {
        composable(LIBRARY) {
            LibraryScreen(
                onOpen = { navController.openReader(it) },
                onSearch = { navController.navigate(SEARCH) { launchSingleTop = true } },
                onChangeCover = { navController.navigate("$COVER/$it") { launchSingleTop = true } },
            )
        }
        composable(SEARCH) {
            val backToLibrary = { navController.popBackStack(LIBRARY, inclusive = false) }
            SearchScreen(onBack = { backToLibrary() }, onAdded = { backToLibrary() })
        }
        composable(
            route = "$READER/{${ReaderViewModel.DOCUMENT_ID_ARG}}",
            arguments = listOf(navArgument(ReaderViewModel.DOCUMENT_ID_ARG) { type = NavType.LongType }),
        ) { entry ->
            ReaderScreen(
                onBack = { navController.popBackStack(LIBRARY, inclusive = false) },
                onManagePages = {
                    val documentId = entry.arguments?.getLong(ReaderViewModel.DOCUMENT_ID_ARG)
                    navController.navigate("$PAGES/$documentId") { launchSingleTop = true }
                },
            )
        }
        composable(
            route = "$COVER/{${CoverViewModel.DOCUMENT_ID_ARG}}",
            arguments = listOf(navArgument(CoverViewModel.DOCUMENT_ID_ARG) { type = NavType.LongType }),
        ) {
            CoverScreen(onDone = { navController.popBackStack() })
        }
        composable(
            route = "$PAGES/{${PagesViewModel.DOCUMENT_ID_ARG}}",
            arguments = listOf(navArgument(PagesViewModel.DOCUMENT_ID_ARG) { type = NavType.LongType }),
        ) {
            PagesScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun NavHostController.openReader(documentId: Long) {
    // Only one reader on the back stack. Back always returns to the library.
    popBackStack(LIBRARY, inclusive = false)
    navigate("$READER/$documentId")
}
