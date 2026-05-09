package com.washdog.app.data

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

class SupabaseDataSource(
    private val supabaseUrl: String,
    private val anonKey: String
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Snake_case Gson para Supabase (owner_name, pet_name, etc.)
    val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private val JSON = "application/json".toMediaType()

    private fun baseHeaders() = okhttp3.Headers.Builder()
        .add("apikey", anonKey)
        .add("Authorization", "Bearer $anonKey")
        .add("Content-Type", "application/json")
        .build()

    suspend fun <T : Any> selectAll(table: String, type: Type): List<T> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/$table?select=*&order=id")
            .headers(baseHeaders())
            .get().build()
        val body = http.newCall(req).execute().use { it.body?.string() ?: "[]" }
        gson.fromJson<List<T>>(body, type) ?: emptyList()
    }

    suspend fun <T : Any> selectSingle(table: String, filter: String, type: Class<T>): T? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/$table?$filter&limit=1")
            .headers(baseHeaders())
            .get().build()
        val body = http.newCall(req).execute().use { it.body?.string() ?: "[]" }
        val list: List<T>? = gson.fromJson(body, TypeToken.getParameterized(List::class.java, type).type)
        list?.firstOrNull()
    }

    suspend fun upsertList(table: String, json: String) = withContext(Dispatchers.IO) {
        val headers = baseHeaders().newBuilder()
            .add("Prefer", "resolution=merge-duplicates,return=minimal")
            .build()
        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/$table")
            .headers(headers)
            .post(json.toRequestBody(JSON)).build()
        http.newCall(req).execute().close()
    }

    suspend fun upsertSingle(table: String, json: String) = withContext(Dispatchers.IO) {
        val headers = baseHeaders().newBuilder()
            .add("Prefer", "resolution=merge-duplicates,return=minimal")
            .build()
        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/$table")
            .headers(headers)
            .post(json.toRequestBody(JSON)).build()
        http.newCall(req).execute().close()
    }

    suspend fun delete(table: String, column: String, value: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/$table?$column=eq.$value")
            .headers(baseHeaders())
            .delete().build()
        http.newCall(req).execute().close()
    }
}
