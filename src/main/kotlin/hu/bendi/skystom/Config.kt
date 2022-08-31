package hu.bendi.skystom

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

@Serializable
data class Config(val id: String, val terminal: Boolean, val compressionThreshold: Int, val extensions: List<String>?, val map: String, val host: String, val port: Int, val brand: String)

val serverUrl = System.getenv("SERVER_URL") ?: "http://localhost:8080"

fun loadConfig(id: String): Config {
    LOGGER.info("Downloading server configuration.")
    val conn = URL("$serverUrl/api/server/$id").openConnection()

    conn.setRequestProperty("Authorization", System.getenv("SERVER_KEY"))

    var result = ""

    BufferedReader(InputStreamReader(conn.inputStream)).use { br ->
        var line: String?
        while (br.readLine().also { line = it } != null) {
            result += line
        }
    }

    LOGGER.info("Downloaded server config for \"$id\".")
    return Json.decodeFromString(result)
}
