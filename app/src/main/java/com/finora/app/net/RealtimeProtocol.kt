package com.finora.app.net

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

sealed class RtEvent {
    object JoinOk : RtEvent()
    data class JoinError(val message: String) : RtEvent()
    data class Change(val updatedAtMillis: Long?) : RtEvent()
    object Ignore : RtEvent()
}

/** Supabase Realtime (Phoenix channels, vsn 1.0.0) messages — the same subscription the web app makes. */
object RealtimeProtocol {
    fun topic(userId: String): String = "realtime:finora_sync_$userId"

    fun joinMessage(userId: String, accessToken: String): String {
        val change = JSONObject().put("event", "*").put("schema", "public").put("table", "user_data").put("filter", "user_id=eq.$userId")
        val config = JSONObject()
            .put("broadcast", JSONObject().put("ack", false).put("self", false))
            .put("presence", JSONObject().put("key", ""))
            .put("postgres_changes", JSONArray().put(change))
        return JSONObject()
            .put("topic", topic(userId)).put("event", "phx_join")
            .put("payload", JSONObject().put("config", config).put("access_token", accessToken))
            .put("ref", "1").put("join_ref", "1")
            .toString()
    }

    fun heartbeat(n: Int): String =
        JSONObject().put("topic", "phoenix").put("event", "heartbeat").put("payload", JSONObject()).put("ref", n.toString()).toString()

    fun parse(text: String, topic: String): RtEvent {
        return try {
            val o = JSONObject(text)
            val event = o.optString("event")
            val t = o.optString("topic")
            val payload = o.optJSONObject("payload")
            when {
                t != topic -> RtEvent.Ignore
                event == "phx_reply" -> {
                    val status = payload?.optString("status") ?: ""
                    if (status == "ok") RtEvent.JoinOk else RtEvent.JoinError(payload?.toString() ?: "join failed")
                }
                event == "system" && payload?.optString("status") == "error" -> RtEvent.JoinError(payload.optString("message"))
                event == "phx_error" || event == "phx_close" -> RtEvent.JoinError(event)
                event == "postgres_changes" -> {
                    val rec = payload?.optJSONObject("data")?.optJSONObject("record")
                    val ts = rec?.optString("updated_at", "")?.takeIf { it.isNotEmpty() }
                    val ms = try { if (ts != null) OffsetDateTime.parse(ts).toInstant().toEpochMilli() else null } catch (e: Exception) { null }
                    RtEvent.Change(ms)
                }
                else -> RtEvent.Ignore
            }
        } catch (e: Exception) {
            RtEvent.Ignore
        }
    }
}

/** Live link to the cloud (implemented with OkHttp on Android). */
interface RealtimeLink {
    fun start()
    fun stop()
}
