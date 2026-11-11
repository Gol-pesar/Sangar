package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ProxyProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class ProfileRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _profiles = MutableStateFlow<List<ProxyProfile>>(emptyList())
    val profiles: StateFlow<List<ProxyProfile>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    private val _themeMode = MutableStateFlow<String>("system") // "system", "dark", "light"
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val rawJson = prefs.getString(KEY_PROFILES, null)
        val list = mutableListOf<ProxyProfile>()
        if (rawJson != null) {
            try {
                val array = JSONArray(rawJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(ProxyProfile.fromJson(obj))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (list.isEmpty()) {
            // Seed default profile from user request
            val defaultProfile = ProxyProfile(
                name = "Twilight Worker",
                uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd",
                workerUrl = "twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev",
                cleanIp = "172.67.70.244",
                localPort = 40446
            )
            list.add(defaultProfile)
            saveProfilesInternal(list)
            prefs.edit().putString(KEY_ACTIVE_ID, defaultProfile.id).apply()
        }

        _profiles.value = list

        val savedActiveId = prefs.getString(KEY_ACTIVE_ID, null) ?: list.firstOrNull()?.id
        _activeProfileId.value = savedActiveId

        _themeMode.value = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
    }

    private fun saveProfilesInternal(list: List<ProxyProfile>) {
        val array = JSONArray()
        for (item in list) {
            array.put(item.toJson())
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
        _profiles.value = list
    }

    fun addOrUpdateProfile(profile: ProxyProfile) {
        val current = _profiles.value.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(0, profile)
        }
        saveProfilesInternal(current)
        if (_activeProfileId.value == null || current.size == 1) {
            setActiveProfile(profile.id)
        }
    }

    fun deleteProfile(id: String) {
        val current = _profiles.value.toMutableList()
        current.removeAll { it.id == id }
        saveProfilesInternal(current)
        if (_activeProfileId.value == id) {
            val nextActive = current.firstOrNull()?.id
            setActiveProfile(nextActive)
        }
    }

    fun setActiveProfile(id: String?) {
        _activeProfileId.value = id
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun getActiveProfile(): ProxyProfile? {
        val id = _activeProfileId.value ?: return _profiles.value.firstOrNull()
        return _profiles.value.find { it.id == id } ?: _profiles.value.firstOrNull()
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    companion object {
        private const val PREFS_NAME = "sangar_prefs"
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_ACTIVE_ID = "active_profile_id"
        private const val KEY_THEME_MODE = "theme_mode"

        @Volatile
        private var INSTANCE: ProfileRepository? = null

        fun getInstance(context: Context): ProfileRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProfileRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
