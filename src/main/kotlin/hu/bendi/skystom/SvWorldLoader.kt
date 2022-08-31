package hu.bendi.skystom

import com.github.luben.zstd.Zstd
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.DynamicChunk
import net.minestom.server.instance.IChunkLoader
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.binary.BinaryReader
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.CompletableFuture

class SvWorldLoader(private val levelFile: File) : IChunkLoader {
    private val sections: MutableList<SvSection>
    private var bes: Array<NBTCompound>
    private val biomeIdMap: MutableMap<Int, Int>

    init {
        sections = ArrayList()
        biomeIdMap = HashMap()
        bes = Array(0) { NBTCompound.EMPTY }
    }

    override fun loadInstance(instance: Instance) {
        if (!levelFile.exists()) return
        val dest = ByteArray(MAX_FILE_SIZE)
        val src: ByteArray
        try {
            val fis = BufferedInputStream(FileInputStream(levelFile))
            src = fis.readAllBytes()
            fis.close()
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }
        Zstd.decompress(dest, src)
        val reader = BinaryReader(dest)
        val biomeCount = reader.readInt()
        val bm = MinecraftServer.getBiomeManager()
        for (i in 0 until biomeCount) {
            val biome = fromNbt(reader.readTag() as NBTCompound)!!
            val b = bm.getByName(biome.name())
            if (b != null) {
                biomeIdMap[i] = b.id()
            } else {
                MinecraftServer.getBiomeManager().addBiome(biome)
                biomeIdMap[i] = biome.id()
            }
        }
        val sectionCount = reader.readInt()
        for (i in 0 until sectionCount) {
            val x = reader.readShort()
            val y = reader.readShort()
            val z = reader.readShort()
            val filled = reader.readBoolean()
            if (filled) {
                val stateId = reader.readInt().toShort()
                val lights = readLightData(reader)
                sections.add(
                    SvSection(
                        SectionPos(x, y, z),
                        true,
                        stateId,
                        ShortArray(0),
                        ShortArray(0),
                        lights.sky,
                        lights.block,
                        reader.readUnsignedShort()
                    )
                )
            } else {
                val paletteLength = reader.readInt()
                val palette = ShortArray(paletteLength)
                val data = ShortArray(4096)
                for (j in 0 until paletteLength) {
                    palette[j] = reader.readInt().toShort()
                }
                for (j in 0..4095) {
                    data[j] = reader.readUnsignedShort().toShort()
                }
                val lights = readLightData(reader)
                sections.add(
                    SvSection(
                        SectionPos(x, y, z),
                        false,
                        0,
                        palette,
                        data,
                        lights.sky,
                        lights.block,
                        reader.readUnsignedShort()
                    )
                )
            }
        }
        val beCount = reader.readInt()
        bes = Array(beCount) {
            reader.readTag() as NBTCompound
        }
        LOGGER.info("Loaded ${sections.size} sections.")
    }

    private fun readLightData(reader: BinaryReader): LightData {
        var skyLight = ByteArray(0)
        if (reader.readBoolean()) {
            try {
                skyLight = reader.readNBytes(2048)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        var blockLight = ByteArray(0)
        if (reader.readBoolean()) {
            try {
                blockLight = reader.readNBytes(2048)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return LightData(skyLight, blockLight)
    }

    override fun loadChunk(instance: Instance, chunkX: Int, chunkZ: Int): CompletableFuture<Chunk?> {
        return CompletableFuture.supplyAsync {
            val chunk = DynamicChunk(instance, chunkX, chunkZ)
            for (s in sections) {
                if (chunkX == s.pos.x.toInt() && chunkZ == s.pos.z.toInt()) {
                    val yOffset: Int = s.pos.y * 16
                    chunk.getSection(s.pos.y.toInt()).skyLight = s.skyLight
                    chunk.getSection(s.pos.y.toInt()).blockLight = s.blockLight
                    if (s.filled) {
                        var block =
                            Block.fromStateId(s.fillStateId) ?: continue
                        val handler = MinecraftServer.getBlockManager().getHandler(block.name())
                        if (handler != null) block = block.withHandler(handler)
                        val bP =
                            chunk.getSection(s.pos.y.toInt()).biomePalette()
                        for (x in 0..15) {
                            for (y in 0..15) {
                                for (z in 0..15) {
                                    chunk.setBlock(x, y + yOffset, z, block)
                                    bP[x, y, z] = biomeIdMap[s.biomeId]!!
                                }
                            }
                        }
                    } else {
                        var i: Short = 0
                        val bP =
                            chunk.getSection(s.pos.y.toInt()).biomePalette()
                        for (x in 0..15) {
                            for (y in 0..15) {
                                for (z in 0..15) {
                                    val stateId = s.palette[s.data[i.toInt()].toInt()]
                                    i++
                                    var block =
                                        Block.fromStateId(stateId) ?: continue
                                    val handler =
                                        MinecraftServer.getBlockManager().getHandler(block.name())
                                    if (handler != null) block = block.withHandler(handler)
                                    chunk.setBlock(x, y + yOffset, z, block)
                                    bP[x, y, z] = biomeIdMap[s.biomeId]!!
                                }
                            }
                        }
                    }
                }
            }
            for (nbt in bes) {
                val x = nbt.getInt("x")!!
                val y = nbt.getInt("y")!!
                val z = nbt.getInt("z")!!

                val cX = chunkX * 16
                val cZ = chunkZ * 16
                if (x < cX || x > cX + 15 || z < cZ || z > cZ + 15) continue
                var block = chunk.getBlock(x, y, z)
                val tileEntityID = nbt.getString("id")
                if (tileEntityID != null) {
                    val handler = MinecraftServer.getBlockManager().getHandler(tileEntityID)
                    if (handler != null) block = block.withHandler(handler)
                }
                val mutableCopy = nbt.toMutableCompound()
                mutableCopy.remove("id")
                mutableCopy.remove("x")
                mutableCopy.remove("y")
                mutableCopy.remove("z")
                mutableCopy.remove("keepPacked")
                val finalBlock =
                    if (!mutableCopy.toCompound().isEmpty()) block.withNbt(mutableCopy.toCompound()) else block
                try {
                    chunk.setBlock(x, y, z, finalBlock)
                } catch (ignored: IllegalStateException) {
                }
            }
            chunk
        }
    }

    override fun saveInstance(instance: Instance): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    override fun saveChunk(chunk: Chunk): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    override fun saveChunks(chunks: Collection<Chunk>): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    override fun supportsParallelSaving(): Boolean {
        return false
    }

    override fun supportsParallelLoading(): Boolean {
        return true
    }

    companion object {
        const val MAX_FILE_SIZE = 10000000
    }
}

data class SectionPos(val x: Short, val y: Short, val z: Short)

data class SvSection(
val pos: SectionPos,
val filled: Boolean,
val fillStateId: Short,
val palette: ShortArray,
val data: ShortArray,
val skyLight: ByteArray,
val blockLight: ByteArray,
val biomeId: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SvSection

        if (pos != other.pos) return false
        if (filled != other.filled) return false
        if (fillStateId != other.fillStateId) return false
        if (!palette.contentEquals(other.palette)) return false
        if (!data.contentEquals(other.data)) return false
        if (!skyLight.contentEquals(other.skyLight)) return false
        if (!blockLight.contentEquals(other.blockLight)) return false
        if (biomeId != other.biomeId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pos.hashCode()
        result = 31 * result + filled.hashCode()
        result = 31 * result + fillStateId
        result = 31 * result + palette.contentHashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + skyLight.contentHashCode()
        result = 31 * result + blockLight.contentHashCode()
        result = 31 * result + biomeId
        return result
    }
}

data class LightData(val sky: ByteArray, val block: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LightData

        if (!sky.contentEquals(other.sky)) return false
        if (!block.contentEquals(other.block)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sky.contentHashCode()
        result = 31 * result + block.contentHashCode()
        return result
    }
}
