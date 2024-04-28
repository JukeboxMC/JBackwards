package org.jukeboxmc.plugin.backwards.data

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec

data class ProtocolData(
    val codec: BedrockCodec,
    val biomeDefinitionReaderType: NbtReaderType,
    val entityIdentifierReaderType: NbtReaderType,
    val blockPaletteReaderType: NbtReaderType
)
