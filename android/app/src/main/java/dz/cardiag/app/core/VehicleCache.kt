package dz.cardiag.app.core

import android.content.Context
import androidx.core.content.edit
import dz.cardiag.app.UiModel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object VehicleCache {
    private const val PREFS = "cardiag_cache"
    private const val KEY_MODELS = "vehicle_models_v2"
    private val json = Json { ignoreUnknownKeys = true }

    fun read(context: Context): List<UiModel> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODELS, null)
        if (raw.isNullOrBlank()) emptyList() else json.decodeFromString(ListSerializer(UiModel.serializer()), raw)
    }.getOrDefault(emptyList())

    fun write(context: Context, models: List<UiModel>) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString(KEY_MODELS, json.encodeToString(ListSerializer(UiModel.serializer()), models))
            }
        }
    }
}
