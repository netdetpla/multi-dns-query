package org.ndp.multi_dns_query_ns.bean

import org.xbill.DNS.Lookup

data class DNSQuery(
    val domain: String,
    val dnsServer: String,
    val aRecordLookup: Lookup,
    val cNameLookup: Lookup
)