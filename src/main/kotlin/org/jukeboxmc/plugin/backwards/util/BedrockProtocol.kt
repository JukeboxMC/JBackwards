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
import org.cloudburstmc.protocol.bedrock.codec.v662.Bedrock_v662
import org.cloudburstmc.protocol.bedrock.codec.v671.Bedrock_v671
import org.jukeboxmc.plugin.backwards.data.NbtReaderType
import org.jukeboxmc.plugin.backwards.data.ProtocolData

/**
 * @author Kaooot
 * @version 1.0
 */
object BedrockProtocol {

    private val supportedProtocols: MutableMap<String, ProtocolData> = HashMap()
    private val codecByProtocol: Int2ObjectMap<BedrockCodec> = Int2ObjectOpenHashMap()
    private val minCodec: BedrockCodec
    private val maxCodec: BedrockCodec

    init {
        this.supportedProtocols["1.20.0"] = ProtocolData(Bedrock_v589.CODEC, NbtReaderType.GZIP, NbtReaderType.GZIP, NbtReaderType.GZIP)
        this.supportedProtocols["1.20.10"] = ProtocolData(Bedrock_v594.CODEC, NbtReaderType.GZIP, NbtReaderType.GZIP, NbtReaderType.GZIP)
        this.supportedProtocols["1.20.30"] = ProtocolData(Bedrock_v618.CODEC, NbtReaderType.GZIP, NbtReaderType.GZIP, NbtReaderType.GZIP)
        this.supportedProtocols["1.20.40"] = ProtocolData(Bedrock_v622.CODEC, NbtReaderType.GZIP, NbtReaderType.GZIP, NbtReaderType.GZIP)
        this.supportedProtocols["1.20.50"] = ProtocolData(Bedrock_v630.CODEC, NbtReaderType.GZIP, NbtReaderType.GZIP, NbtReaderType.GZIP)
        this.supportedProtocols["1.20.60"] = ProtocolData(Bedrock_v649.CODEC, NbtReaderType.GZIP, NbtReaderType.GZIP, NbtReaderType.GZIP)
        this.supportedProtocols["1.20.70"] = ProtocolData(Bedrock_v662.CODEC, NbtReaderType.GZIP, NbtReaderType.GZIP, NbtReaderType.GZIP)
        this.supportedProtocols["1.20.80"] = ProtocolData(Bedrock_v671.CODEC, NbtReaderType.NETWORK, NbtReaderType.NETWORK, NbtReaderType.GZIP)

        for (supportedProtocol in this.supportedProtocols) {
            this.codecByProtocol[supportedProtocol.value.codec.protocolVersion] = supportedProtocol.value.codec
        }

        this.minCodec = this.supportedProtocols.entries.first().value.codec
        this.maxCodec = this.supportedProtocols.entries.last().value.codec
    }

    fun getSupportedProtocols(): Map<String, ProtocolData> = this.supportedProtocols

    fun getCodec(protocolVersion: Int) = this.codecByProtocol[protocolVersion]

    fun getMinCodec() = this.minCodec

    fun getMaxCodec() = this.maxCodec
}