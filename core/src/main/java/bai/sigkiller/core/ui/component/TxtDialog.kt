package bai.sigkiller.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bai.sigkiller.core.ui.theme.LocalTxtUiStyle
import bai.sigkiller.core.ui.theme.UiStyle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class TxtDialogSpec(
    val title: String,
    val confirmText: String,
    val message: String = "",
    val dismissText: String? = null,
    val danger: Boolean = false,
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)//a dialog is data, the host renders whatever is queued

class TxtDialogState {

    internal val queue = mutableStateListOf<TxtDialogSpec>()

    val isActive: Boolean get() = queue.isNotEmpty()

    val current: TxtDialogSpec? get() = queue.firstOrNull()

    fun show(spec: TxtDialogSpec) {
        queue.add(spec)
    }

    fun dismiss() {
        queue.removeFirstOrNull()
    }//a second dialog never replaces a pending confirmation

    fun confirm() {
        val spec = queue.firstOrNull() ?: return
        queue.removeFirstOrNull()
        spec.onConfirm()
    }
}

@Composable
fun rememberTxtDialogState(): TxtDialogState = remember { TxtDialogState() }

@Composable
fun TxtDialogHost(state: TxtDialogState, modifier: Modifier = Modifier) {
    val spec = state.current
    val reporter = LocalTxtDialogOpenReporter.current
    TxtReportDialogOpen(reporter, spec != null)
    if (LocalTxtUiStyle.current == UiStyle.MATERIAL3 && spec != null) {
        M3AlertDialogHost(spec = spec, state = state, modifier = modifier)
    } else {
        OverlayDialog(
            show = spec != null,
            title = spec?.title,
            summary = spec?.message?.takeIf { it.isNotBlank() },
            modifier = modifier,
            onDismissRequest = {
                val pending = state.current
                state.dismiss()
                pending?.onDismiss?.invoke()
            },
        ) {
            spec?.let { current ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (current.dismissText != null) {
                        Button(
                            onClick = {
                                state.dismiss()
                                current.onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(current.dismissText)
                        }
                    }
                    Button(
                        onClick = { state.confirm() },
                        modifier = Modifier.weight(1f),
                        colors = if (current.danger) {
                            ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.error,
                                contentColor = MiuixTheme.colorScheme.onError,
                            )
                        } else {
                            ButtonDefaults.buttonColorsPrimary()
                        },
                    ) {
                        Text(current.confirmText)
                    }
                }
            }
        }
    }
}//host is placed once per page, OverlayDialog needs the popup host from Scaffold

@Composable
private fun M3AlertDialogHost(
    spec: TxtDialogSpec,
    state: TxtDialogState,
    modifier: Modifier,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {
            val pending = state.current
            state.dismiss()
            pending?.onDismiss?.invoke()
        },
        modifier = modifier,
        title = { androidx.compose.material3.Text(spec.title) },
        text = spec.message.takeIf { it.isNotBlank() }?.let {
            { androidx.compose.material3.Text(it) }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { state.confirm() }) {
                androidx.compose.material3.Text(
                    text = spec.confirmText,
                    color = if (spec.danger) {
                        androidx.compose.material3.MaterialTheme.colorScheme.error
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = spec.dismissText?.let { label ->
            {
                androidx.compose.material3.TextButton(onClick = {
                    state.dismiss()
                    spec.onDismiss()
                }) {
                    androidx.compose.material3.Text(label)
                }
            }
        },
    )
}//m3 dialogs take text actions, not filled buttons, so danger is a red label

@Composable
fun TxtSheetDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reporter = LocalTxtDialogOpenReporter.current
    TxtReportDialogOpen(reporter, show)
    if (LocalTxtUiStyle.current == UiStyle.MATERIAL3) {
        M3SheetDialog(show = show, title = title, onDismissRequest = onDismissRequest, modifier = modifier, content = content)
    } else {
        OverlayDialog(
            show = show,
            title = title,
            modifier = modifier,
            onDismissRequest = onDismissRequest,
        ) {
            ProvideNoIndication {
                Column(Modifier.fillMaxWidth(), content = content)
            }
        }
    }
}//a hand written sheet must report upward too, or the bar stays put and covers it

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun M3SheetDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!show) return
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth()) {
            androidx.compose.material3.Text(
                text = title,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
            ProvideNoIndication {
                Column(Modifier.fillMaxWidth(), content = content)
            }
        }
    }
}//m3 sheet owns its own scrim, so the hosting page must not add another