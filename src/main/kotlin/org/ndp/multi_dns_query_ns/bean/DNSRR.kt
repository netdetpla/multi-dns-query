package org.ndp.multi_dns_query_ns.bean

data class DNSRR(
    val domain: String,
    val dnsServer: String,
    val aRecord: List<String>
)