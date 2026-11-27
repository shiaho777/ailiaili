package app.ailiaili

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ailiaili.core.data.AppGraph

@Composable
internal actual fun rememberShellViewModel(): ShellViewModel =
    viewModel { ShellViewModel(SavedStateHandle(), AppGraph.settings.current.startupPage) }
