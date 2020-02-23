package org.ndp.multi_dns_query_ns.bean

import com.squareup.moshi.Json
import org.ndp.multi_dns_query_ns.bean.DNSRR

data class MQResult(
    @Json(name = "task-id") val taskID: Int,
    val result: List<DNSRR>,
    val status: Int,
    val desc: String
)