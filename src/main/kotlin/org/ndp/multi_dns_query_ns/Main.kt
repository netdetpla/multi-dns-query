package org.ndp.multi_dns_query_ns

import org.ndp.multi_dns_query_ns.bean.DNSQuery
import org.ndp.multi_dns_query_ns.bean.DNSRR
import org.ndp.multi_dns_query_ns.bean.MQResult
import org.ndp.multi_dns_query_ns.utils.Logger.logger
import org.ndp.multi_dns_query_ns.utils.RedisHandler
import org.xbill.DNS.Cache
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

class LookupTask(
    val domain: String,
    val dnsServer: String
) : Callable<LookupTask> {
    private val aRecordLookup = Lookup(domain, Type.A)
    private val resolver = SimpleResolver(dnsServer)

    init {
        resolver.setTimeout(5)
        aRecordLookup.setResolver(resolver)
        aRecordLookup.setCache(Cache())
    }

    override fun call(): LookupTask {
        aRecordLookup.run()
        return this
    }

    fun getAnswers(): List<String> {
        return if (aRecordLookup.result == Lookup.SUCCESSFUL) {
            aRecordLookup.answers.map { it.rdataToString() }
        } else {
            ArrayList()
        }
    }
}

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
        logger.info("constructing DNS queries...")
        val executor = Executors.newFixedThreadPool(32)
        val queries = ArrayList<Future<LookupTask>>()
        for (d in domains) {
            for (s in dnsServers) {
                if (s == "") continue
                queries.add(executor.submit(LookupTask(d, s)))
            }
        }
        logger.info("parsing DNS rr...")
        val results = ArrayList<DNSRR>()
        for (q in queries) {
            val l = q.get()
            val answers = l.getAnswers()
            if (answers.isNotEmpty()) {
                results.add(DNSRR(l.domain, l.dnsServer, l.getAnswers()))
            }
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