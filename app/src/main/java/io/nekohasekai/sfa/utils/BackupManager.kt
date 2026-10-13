package io.nekohasekai.sfa.utils

import android.content.Context
import android.net.Uri
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.preference.KeyValueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    suspend fun exportSettings(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val allSettings = Settings.instance.keyValuePairDao().all()
            val jsonArray = JSONArray()

            for (entity in allSettings) {
                val jsonObject = JSONObject()
                jsonObject.put("key", entity.key)
                jsonObject.put("valueType", entity.valueType)

                when (entity.valueType) {
                    KeyValueEntity.TYPE_BOOLEAN -> jsonObject.put("value", entity.boolean)
                    KeyValueEntity.TYPE_FLOAT -> jsonObject.put("value", entity.float)
                    KeyValueEntity.TYPE_LONG -> jsonObject.put("value", entity.long)
                    KeyValueEntity.TYPE_STRING -> jsonObject.put("value", entity.string)
                    KeyValueEntity.TYPE_STRING_SET -> {
                        val setArray = JSONArray()
                        entity.stringSet?.forEach { setArray.put(it) }
                        jsonObject.put("value", setArray)
                    }
                }
                jsonArray.put(jsonObject)
            }

            val resultJson = JSONObject().apply {
                put("version", 1)
                put("settings", jsonArray)
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(resultJson.toString(2).toByteArray())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun importSettings(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: return@withContext Result.failure(Exception("Failed to read file"))

            val rootObject = JSONObject(jsonString)
            val settingsArray = rootObject.getJSONArray("settings")

            val entities = mutableListOf<KeyValueEntity>()

            for (i in 0 until settingsArray.length()) {
                val item = settingsArray.getJSONObject(i)
                val key = item.getString("key")
                val valueType = item.getInt("valueType")

                val entity = KeyValueEntity(key)
                when (valueType) {
                    KeyValueEntity.TYPE_BOOLEAN -> entity.put(item.getBoolean("value"))
                    KeyValueEntity.TYPE_FLOAT -> entity.put(item.getDouble("value").toFloat())
                    KeyValueEntity.TYPE_LONG -> entity.put(item.getLong("value"))
                    KeyValueEntity.TYPE_STRING -> entity.put(item.getString("value"))
                    KeyValueEntity.TYPE_STRING_SET -> {
                        val setArray = item.getJSONArray("value")
                        val set = mutableSetOf<String>()
                        for (j in 0 until setArray.length()) {
                            set.add(setArray.getString(j))
                        }
                        entity.put(set)
                    }
                }
                entities.add(entity)
            }

            val dao = Settings.instance.keyValuePairDao()
            entities.forEach { dao.put(it) }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
