package dz.cardiag.app.core

import android.content.Context
import dz.cardiag.app.VehicleModel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object VehicleCache {
    private const val PREFS = "cardiag_cache"
    private const val KEY_MODELS = "vehicle_models"
    private val json = Json { ignoreUnknownKeys = true }

    fun read(context: Context): List<VehicleModel> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODELS, null)
        if (raw.isNullOrBlank()) emptyList() else json.decodeFromString(ListSerializer(VehicleModel.serializer()), raw)
    }.getOrDefault(emptyList())

    fun write(context: Context, models: List<VehicleModel>) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_MODELS, json.encodeToString(ListSerializer(VehicleModel.serializer()), models)).apply()
        }
    }
}
