package com.example.pwabuilder.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface PwaDestinations : NavKey {
    @Serializable
    data object ProjectDashboard : PwaDestinations

    @Serializable
    data object AiEditor : PwaDestinations

    @Serializable
    data object Settings : PwaDestinations

    @Serializable
    data object ImportProject : PwaDestinations

    @Serializable
    data class PwaPreview(val projectId: String) : PwaDestinations

    @Serializable
    data class ProjectSettings(val projectId: String) : PwaDestinations
}
