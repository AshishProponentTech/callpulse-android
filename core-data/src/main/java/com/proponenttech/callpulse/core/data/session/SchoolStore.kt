package com.proponenttech.callpulse.core.data.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Synchronous reads for the HTTP interceptor, observable reads for the school pickers. */
@Singleton
class SchoolStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("yogatribex_school", Context.MODE_PRIVATE)
    private val selected = MutableStateFlow(prefs.getString("school_uuid", null))
    val schoolId = selected.asStateFlow()
    fun select(id: String?) {
        prefs.edit().apply {
            if (id == null) remove("school_uuid") else putString("school_uuid", id)
        }.apply()
        selected.value = id
    }
}
