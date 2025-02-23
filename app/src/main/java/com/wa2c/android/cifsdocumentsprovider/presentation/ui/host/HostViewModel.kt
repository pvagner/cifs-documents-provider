package com.wa2c.android.cifsdocumentsprovider.presentation.ui.host

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.hadilq.liveevent.LiveEvent
import com.wa2c.android.cifsdocumentsprovider.common.utils.MainCoroutineScope
import com.wa2c.android.cifsdocumentsprovider.common.utils.logD
import com.wa2c.android.cifsdocumentsprovider.common.values.HostSortType
import com.wa2c.android.cifsdocumentsprovider.domain.model.HostData
import com.wa2c.android.cifsdocumentsprovider.domain.repository.HostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Host Screen ViewModel
 */
@HiltViewModel
class HostViewModel @Inject constructor(
    private val hostRepository: HostRepository,
): ViewModel(), CoroutineScope by MainCoroutineScope() {

    private val _navigationEvent = LiveEvent<HostNav>()
    val navigationEvent: LiveData<HostNav> = _navigationEvent

    private val _isLoading =  LiveEvent<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    val hostData: LiveData<HostData> = hostRepository.hostFlow.onEach {
        if (it == null) _isLoading.value = false
    }.filterNotNull().asLiveData()

    val sortType: HostSortType get() = hostRepository.sortType

    fun discovery() {
        launch {
            runCatching {
                hostRepository.stopDiscovery()
                _isLoading.value = true
                hostRepository.startDiscovery()
            }.onFailure {
                _navigationEvent.value = HostNav.NetworkError
                _isLoading.value = false
            }
        }
    }

    fun onClickItem(item: HostData) {
        logD("onClickItem")
        _navigationEvent.value = HostNav.SelectItem(item)
    }

    fun onClickSetManually() {
        logD("onClickSetManually")
        _navigationEvent.value = HostNav.SelectItem(null)
    }

    fun onClickSort(sortType: HostSortType) {
        hostRepository.sortType = sortType
    }

    override fun onCleared() {
        super.onCleared()
        runCatching {
            hostRepository.stopDiscovery()
        }.onFailure {
            _navigationEvent.value = HostNav.NetworkError
        }
        _isLoading.value = false
    }


}
