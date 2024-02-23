package org.jukeboxmc.plugin.backwards.listener

import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket
import org.jukeboxmc.api.event.EventHandler
import org.jukeboxmc.api.event.Listener
import org.jukeboxmc.plugin.backwards.util.BedrockMappingUtil
import org.jukeboxmc.plugin.backwards.util.BedrockProtocol
import org.jukeboxmc.server.event.PacketReceiveEvent
import org.jukeboxmc.server.network.BedrockServer

/**
 * @author Kaooot
 * @version 1.0
 */
class PacketReceiveListener : Listener {

    @EventHandler
    fun onPacketReceive(event: PacketReceiveEvent) {
        val packet = event.getBedrockPacket()
        val protocolVersion = event.getPlayer().getSession().codec.protocolVersion
        val condition = protocolVersion != BedrockServer.BEDROCK_CODEC.protocolVersion

        if (packet is RequestNetworkSettingsPacket) {
            event.getPlayer().getSession().codec = BedrockProtocol.getCodec(packet.protocolVersion)
        }

        if (condition) {
            if (packet is InventoryTransactionPacket) {
                val actions = ArrayList<InventoryActionData>()
                for (action in packet.actions) {
                    var fromItem: ItemData? = null
                    var toItem: ItemData? = null
                    if (action.fromItem != null) {
                        fromItem = BedrockMappingUtil.translateItem(protocolVersion, action.fromItem, false)
                    }
                    if (action.toItem != null) {
                        toItem = BedrockMappingUtil.translateItem(protocolVersion, action.toItem, false)
                    }
                    actions.add(InventoryActionData(action.source, action.slot, fromItem, toItem, action.stackNetworkId))
                }
                packet.actions.clear()
                packet.actions.addAll(actions)
                if (packet.itemInHand != null) {
                    packet.itemInHand = BedrockMappingUtil.translateItem(protocolVersion, packet.itemInHand, false)
                }
            }
        }
    }
}