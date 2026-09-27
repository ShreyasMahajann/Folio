package com.shreyas.pdfreader.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shreyas.pdfreader.ReaderApp
import com.shreyas.pdfreader.data.LibraryItem
import com.shreyas.pdfreader.data.LibraryRepository
import com.shreyas.pdfreader.data.db.DocumentEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {

    /** Null while the library loads. */
    val items: StateFlow<List<LibraryItem>?> =
        repository.observeLibrary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun import(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                repository.import(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(importErrorMessage(e))
            } finally {
                _importing.value = false
            }
        }
    }

    fun rename(document: DocumentEntity, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.rename(document.id, trimmed) }
    }

    fun remove(document: DocumentEntity) {
        viewModelScope.launch { repository.remove(document) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel((this[APPLICATION_KEY] as ReaderApp).container.repository) }
        }
    }
}

fun importErrorMessage(error: Exception): String = when (error) {
    is SecurityException -> "Cannot import. The PDF needs a password or cannot be read."
    else -> "Cannot import. The file is not a valid PDF or cannot be read."
}
