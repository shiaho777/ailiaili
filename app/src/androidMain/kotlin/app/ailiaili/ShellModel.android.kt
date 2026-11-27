package app.ailiaili

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import app.ailiaili.core.data.AppGraph

@Composable
internal actual fun rememberShellViewModel(): ShellViewModel =
    viewModel { ShellViewModel(createSavedStateHandle(), AppGraph.settings.current.startupPage) }
