package io.github.eslamasabry.oc.feature.diagnostics.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.flow.StateFlow

/** Everything this phone kept: [KeptPresenter] on the page's scope. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class KeptViewModel(host: KeptDiagnosticsHost, nav: AppNavigator) : ViewModel(), KeptActions {
    private val presenter = KeptPresenter(host, viewModelScope).also { it.start(nav::back) }

    public val state: StateFlow<KeptUi> = presenter.state

    override fun onBack(): Unit = presenter.onBack()

    override fun onCrashCapture(on: Boolean): Unit = presenter.onCrashCapture(on)

    override fun onPreviewCrashReport(): Unit = presenter.onPreviewCrashReport()

    override fun onShareCrashReport(): Unit = presenter.onShareCrashReport()

    override fun onClosePreview(): Unit = presenter.onClosePreview()

    override fun onDeleteCrashReports(): Unit = presenter.onDeleteCrashReports()

    override fun onConfirmDelete(): Unit = presenter.onConfirmDelete()

    override fun onClearErrors(): Unit = presenter.onClearErrors()

    override fun onConfirmClear(): Unit = presenter.onConfirmClear()

    override fun onCancelConfirm(): Unit = presenter.onCancelConfirm()

    override fun onRetryExits(): Unit = presenter.onRetryExits()

    override fun onDismissNotice(): Unit = presenter.onDismissNotice()
}
