package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMapPage(
    chatId: Uuid,
    highlightPoiId: String? = null,
) {
    val vm: ChatVM = koinViewModel(parameters = { parametersOf(chatId.toString()) })
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = { BackButton() },
                title = { Text(stringResource(R.string.chat_map_open)) },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        ChatMapDrawerContent(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            travelPois = conversation.travelPlan?.pois ?: emptyList(),
            highlightPoiId = highlightPoiId,
            onOpenInternalWebView = { url -> nav.navigate(Screen.WebView(url = url)) },
        )
    }
}
