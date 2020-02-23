package org.ndp.multi_dns_query_ns

import org.ndp.multi_dns_query_ns.bean.DNSQuery
import org.ndp.multi_dns_query_ns.bean.DNSRR
import org.ndp.multi_dns_query_ns.bean.MQResult
import org.ndp.multi_dns_query_ns.utils.Logger.logger
import org.ndp.multi_dns_query_ns.utils.RedisHandler
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.io.PrintWriter
import java.io.StringWriter

object Main {

    private val task = RedisHandler.consumeTaskParam(
        RedisHandler.generateNonce(5)
    )
    private val domains = ArrayList<String>()
    private val dnsServers = ArrayList<String>()

    private fun parseParam() {
        logger.info("parsing task param...")
        val param = task!!.param.split(",")
        domains.addAll(param[1].split("+"))
        dnsServers.addAll(param[2].split("+"))
//        dnsServers.add(param[2].split("+")[0])
    }

    private fun execute(): List<DNSRR> {
        val dnsQueries = ArrayList<DNSQuery>()
        logger.info("constructing DNS queries...")
        for (d in domains) {
            for (s in dnsServers) {
                if (s == "") continue
                val aRecordLookup = Lookup(d, Type.A)
                aRecordLookup.setResolver(SimpleResolver(s))
                aRecordLookup.run()
                val cNameLookup = Lookup(d, Type.CNAME)
                cNameLookup.setResolver(SimpleResolver(s))
                cNameLookup.run()
                dnsQueries.add(DNSQuery(d, s, aRecordLookup, cNameLookup))
            }
        }
        logger.info("parsing DNS rr...")
        val results = ArrayList<DNSRR>()
        for (q in dnsQueries) {
            val aRecords = ArrayList<String>()
            val cNames = ArrayList<String>()
            // a record
            if (q.aRecordLookup.result == Lookup.SUCCESSFUL) {
                aRecords.addAll(
                    q.aRecordLookup.answers.map { it.rdataToString() }
                )
            }
            // cname
            if (q.cNameLookup.result == Lookup.SUCCESSFUL) {
                cNames.addAll(
                    q.cNameLookup.answers.map { it.rdataToString() }
                )
            }
            results.add(DNSRR(q.domain, q.dnsServer, aRecords, cNames))
        }
        return results
    }

    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("dns-query started")
        if (task == null || task.taskID == 0) {
            logger.warn("no task, exiting...")
            return
        }
        try {
            parseParam()
            val results = execute()
            RedisHandler.produceResult(
                MQResult(task.taskID, results, 0, "")
            )
        } catch (e: Exception) {
            logger.error(e.toString())
            val stringWriter = StringWriter()
            e.printStackTrace(PrintWriter(stringWriter))
            RedisHandler.produceResult(
                MQResult(task.taskID, ArrayList(), 1, stringWriter.buffer.toString())
            )
        }
        logger.info("dns-query end successfully")
    }
}