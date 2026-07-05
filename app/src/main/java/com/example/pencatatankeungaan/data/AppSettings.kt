package com.example.pencatatankeungaan.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppSettings(private val context: Context) {
    companion object {
        val KEY_BUSINESS_NAME = stringPreferencesKey("business_name")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val KEY_CATEGORIES_INCOME = stringPreferencesKey("categories_income")
        val KEY_CATEGORIES_EXPENSE = stringPreferencesKey("categories_expense")
    }

    val businessNameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_BUSINESS_NAME] ?: "Toko Sembako Berkah"
    }

    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val appLockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_APP_LOCK_ENABLED] ?: false
    }

    val categoriesIncomeFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_CATEGORIES_INCOME] ?: "Penjualan||Lainnya"
        raw.split("||").filter { it.isNotEmpty() }
    }

    val categoriesExpenseFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_CATEGORIES_EXPENSE] ?: "Bahan Baku||Operasional||Lainnya"
        raw.split("||").filter { it.isNotEmpty() }
    }

    suspend fun saveBusinessName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BUSINESS_NAME] = name
        }
    }

    suspend fun saveOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun saveAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun saveCategories(income: List<String>, expense: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CATEGORIES_INCOME] = income.joinToString("||")
            preferences[KEY_CATEGORIES_EXPENSE] = expense.joinToString("||")
        }
    }

    suspend fun clearSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
