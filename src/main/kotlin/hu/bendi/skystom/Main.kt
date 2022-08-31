package hu.bendi.skystom

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

val LOGGER: Logger = LoggerFactory.getLogger("SkyStom")

fun downloadFile(url: URL, fileName: String) {
    url.openStream().use {
        Files.copy(it, Paths.get(fileName))
    }
}

fun downloadMap(map: String) {
    val name = map.split(':')[0]
    val tag = map.split(':')[1]
    downloadFile(URL("$serverUrl/api/map/$name/$tag?key=${System.getenv("SERVER_KEY")}"), "./level.svw")
}

fun main() {
    val config = loadConfig("én")

    downloadMap(config.map)

    val server = MinecraftServer.init()

    MinecraftServer.setTerminalEnabled(config.terminal)
    MinecraftServer.setCompressionThreshold(config.compressionThreshold)
    MinecraftServer.setBrandName(config.brand)

    val instanceManager = MinecraftServer.getInstanceManager()

    val instance = instanceManager.createInstanceContainer()

    instance.chunkLoader = SvWorldLoader(File("./level.svw"))
    //Crappy hack to call the custom chunk loader's init function
    instance.chunkLoader.loadInstance(instance)

    val eventHandler = MinecraftServer.getGlobalEventHandler()

    eventHandler.addListener(PlayerLoginEvent::class.java) {
        it.setSpawningInstance(instance)
        it.player.respawnPoint = Pos(0.0, 200.0, 0.0)
        it.player.gameMode = GameMode.CREATIVE
    }

    eventHandler.addListener(PlayerSpawnEvent::class.java) {
        it.player.addEffect(Potion(PotionEffect.NIGHT_VISION, 1, 999999))
        it.player.sendMessage("Welcome on server ${config.id}!")
    }

    server.start(config.host, config.port)
}