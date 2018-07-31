package com.chesire.malime.view.search

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.LiveDataReactiveStreams
import android.arch.lifecycle.MutableLiveData
import android.support.annotation.StringRes
import com.chesire.malime.R
import com.chesire.malime.core.api.SearchApi
import com.chesire.malime.core.flags.ItemType
import com.chesire.malime.core.models.MalimeModel
import com.chesire.malime.core.repositories.Library
import com.chesire.malime.util.IOScheduler
import com.chesire.malime.util.UIScheduler
import io.reactivex.BackpressureStrategy
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import timber.log.Timber
import javax.inject.Inject

class SearchViewModel @Inject constructor(
    context: Application,
    private val searchApi: SearchApi,
    private val library: Library
) : AndroidViewModel(context) {
    private val disposables = CompositeDisposable()

    @Inject
    @field:IOScheduler
    lateinit var subscribeScheduler: Scheduler

    @Inject
    @field:UIScheduler
    lateinit var observeScheduler: Scheduler

    val series: LiveData<List<MalimeModel>> = LiveDataReactiveStreams.fromPublisher(
        library.observeLibrary().toFlowable(BackpressureStrategy.ERROR)
    )
    val searchItems = MutableLiveData<List<MalimeModel>>()
    val params = SearchParams()

    fun searchForSeries(type: ItemType, @StringRes errorCallback: (Int) -> Unit) {
        if (params.searchText.isBlank() || type == ItemType.Unknown) {
            Timber.w("No text entered or type was unknown")
            return
        }

        disposables.add(
            searchApi.searchForSeriesWith(params.searchText, type)
                .subscribeOn(subscribeScheduler)
                .observeOn(observeScheduler)
                .doOnSubscribe {
                    params.searching = true
                }
                .doOnComplete {
                    params.searching = false
                }
                .doOnError {
                    Timber.e(it, "Error performing the search")
                    params.searching = false
                    errorCallback(R.string.search_failed_general_error)
                }
                .subscribe {
                    Timber.i("Found ${it.count()} items")
                    searchItems.value = it
                    if (it.isEmpty()) {
                        errorCallback(R.string.search_failed_no_items)
                    }
                }
        )
    }

    fun addNewSeries(selectedSeries: MalimeModel, callback: (Boolean) -> Unit) {
        disposables.add(
            library.sendNewToApi(selectedSeries)
                .subscribeOn(subscribeScheduler)
                .observeOn(observeScheduler)
                .subscribe(
                    {
                        library.insertIntoLocalLibrary(it)
                        callback(true)
                    },
                    {
                        callback(false)
                    }
                )
        )
    }

    override fun onCleared() {
        disposables.clear()
        super.onCleared()
    }
}