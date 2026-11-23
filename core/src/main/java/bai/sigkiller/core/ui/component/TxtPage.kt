package bai.sigkiller.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bai.sigkiller.core.R
import bai.sigkiller.core.ui.theme.LocalTxtUiStyle
import bai.sigkiller.core.ui.theme.UiStyle
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberTxtSnackbarState(): SnackbarHostState = remember { SnackbarHostState() }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TxtPage(
    title: String,
    snackbarState: SnackbarHostState,
    dialogState: TxtDialogState,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    showTopBar: Boolean = true,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    overlays: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top).union(WindowInsets.displayCutout),
    content: @Composable (PaddingValues) -> Unit,
) {
    ProvideNoIndication {
        val m3 = LocalTxtUiStyle.current == UiStyle.MATERIAL3
        val surfaceColor = if (m3) {
            androidx.compose.material3.MaterialTheme.colorScheme.surface
        } else {
            MiuixTheme.colorScheme.surface
        }
        val isSub = onBack != null && showTopBar

        Box(modifier.fillMaxSize()) {
            MiuixScaffold(
                m3 = m3,
                title = title,
                subtitle = subtitle,
                isSub = isSub,
                onBack = onBack,
                actions = actions,
                surfaceColor = surfaceColor,
                bottomBar = bottomBar,
                snackbarState = snackbarState,
                dialogState = dialogState,
                overlays = overlays,
                contentWindowInsets = contentWindowInsets,
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = if (isSub) 0.dp else padding.calculateTopPadding()),
                ) {
                    if (!isSub) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(end = 8.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            actions()
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        content(
                            PaddingValues(
                                start = padding.calculateLeftPadding(LocalLayoutDirection.current),
                                top = if (isSub) padding.calculateTopPadding() else 0.dp,
                                end = padding.calculateRightPadding(LocalLayoutDirection.current),
                                bottom = padding.calculateBottomPadding(),
                            ),
                        )
                    }
                }
            }
        }
    }
}//top level tabs drop the title bar, sub pages keep a plain one

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MiuixScaffold(
    m3: Boolean,
    title: String,
    subtitle: String,
    isSub: Boolean,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    surfaceColor: androidx.compose.ui.graphics.Color,
    bottomBar: @Composable () -> Unit,
    snackbarState: SnackbarHostState,
    dialogState: TxtDialogState,
    overlays: @Composable () -> Unit,
    contentWindowInsets: WindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (m3) {
        androidx.compose.material3.Scaffold(
            containerColor = surfaceColor,
            topBar = {
                if (isSub) {
                    androidx.compose.material3.TopAppBar(
                        title = {
                            Column {
                                androidx.compose.material3.Text(
                                    text = title,
                                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                                )
                                if (subtitle.isNotBlank()) {
                                    androidx.compose.material3.Text(
                                        text = subtitle,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            androidx.compose.material3.TextButton(onClick = { onBack?.invoke() }) {
                                androidx.compose.material3.Text(
                                    text = stringResource(R.string.core_back),
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                )
                            }
                        },
                        actions = actions,
                        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                            containerColor = surfaceColor,
                        ),
                    )
                }
            },
            bottomBar = bottomBar,
            snackbarHost = { SnackbarHost(snackbarState) },
            contentWindowInsets = contentWindowInsets,
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = if (isSub) 0.dp else padding.calculateTopPadding()),
                ) {
                    if (!isSub) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(end = 8.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            actions()
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        content(
                            PaddingValues(
                                start = padding.calculateLeftPadding(LocalLayoutDirection.current),
                                top = if (isSub) padding.calculateTopPadding() else 0.dp,
                                end = padding.calculateRightPadding(LocalLayoutDirection.current),
                                bottom = padding.calculateBottomPadding(),
                            ),
                        )
                    }
                }
                TxtDialogHost(dialogState)
                overlays()
            }
        }
    } else {
        Scaffold(
            containerColor = surfaceColor,
            topBar = {
                if (isSub) {
                    TopAppBar(
                        title = title,
                        subtitle = subtitle,
                        color = surfaceColor,
                        navigationIcon = {
                            IconButton(onClick = { onBack?.invoke() }) {
                                Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.core_back))
                            }
                        },
                        actions = actions,
                    )
                }
            },
            bottomBar = bottomBar,
            snackbarHost = { SnackbarHost(snackbarState) },
            contentWindowInsets = contentWindowInsets,
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = if (isSub) 0.dp else padding.calculateTopPadding()),
                ) {
                    if (!isSub) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(end = 8.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            actions()
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        content(
                            PaddingValues(
                                start = padding.calculateLeftPadding(LocalLayoutDirection.current),
                                top = if (isSub) padding.calculateTopPadding() else 0.dp,
                                end = padding.calculateRightPadding(LocalLayoutDirection.current),
                                bottom = padding.calculateBottomPadding(),
                            ),
                        )
                    }
                }
            }
            TxtDialogHost(dialogState)
            overlays()
        }
    }
}//m3 and miuix scaffolds differ in where the dialog host and overlay slots live

@Composable
fun TxtScrollTail() {
    val barInset = LocalTxtNavBarInset.current
    if (barInset > 0.dp) {
        Spacer(Modifier.height(barInset))
    }
}//last item of a scrollable page, keeps the tail clear of the floating bar