package org.jukeboxmc.plugin.backwards.listener

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.cloudburstmc.protocol.bedrock.data.LevelEvent
import org.cloudburstmc.protocol.bedrock.data.SoundEvent
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.ContainerMixData
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.PotionMixData
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.*
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData
import org.cloudburstmc.protocol.bedrock.packet.*
import org.jukeboxmc.api.JukeboxMC
import org.jukeboxmc.api.event.EventHandler
import org.jukeboxmc.api.event.Listener
import org.jukeboxmc.plugin.backwards.util.BedrockMappingUtil
import org.jukeboxmc.plugin.backwards.util.BedrockProtocol
import org.jukeboxmc.plugin.backwards.util.BedrockResourcesUtil
import org.jukeboxmc.server.JukeboxServer
import org.jukeboxmc.server.block.JukeboxBlock
import org.jukeboxmc.server.block.palette.RuntimeDataSerializer
import org.jukeboxmc.server.event.PacketSendEvent
import org.jukeboxmc.server.extensions.toJukeboxChunk
import org.jukeboxmc.server.network.BedrockServer

/**
 * @author Kaooot
 * @version 1.0
 */
class PacketSendListener : Listener {

    @EventHandler
    fun onPacketSend(event: PacketSendEvent) {
        val packet = event.getBedrockPacket()
        val protocolVersion = event.getPlayer().getSession().codec.protocolVersion
        val condition = protocolVersion != BedrockServer.BEDROCK_CODEC.protocolVersion

        if (packet is PlayStatusPacket && packet.status == PlayStatusPacket.Status.LOGIN_FAILED_CLIENT_OLD && protocolVersion >= BedrockProtocol.getMinCodec().protocolVersion) {
            event.setCancelled(true)

            val networkSettingsPacket = NetworkSettingsPacket()
            networkSettingsPacket.clientThrottleThreshold = 0
            networkSettingsPacket.compressionAlgorithm = JukeboxServer.getInstance().getCompressionAlgorithm()
            event.getPlayer().sendPacketImmediately(networkSettingsPacket)

            JukeboxMC.getServer().getScheduler().scheduleDelayed({
                event.getPlayer().getSession().setCompression(networkSettingsPacket.compressionAlgorithm)
            }, 1)

            println("allow join although the clients protocol is older than the servers")
        }

        if (condition) {
            when (packet) {
                is AvailableEntityIdentifiersPacket -> {
                    packet.identifiers = BedrockResourcesUtil.getEntityIdentifiers(protocolVersion)
                    println("send entity identifiers for v$protocolVersion")
                }

                is BiomeDefinitionListPacket -> {
                    packet.definitions = BedrockResourcesUtil.getBiomeDefinitions(protocolVersion)
                    println("send biome definitions for v$protocolVersion")
                }

                is StartGamePacket -> {
                    packet.itemDefinitions = BedrockResourcesUtil.getItemDefinitions(protocolVersion)
                    println("send item definitions for v$protocolVersion")
                }

                is CreativeContentPacket -> {
                    val items: MutableList<ItemData> = ArrayList()
                    for (creativeItem in BedrockResourcesUtil.getCreativeItems(BedrockProtocol.getMaxCodec().protocolVersion)) {
                        items.add(BedrockMappingUtil.translateItem(protocolVersion, creativeItem, true))
                    }
                    packet.contents = items.toTypedArray()
                    println("send creative items for v$protocolVersion")
                }

                is InventoryContentPacket -> {
                    packet.contents.replaceAll { BedrockMappingUtil.translateItem(protocolVersion, it, true) }
                }

                is InventorySlotPacket -> {
                    packet.item = BedrockMappingUtil.translateItem(protocolVersion, packet.item, true)
                }

                is InventoryTransactionPacket -> {
                    val actions = ArrayList<InventoryActionData>()
                    for (action in packet.actions) {
                        var fromItem: ItemData? = null
                        var toItem: ItemData? = null
                        if (action.fromItem != null) {
                            fromItem = BedrockMappingUtil.translateItem(protocolVersion, action.fromItem, true)
                        }
                        if (action.toItem != null) {
                            toItem = BedrockMappingUtil.translateItem(protocolVersion, action.toItem, true)
                        }
                        actions.add(InventoryActionData(action.source, action.slot, fromItem, toItem, action.stackNetworkId))
                    }
                    packet.actions.clear()
                    packet.actions.addAll(actions)
                    if (packet.itemInHand != null) {
                        packet.itemInHand = BedrockMappingUtil.translateItem(protocolVersion, packet.itemInHand, true)
                    }
                }

                is MobEquipmentPacket -> {
                    packet.item = BedrockMappingUtil.translateItem(protocolVersion, packet.item, true)
                }

                is MobArmorEquipmentPacket -> {
                    packet.helmet = BedrockMappingUtil.translateItem(protocolVersion, packet.helmet, true)
                    packet.chestplate = BedrockMappingUtil.translateItem(protocolVersion, packet.chestplate, true)
                    packet.leggings = BedrockMappingUtil.translateItem(protocolVersion, packet.leggings, true)
                    packet.boots = BedrockMappingUtil.translateItem(protocolVersion, packet.boots, true)
                }

                is UpdateBlockPacket -> {
                    packet.definition = BedrockMappingUtil.translateBlock(protocolVersion, packet.definition, true)
                }

                is LevelChunkPacket -> {
                    val chunk = event.getPlayer().getWorld().getChunk(packet.chunkX, packet.chunkZ, event.getPlayer().getDimension())!!.toJukeboxChunk()
                    val buffer = Unpooled.buffer()
                    try {
                        val method = chunk::class.java.getDeclaredMethod("writeTo", ByteBuf::class.java, RuntimeDataSerializer::class.java)
                        method.isAccessible = true
                        method.invoke(chunk, buffer.retain(), object : RuntimeDataSerializer<JukeboxBlock> {
                            override fun serialize(value: JukeboxBlock): Int = BedrockMappingUtil.translateBlockRuntimeId(protocolVersion, value.getNetworkId(), true)
                        })

                        packet.data = buffer
                    } finally {
                        buffer.release()
                    }
                }

                is CraftingDataPacket -> {
                    val craftingData = ArrayList<RecipeData>()

                    for (datum in packet.craftingData) {
                        var craftingDatum = datum
                        when (craftingDatum) {
                            is CraftingRecipeData -> {
                                val ingredients = ArrayList<ItemDescriptorWithCount>()
                                val results = ArrayList<ItemData>()

                                for (ingredient in craftingDatum.ingredients) {
                                    ingredients.add(ItemDescriptorWithCount.fromItem(BedrockMappingUtil.translateItem(protocolVersion, ingredient.toItem(), true)))
                                }

                                for (result in craftingDatum.results) {
                                    results.add(BedrockMappingUtil.translateItem(protocolVersion, result, true))
                                }

                                craftingDatum.ingredients.clear()
                                craftingDatum.ingredients.addAll(ingredients)
                                craftingDatum.results.clear()
                                craftingDatum.results.addAll(results)
                            }

                            is FurnaceRecipeData -> {
                                val input = BedrockMappingUtil.translateItem(protocolVersion, ItemData.builder().definition(SimpleItemDefinition("", craftingDatum.inputId, false)).damage(craftingDatum.inputData).build(), true)
                                val result = BedrockMappingUtil.translateItem(protocolVersion, craftingDatum.result, true)

                                craftingDatum = FurnaceRecipeData.of(input.definition.runtimeId, input.damage, result, craftingDatum.tag)
                            }

                            is SmithingTransformRecipeData -> {
                                craftingDatum = SmithingTransformRecipeData.of(
                                    craftingDatum.id,
                                    ItemDescriptorWithCount.fromItem(BedrockMappingUtil.translateItem(protocolVersion, craftingDatum.template.toItem(), true)),
                                    ItemDescriptorWithCount.fromItem(BedrockMappingUtil.translateItem(protocolVersion, craftingDatum.base.toItem(), true)),
                                    ItemDescriptorWithCount.fromItem(BedrockMappingUtil.translateItem(protocolVersion, craftingDatum.addition.toItem(), true)),
                                    BedrockMappingUtil.translateItem(protocolVersion, craftingDatum.result, true),
                                    craftingDatum.tag,
                                    craftingDatum.netId
                                )
                            }

                            is SmithingTrimRecipeData -> {
                                craftingDatum = SmithingTrimRecipeData.of(
                                    craftingDatum.id,
                                    ItemDescriptorWithCount.fromItem(BedrockMappingUtil.translateItem(protocolVersion, craftingDatum.template.toItem(), true)),
                                    ItemDescriptorWithCount.fromItem(BedrockMappingUtil.translateItem(protocolVersion, craftingDatum.base.toItem(), true)),
                                    ItemDescriptorWithCount.fromItem(BedrockMappingUtil.translateItem(protocolVersion, craftingDatum.addition.toItem(), true)),
                                    craftingDatum.tag,
                                    craftingDatum.netId
                                )
                            }
                        }

                        craftingData.add(craftingDatum)
                    }

                    packet.craftingData.clear()
                    packet.craftingData.addAll(craftingData)

                    val potionMixData = ArrayList<PotionMixData>()

                    for (potionMixDatum in packet.potionMixData) {
                        val input = BedrockMappingUtil.translateItem(protocolVersion, ItemData.builder().definition(SimpleItemDefinition("", potionMixDatum.inputId, false)).damage(potionMixDatum.inputMeta).build(), true)
                        val reagent = BedrockMappingUtil.translateItem(protocolVersion, ItemData.builder().definition(SimpleItemDefinition("", potionMixDatum.reagentId, false)).damage(potionMixDatum.reagentMeta).build(), true)
                        val output = BedrockMappingUtil.translateItem(protocolVersion, ItemData.builder().definition(SimpleItemDefinition("", potionMixDatum.outputId, false)).damage(potionMixDatum.outputMeta).build(), true)

                        potionMixData.add(PotionMixData(input.definition.runtimeId, input.damage, reagent.definition.runtimeId, reagent.damage, output.definition.runtimeId, output.damage))
                    }

                    packet.potionMixData.clear()
                    packet.potionMixData.addAll(potionMixData)

                    val containerMixData = ArrayList<ContainerMixData>()

                    for (containerMixDatum in packet.containerMixData) {
                        containerMixData.add(
                            ContainerMixData(
                                BedrockMappingUtil.translateItemRuntimeId(protocolVersion, containerMixDatum.inputId, true),
                                BedrockMappingUtil.translateItemRuntimeId(protocolVersion, containerMixDatum.reagentId, true),
                                BedrockMappingUtil.translateItemRuntimeId(protocolVersion, containerMixDatum.outputId, true)
                            )
                        )
                    }

                    packet.containerMixData.clear()
                    packet.containerMixData.addAll(containerMixData)
                }
            }

            if (packet is AddPlayerPacket && packet.hand != null) {
                packet.hand = BedrockMappingUtil.translateItem(protocolVersion, packet.hand, true)
            }

            if (packet is AddItemEntityPacket && packet.itemInHand != null) {
                packet.itemInHand = BedrockMappingUtil.translateItem(protocolVersion, packet.itemInHand, true)
            }

            if (packet is LevelEventPacket && packet.type is LevelEvent && (packet.type.equals(LevelEvent.PARTICLE_DESTROY_BLOCK) || packet.type.equals(LevelEvent.PARTICLE_CRACK_BLOCK))) {
                packet.data = BedrockMappingUtil.translateBlockRuntimeId(protocolVersion, packet.data, true)
            }

            if (packet is LevelSoundEventPacket && (
                        packet.sound == SoundEvent.PLACE ||
                                packet.sound == SoundEvent.BREAK ||
                                packet.sound == SoundEvent.BREAK_BLOCK ||
                                packet.sound == SoundEvent.STEP ||
                                packet.sound == SoundEvent.HEAVY_STEP ||
                                packet.sound == SoundEvent.BABY_STEP ||
                                packet.sound == SoundEvent.JUMP ||
                                packet.sound == SoundEvent.FALL ||
                                packet.sound == SoundEvent.FALL_BIG ||
                                packet.sound == SoundEvent.FALL_SMALL
                        ) && packet.extraData > 0
            ) {
                packet.extraData = BedrockMappingUtil.translateBlockRuntimeId(protocolVersion, packet.extraData, true)
            }
        }
    }
}