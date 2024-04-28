package org.jukeboxmc.plugin.backwards.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtUtils
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData
import org.jukeboxmc.plugin.backwards.data.NbtReaderType
import org.jukeboxmc.plugin.backwards.data.ProtocolData
import org.jukeboxmc.server.block.RuntimeBlockDefinition
import org.jukeboxmc.server.util.BlockPalette
import org.jukeboxmc.server.util.ItemPalette
import org.jukeboxmc.server.util.PaletteUtil
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.Reader
import java.util.*

/**
 * @author Kaooot
 * @version 1.0
 */
object BedrockResourcesUtil {

    private val biomeDefinitions: Int2ObjectMap<NbtMap> = Int2ObjectOpenHashMap()
    private val entityIdentifiers: Int2ObjectMap<NbtMap> = Int2ObjectOpenHashMap()
    private val itemPalettes: Int2ObjectMap<ArrayList<ItemDefinition>> = Int2ObjectOpenHashMap()
    private val creativeItems: Int2ObjectMap<ArrayList<ItemData>> = Int2ObjectOpenHashMap()

    init {
        val gson = Gson()

        for (protocol in BedrockProtocol.getSupportedProtocols()) {
            val fileVersion = protocol.key.replace(".", "_")
            val version = protocol.value.codec.minecraftVersion
            val protocolVersion = protocol.value.codec.protocolVersion
            val biomeDefinitions = this::class.java.classLoader.getResourceAsStream("bedrock/biome_definitions/biome_definitions.$fileVersion.dat") ?: throw RuntimeException("Could not find biome definitions for $version")
            val entityIdentifiers = this::class.java.classLoader.getResourceAsStream("bedrock/entity_identifiers/entity_identifiers.$fileVersion.dat") ?: throw RuntimeException("Could not find entity identifiers for $version")
            val itemPalette = this::class.java.classLoader.getResourceAsStream("bedrock/item_palette/item_palette.$fileVersion.json") ?: throw RuntimeException("Could not find item palette for $version")
            val creativeItems = this::class.java.classLoader.getResourceAsStream("bedrock/creative_items/creative_items.$fileVersion.json") ?: throw RuntimeException("Could not find creative items for $version")

            val itemDefinitions = arrayListOf<ItemDefinition>()

            itemPalette.reader().use {
                val item = gson.fromJson<List<LinkedHashMap<String, Any>>>(it)

                for (entry in item) {
                    itemDefinitions.add(SimpleItemDefinition(entry["name"] as String, (entry["id"] as Double).toInt(), false))
                }
            }

            this.biomeDefinitions[protocolVersion] = this.streamToNbtMap(biomeDefinitions, protocol.value.biomeDefinitionReaderType)
            this.entityIdentifiers[protocolVersion] = this.streamToNbtMap(entityIdentifiers, protocol.value.entityIdentifierReaderType)
            this.itemPalettes[protocolVersion] = itemDefinitions

            creativeItems.reader().use {
                val itemEntries = gson.fromJson<Map<String, List<Map<String, Any>>>>(it)
                var netIdCounter = 0
                val items = arrayListOf<ItemData>()

                for (itemEntry in itemEntries["items"]!!) {
                    val identifier = itemEntry["id"].toString()
                    var blockRuntimeId = 0

                    if (itemEntry.containsKey("block_state_b64")) {
                        blockRuntimeId = this.runtimeByBlockStateBase64(itemEntry["block_state_b64"].toString())
                    }

                    val itemBuilder = ItemData.builder()
                        .definition(SimpleItemDefinition(identifier, this.getItemDefinitions(protocolVersion).find { it.identifier == identifier }!!.runtimeId, false))
                        .blockDefinition(RuntimeBlockDefinition(blockRuntimeId))
                        .count(1)

                    if (itemEntry.containsKey("damage")) {
                        itemBuilder.damage((itemEntry["damage"] as Double).toInt())
                    }

                    val nbtTag: String? = itemEntry["nbt_b64"] as? String

                    if (nbtTag != null) {
                        ByteArrayInputStream(Base64.getDecoder().decode(nbtTag.toByteArray())).use { byteStream ->
                            NbtUtils.createReaderLE(byteStream).use { stream ->
                                itemBuilder.tag(stream.readTag() as NbtMap)
                            }
                        }
                    }

                    netIdCounter++

                    itemBuilder.netId(netIdCounter)

                    items.add(itemBuilder.build())
                }

                this.creativeItems[protocolVersion] = items
            }
        }
    }

    fun getBiomeDefinitions(protocolVersion: Int): NbtMap = this.biomeDefinitions[protocolVersion]

    fun getEntityIdentifiers(protocolVersion: Int): NbtMap = this.entityIdentifiers[protocolVersion]

    fun getItemDefinitions(protocolVersion: Int): List<ItemDefinition> = this.itemPalettes[protocolVersion] ?: ItemPalette.getItemDefinitions()

    fun getCreativeItems(protocolVersion: Int): List<ItemData> = this.creativeItems[protocolVersion] ?: PaletteUtil.getCreativeItems()

    private fun streamToNbtMap(inputStream: InputStream, readerType: NbtReaderType): NbtMap {
        if (readerType == NbtReaderType.GZIP) {
            NbtUtils.createGZIPReader(inputStream).use {
                return it.readTag() as NbtMap
            }
        } else {
            NbtUtils.createNetworkReader(inputStream).use {
                return it.readTag() as NbtMap
            }
        }
    }

    private fun runtimeByBlockStateBase64(blockStateBase64: String): Int {
        val data = Base64.getDecoder().decode(blockStateBase64)
        ByteArrayInputStream(data).use {
            NbtUtils.createReaderLE(it).use { stream ->
                val nbtMap = stream.readTag() as NbtMap
                for (blockDefinition in BlockPalette.getBlockDefinitions()) {
                    if (blockDefinition.identifier == nbtMap.getString("name") && blockDefinition.state.equals(nbtMap.getCompound("states"))) {
                        return blockDefinition.runtimeId
                    }
                }
            }
        }
        return 0
    }

    private inline fun <reified T> gsonTypeRef(): TypeToken<T> = object : TypeToken<T>() {}

    private inline fun <reified T> Gson.fromJson(reader: Reader): T = fromJson(reader, gsonTypeRef<T>())
}