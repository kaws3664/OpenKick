package com.gallbladderz.openkick.core.network

import android.util.Log
import okhttp3.Dns
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 内置 DNS 优选：
 * 1. 并行向多个 DoH 服务器（阿里、腾讯、Google）解析，取候选 IP 并集；
 * 2. 对候选 IP 做 TCP(443) 连接测速，选延迟最低且可连通的 IP；
 * 3. 结果缓存 5 分钟；TLS SNI 仍是原域名，证书校验不受影响。
 */
object SmartDns : Dns {

    private const val TAG = "SmartDns"
    private val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)

    // DoH 源：前两个在国内可直连，Google 作为海外兜底（JSON API）
    private val DOH_SERVERS = listOf(
        "https://223.5.5.5/resolve",      // 阿里 DoH
        "https://120.53.53.53/dns-query", // 腾讯 DoH (DNSPod)
        "https://8.8.8.8/resolve"         // Google DoH
    )

    // Cloudflare 托管的域名：可补充已知 CF 段做候选兜底
    private val CF_FALLBACK_HOSTS = setOf("kick.com", "web.kick.com", "search.kick.com")
    private val CF_FALLBACK_PREFIXES = listOf("104.16.0.", "104.17.0.", "172.64.0.", "162.159.0.")

    private data class Entry(val ips: List<InetAddress>, val at: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val pool = Executors.newFixedThreadPool(8)

    override fun lookup(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        val cached = cache[hostname]
        if (cached != null && now - cached.at < CACHE_TTL_MS) return cached.ips

        // 过期时先用旧值立即返回（stale），后台异步刷新，避免阻塞网络请求
        if (cached != null) {
            pool.submit { runCatching { refresh(hostname) } }
            return cached.ips
        }

        val result = refresh(hostname)
        if (result != null) return result

        Log.w(TAG, "$hostname: all DoH failed, fallback to system DNS")
        return InetAddress.getAllByName(hostname).toList()
    }

    /** 重新解析并测速，返回前 2 个最快 IP（OkHttp 换 IP 重试用）；完全失败返回 null */
    private fun refresh(hostname: String): List<InetAddress>? {
        val candidates = LinkedHashSet<InetAddress>()

        if (hostname in CF_FALLBACK_HOSTS) {
            CF_FALLBACK_PREFIXES.take(3).forEach { candidates.add(InetAddress.getByName(it + "1")) }
        }

        val futures = DOH_SERVERS.map { url ->
            pool.submit(Callable { runCatching { dohQuery(url, hostname) }.getOrDefault(emptyList()) })
        }
        futures.forEach { f -> runCatching { candidates.addAll(f.get(5, TimeUnit.SECONDS)) } }

        if (candidates.isEmpty()) return null

        // TCP 443 连通测速，取延迟最低的前 2 个
        val scored = candidates.mapNotNull { ip -> probe(ip.hostAddress!!)?.let { it to ip } }
            .sortedBy { it.first }
        val result = scored.take(2).map { it.second }.ifEmpty { candidates.toList() }
        Log.i(TAG, "$hostname -> ${result.joinToString { it.hostAddress }} " +
                "(rtt: ${scored.joinToString { (t, ip) -> "${ip.hostAddress}=${t}ms" }})")
        cache[hostname] = Entry(result, System.currentTimeMillis())
        return result
    }

    private fun dohQuery(server: String, hostname: String): List<InetAddress> {
        val c = URL("$server?name=$hostname&type=A").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 3000
            c.readTimeout = 3000
            c.setRequestProperty("accept", "application/dns-json")
            val body = c.inputStream.bufferedReader().readText()
            val answers = JSONObject(body).optJSONArray("Answer") ?: return emptyList()
            return (0 until answers.length()).mapNotNull { i ->
                val a = answers.getJSONObject(i)
                if (a.optInt("type") == 1) InetAddress.getByName(a.getString("data")) else null
            }
        } finally {
            c.disconnect()
        }
    }

    /** TCP 443 连接耗时(ms)，不可达返回 null */
    private fun probe(ip: String): Long? = runCatching {
        val s = Socket()
        try {
            val t0 = System.currentTimeMillis()
            s.connect(InetSocketAddress(ip, 443), 2000)
            System.currentTimeMillis() - t0
        } finally {
            s.close()
        }
    }.getOrNull()
}
