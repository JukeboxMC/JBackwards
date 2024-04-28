package org.jukeboxmc.plugin.backwards.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtType
import org.cloudburstmc.nbt.NbtUtils
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData
import org.jukeboxmc.api.block.Block
import org.jukeboxmc.api.block.BlockType
import org.jukeboxmc.api.item.Item
import org.jukeboxmc.api.item.ItemType
import org.jukeboxmc.mapping.ItemMappingGenerator
import org.jukeboxmc.server.extensions.toJukeboxItem
import org.jukeboxmc.server.util.BlockPalette
import java.io.Reader
import java.util.*

/**
 * @author Kaooot
 * @version 1.0
 */
object BedrockMappingUtil {

    private val itemMappings: Int2ObjectMap<List<ItemMappingGenerator.MappedItemEntry>> = Int2ObjectOpenHashMap()
    private val blockMappings: Int2ObjectMap<Int2IntMap> = Int2ObjectOpenHashMap()
    private val blockMappingsReversed: Int2ObjectMap<Int2IntMap> = Int2ObjectOpenHashMap()
    private val itemRuntimeMappings: Int2ObjectMap<Int2IntMap> = Int2ObjectOpenHashMap()
    private val itemRuntimeMappingsReversed: Int2ObjectMap<Int2IntMap> = Int2ObjectOpenHashMap()
    private val blockStates: Int2ObjectMap<Int2ObjectMap<NbtMap>> = Int2ObjectOpenHashMap()
    private val itemPlaceholderRuntimeIds: Int2ObjectMap<Int2IntMap> = Int2ObjectOpenHashMap()
    private val blockPlaceholderRuntimeIds: Int2ObjectMap<Int2IntMap> = Int2ObjectOpenHashMap()
    private const val placeHolderId = "minecraft:stone"
    private val placeHolderItemId: Int = Item.create(ItemType.STONE).getNetworkId()
    private val placeHolderBlockId: Int = Block.create(BlockType.STONE).getNetworkId()
    private val placeHolderItemDataBuilder = Item.create(ItemType.STONE).toJukeboxItem().toItemData().toBuilder()
    private val placeHolderBlockDefinition = this.placeHolderItemDataBuilder.build().blockDefinition!!

    init {
        val gson = Gson()
        val maxCodecProtocol = BedrockProtocol.getMaxCodec().protocolVersion

        for (protocol in BedrockProtocol.getSupportedProtocols()) {
            if (protocol.key == BedrockProtocol.getMaxCodec().minecraftVersion) {
                continue
            }

            val protocolVersion = protocol.value.codec.protocolVersion
            val itemMappings = this::class.java.classLoader.getResourceAsStream("bedrock/mapping_items/item_mapping_${protocolVersion}_to_${maxCodecProtocol}.json") ?: throw RuntimeException("Could not find item mappings for ${protocol.key}")

            itemMappings.reader().use {
                val mappings = gson.fromJson<List<ItemMappingGenerator.MappedItemEntry>>(it)

                this.itemMappings[protocolVersion] = mappings

                val runtimeMappings = Int2IntOpenHashMap()
                val runtimeMappingsReversed = Int2IntOpenHashMap()

                for (mapping in mappings) {
                    if (mapping.getSource().getName() == "minecraft:stone") {
                        this.itemPlaceholderRuntimeIds[protocolVersion] = Int2IntOpenHashMap(Collections.singletonMap(mapping.getTarget().getId(), mapping.getSource().getId()))
                    }

                    runtimeMappings[mapping.getTarget().getId()] = mapping.getSource().getId()
                    runtimeMappingsReversed[mapping.getSource().getId()] = mapping.getTarget().getId()
                }

                this.itemRuntimeMappings[protocolVersion] = runtimeMappings
                this.itemRuntimeMappingsReversed[protocolVersion] = runtimeMappingsReversed
            }

            val fileVersion = protocol.key.replace(".", "_")
            val blockPalette = this::class.java.classLoader.getResourceAsStream("bedrock/block_palette/block_palette.${fileVersion}.nbt")
            val blockStates: Int2ObjectMap<NbtMap> = Int2ObjectOpenHashMap()

            blockPalette.use {
                NbtUtils.createGZIPReader(it).use { stream ->
                    val states = stream.readTag() as NbtMap

                    for (nbtMap in states.getList("blocks", NbtType.COMPOUND)) {
                        blockStates[nbtMap.getInt("network_id")] = nbtMap
                    }
                }
            }

            this.blockStates[protocolVersion] = blockStates

            val blockMappings = this::class.java.classLoader.getResourceAsStream("bedrock/mapping_blocks/block_mapping_${protocolVersion}_to_${maxCodecProtocol}.json") ?: throw RuntimeException("Could not find block mappings for ${protocol.key}")

            blockMappings.reader().use {
                val mappings = Int2IntOpenHashMap(gson.fromJson<Map<Int, Int>>(it))

                this.blockMappings[protocolVersion] = mappings

                val reversedMappings = Int2IntOpenHashMap()

                for (mapping in mappings) {
                    if (mapping.value == this.placeHolderBlockId) {
                        this.blockPlaceholderRuntimeIds[protocolVersion] = Int2IntOpenHashMap(Collections.singletonMap(mapping.value, mapping.key))
                    }
                    reversedMappings[mapping.value] = mapping.key
                }

                this.blockMappingsReversed[protocolVersion] = reversedMappings
            }
        }
    }

    fun translateBlockRuntimeId(protocolVersion: Int, runtimeId: Int, send: Boolean): Int {
        if (!this.blockMappingsReversed[protocolVersion].containsKey(runtimeId) && !this.blockMappings[protocolVersion].containsKey(runtimeId)) {
            return if (!send) this.placeHolderBlockId else this.blockPlaceholderRuntimeIds[protocolVersion][this.placeHolderBlockId]
        }

        return if (send) this.blockMappingsReversed[protocolVersion][runtimeId] else this.blockMappings[protocolVersion][runtimeId]
    }

    fun translateItemRuntimeId(protocolVersion: Int, runtimeId: Int, send: Boolean): Int {
        if (!this.itemRuntimeMappingsReversed[protocolVersion].containsKey(runtimeId) && !this.itemRuntimeMappings[protocolVersion].containsKey(runtimeId)) {
            return if (!send) this.placeHolderItemId else this.itemPlaceholderRuntimeIds[protocolVersion][this.placeHolderItemId]
        }

        return if (send) this.itemRuntimeMappingsReversed[protocolVersion][runtimeId] else this.itemRuntimeMappings[protocolVersion][runtimeId]
    }

    fun translateBlock(protocolVersion: Int, blockDefinition: BlockDefinition, send: Boolean): BlockDefinition {
        if (!this.blockMappingsReversed[protocolVersion].containsKey(blockDefinition.runtimeId) && !this.blockMappings[protocolVersion].containsKey(blockDefinition.runtimeId)) {
            return if (!send) this.placeHolderBlockDefinition else SimpleBlockDefinition(
                this.placeHolderId, this.blockPlaceholderRuntimeIds[protocolVersion][this.placeHolderBlockId],
                this.blockStates[protocolVersion][this.blockPlaceholderRuntimeIds[protocolVersion][this.placeHolderBlockId]].getCompound("states")
            )
        }

        val runtimeId = if (send) this.blockMappingsReversed[protocolVersion][blockDefinition.runtimeId] else this.blockMappings[protocolVersion][blockDefinition.runtimeId]

        return if (!send) {
            BlockPalette.getBlockDefinitions().find { it.runtimeId == runtimeId }!!
        } else {
            SimpleBlockDefinition(this.blockStates[protocolVersion][runtimeId].getString("name"), runtimeId, this.blockStates[protocolVersion][runtimeId].getCompound("states"))
        }
    }

    fun translateItem(protocolVersion: Int, itemData: ItemData, send: Boolean): ItemData {
        for (entry in this.itemMappings[protocolVersion]) {
            if (send && (itemData.definition.identifier.isEmpty() || entry.getTarget().getName() == itemData.definition.identifier) && entry.getTarget().getId() == itemData.definition.runtimeId) {
                var damage = -1
                if (entry.getRemappedMetas() != null && entry.getRemappedMetas()!!.containsValue(entry.getTarget().getName())) {
                    damage = entry.getRemappedMetas()!!.entries.stream()
                        .filter { it.value == entry.getTarget().getName() }
                        .map { it.key }
                        .findAny()
                        .orElse(-1)
                }
                return itemData.toBuilder()
                    .definition(SimpleItemDefinition(entry.getSource().getName(), entry.getSource().getId(), false))
                    .blockDefinition(itemData.blockDefinition?.let { this.translateBlock(protocolVersion, it, true) })
                    .damage(if (damage == -1) itemData.damage else damage)
                    .build()
            } else if (!send && (itemData.definition.identifier.isEmpty() || entry.getSource().getName() == itemData.definition.identifier) && entry.getSource().getId() == itemData.definition.runtimeId) {
                var identifier = entry.getTarget().getName()
                var damage = -1
                if (entry.getRemappedMetas() != null && entry.getRemappedMetas()!!.containsKey(itemData.damage)) {
                    entry.getRemappedMetas()!!.entries.stream()
                        .filter { it.key == itemData.damage }
                        .map { it.value }
                        .findAny()
                        .ifPresent {
                            identifier = it
                            damage = 0
                        }
                }
                return itemData.toBuilder()
                    .definition(SimpleItemDefinition(identifier, entry.getTarget().getId(), false))
                    .blockDefinition(itemData.blockDefinition?.let { this.translateBlock(protocolVersion, it, false) })
                    .damage(if (damage == -1) itemData.damage else damage)
                    .build()
            }
        }
        return itemData
    }

    private inline fun <reified T> gsonTypeRef(): TypeToken<T> = object : TypeToken<T>() {}

    private inline fun <reified T> Gson.fromJson(reader: Reader): T = fromJson(reader, gsonTypeRef<T>())
}