package com.lemonair.stream.rmtp.model.messages;

import com.lemonair.stream.rmtp.model.messages.RtmpHeader;

import io.netty.buffer.ByteBuf;

public record RtmpMessage(RtmpHeader header, ByteBuf payload) {
}