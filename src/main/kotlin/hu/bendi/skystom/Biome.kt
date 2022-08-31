package hu.bendi.skystom

import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.biomes.Biome
import net.minestom.server.world.biomes.Biome.Precipitation
import net.minestom.server.world.biomes.Biome.TemperatureModifier
import net.minestom.server.world.biomes.BiomeEffects
import net.minestom.server.world.biomes.BiomeEffects.*
import net.minestom.server.world.biomes.BiomeParticle
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import java.util.*

fun moodSoundFromNbt(tag: NBTCompound): MoodSound {
    return MoodSound(
        NamespaceID.from(tag.getString("sound")!!),
        tag.getInt("tick_delay")!!, tag.getInt("block_search_extent")!!, tag.getDouble("offset")!!
    )
}

fun additionsSoundFromNbt(tag: NBTCompound): AdditionsSound {
    return AdditionsSound(NamespaceID.from(tag.getString("sound")!!), tag.getDouble("tick_chance")!!)
}

fun musicFromNbt(tag: NBTCompound): Music {
    return Music(
        NamespaceID.from(tag.getString("sound")!!), tag.getInt("min_delay")!!,
        tag.getInt("max_delay")!!, tag.getBoolean("replace_current_music")!!
    )
}

fun biomeEffectsFromNbt(tag: NBTCompound): BiomeEffects {
    var foliageColor = -1
    var grassColor = -1
    var grassColorModifier = GrassColorModifier.NONE
    val biomeParticle: BiomeParticle? = null
    var ambientSound: NamespaceID? = null
    var moodSound: MoodSound? = null
    var additionsSound: AdditionsSound? = null
    var music: Music? = null
    run {
        val foliageC = tag.getInt("foliage_color")
        if (foliageC != null) foliageColor = foliageC
        val grassC = tag.getInt("grass_color")
        if (grassC != null) grassColor = grassC
        if (tag.getString("grass_color_modifier") != null) grassColorModifier = GrassColorModifier.valueOf(
            tag.getString("grass_color_modifier")!!.uppercase(Locale.getDefault())
        )
        //TODO do this
        //if (tag.get("particle") != null) biomeParticle.
        if (tag["ambient_sound"] != null) ambientSound = NamespaceID.from(tag.getString("ambient_sound")!!)
        if (tag["mood_sound"] != null) moodSound = moodSoundFromNbt(tag.getCompound("mood_sound")!!)
        if (tag["additions_sound"] != null) additionsSound =
            additionsSoundFromNbt(tag.getCompound("additions_sound")!!)
        if (tag["music"] != null) music = musicFromNbt(tag.getCompound("music")!!)
    }
    return BiomeEffects(
        tag.getInt("fog_color")!!,
        foliageColor,
        grassColor,
        tag.getInt("sky_color")!!,
        tag.getInt("water_color")!!,
        tag.getInt("water_fog_color")!!,
        grassColorModifier,
        biomeParticle,
        ambientSound,
        moodSound,
        additionsSound,
        music
    )
}

fun fromNbt(tag: NBTCompound): Biome? {
    val element = tag.getCompound("element")
    val tempModifier = tag.getString("temperature_modifier")
    var temperatureModifier = TemperatureModifier.NONE
    if (tempModifier != null) {
        temperatureModifier = TemperatureModifier.FROZEN
    }
    var effects: BiomeEffects? = Biome.PLAINS.effects()
    if (tag["effects"] != null) effects = biomeEffectsFromNbt(tag.getCompound("effects")!!)
    return Biome.builder()
        .name(NamespaceID.from(tag.getString("name")!!))
        .depth(element!!.getFloat("depth")!!)
        .temperature(element.getFloat("temperature")!!)
        .scale(element.getFloat("scale")!!)
        .downfall(element.getFloat("downfall")!!)
        .category(Biome.Category.valueOf(element.getString("category")!!.uppercase(Locale.getDefault())))
        .precipitation(Precipitation.valueOf(element.getString("precipitation")!!.uppercase(Locale.getDefault())))
        .temperatureModifier(temperatureModifier)
        .effects(effects)
        .build()
}
