package org.ndp.multi_dns_query_ns.utils

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.lettuce.core.RedisClient
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.sync.RedisCommands
import org.ndp.multi_dns_query_ns.bean.MQResult
import org.ndp.multi_dns_query_ns.bean.MQTask

object RedisHandler {
    private val commands: RedisCommands<String, String>
    private val mqTaskAdapter: JsonAdapter<MQTask>
    private val mqResultAdapter: JsonAdapter<MQResult>
    private var consumedID = ""

    init {
        val client = RedisClient.create(Settings.setting["redis.url"] as String)
        val connection = client.connect()
        commands = connection.sync()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        mqTaskAdapter = moshi.adapter(MQTask::class.java)
        mqResultAdapter = moshi.adapter(MQResult::class.java)
    }

    fun generateNonce(size: Int): String {
        val nonceScope = "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val scopeSize = nonceScope.length
        val nonceItem: (Int) -> Char = { nonceScope[(scopeSize * Math.random()).toInt()] }
        return Array(size, nonceItem).joinToString("")
    }

    fun consumeTaskParam(name: String): MQTask? {
        val consumer = io.lettuce.core.Consumer.from(
            Settings.setting["group"] as String, name
        )
        val content = commands.xreadgroup(
            consumer,
            XReadArgs.Builder.count(1),
            XReadArgs.StreamOffset.lastConsumed(Settings.setting["key.task"] as String)
        )
        return if (content.isNotEmpty()) {
            consumedID = content[0].id
            commands.xack(
                Settings.setting["key.task"] as String,
                Settings.setting["group"] as String,
                consumedID
            )
            mqTaskAdapter.fromJson(content[0].body["task"]!!)
        } else {
            null
        }
    }

    fun produceResult(result: MQResult) {
        val body = HashMap<String, String>()
        body["result"] = mqResultAdapter.toJson(result)
        commands.xadd(Settings.setting["key.result"] as String, body)
    }
}