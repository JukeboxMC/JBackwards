package org.jukeboxmc.plugin.backwards.util

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec
import org.cloudburstmc.protocol.bedrock.codec.v589.Bedrock_v589
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594
import org.cloudburstmc.protocol.bedrock.codec.v618.Bedrock_v618
import org.cloudburstmc.protocol.bedrock.codec.v622.Bedrock_v622
import org.cloudburstmc.protocol.bedrock.codec.v630.Bedrock_v630
import org.cloudburstmc.protocol.bedrock.codec.v649.Bedrock_v649

/**
 * @author Kaooot
 * @version 1.0
 */
object BedrockProtocol {

    private val supportedProtocols: MutableMap<String, BedrockCodec> = HashMap()
    private val codecByProtocol: Int2ObjectMap<BedrockCodec> = Int2ObjectOpenHashMap()
    private val minCodec: BedrockCodec
    private val maxCodec: BedrockCodec

    init {
        this.supportedProtocols["1.20.0"] = Bedrock_v589.CODEC
        this.supportedProtocols["1.20.10"] = Bedrock_v594.CODEC
        this.supportedProtocols["1.20.30"] = Bedrock_v618.CODEC
        this.supportedProtocols["1.20.40"] = Bedrock_v622.CODEC
        this.supportedProtocols["1.20.50"] = Bedrock_v630.CODEC
        this.supportedProtocols["1.20.60"] = Bedrock_v649.CODEC

        for (supportedProtocol in this.supportedProtocols) {
            this.codecByProtocol[supportedProtocol.value.protocolVersion] = supportedProtocol.value
        }

        this.minCodec = this.supportedProtocols.entries.first().value
        this.maxCodec = this.supportedProtocols.entries.last().value
    }

    fun getSupportedProtocols(): Map<String, BedrockCodec> = this.supportedProtocols

    fun getCodec(protocolVersion: Int) = this.codecByProtocol[protocolVersion]

    fun getMinCodec() = this.minCodec

    fun getMaxCodec() = this.maxCodec
}