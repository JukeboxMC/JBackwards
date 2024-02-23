package org.jukeboxmc.plugin.backwards

import org.jukeboxmc.api.plugin.Plugin
import org.jukeboxmc.plugin.backwards.listener.PacketReceiveListener
import org.jukeboxmc.plugin.backwards.listener.PacketSendListener

/**
 * @author Kaooot
 * @version 1.0
 */
class JBackwardsPlugin : Plugin() {

    override fun onEnable() {
        this.getServer().getPluginManager().registerListener(PacketReceiveListener())
        this.getServer().getPluginManager().registerListener(PacketSendListener())
    }
}