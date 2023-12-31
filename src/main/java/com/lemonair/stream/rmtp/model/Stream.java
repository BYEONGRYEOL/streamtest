package com.lemonair.stream.rmtp.model;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;

import com.lemonair.stream.rmtp.model.messages.RtmpConstants;
import com.lemonair.stream.rmtp.model.messages.RtmpMediaMessage;
import com.lemonair.stream.rmtp.model.messages.RtmpMessage;
import com.lemonair.stream.rmtp.model.util.MessageProvider;

@Getter
@Setter
@Slf4j
public class Stream {

	private Map<String, Object> metadata;

	private Channel publisher;

	private final Set<Channel> subscribers;
	private final String streamName;
	private String streamKey;

	private final BlockingQueue<RtmpMediaMessage> rtmpGopCache;

	private RtmpMediaMessage videoConfig;
	private RtmpMediaMessage audioConfig;

	private CompletableFuture<Boolean> readyToBroadcast;

	public Stream(String streamName) {
		this.streamName = streamName;
		this.subscribers = new LinkedHashSet<>();
		this.rtmpGopCache = new ArrayBlockingQueue<>(1024);
		this.readyToBroadcast = new CompletableFuture<>();
	}

	public void addMedia(RtmpMediaMessage message) {
		short type = message.header().getType();

		if (type == (short) RtmpConstants.RTMP_MSG_USER_CONTROL_TYPE_AUDIO) {
			if (message.isAudioConfig()) {
				log.info("Audio config is set");
				audioConfig = message;
			}
		} else if (type == (short) RtmpConstants.RTMP_MSG_USER_CONTROL_TYPE_VIDEO) {
			if (message.isVideoConfig()) {
				log.info("Video config is set");
				videoConfig = message;
			}
			// clear interFrames queue
			if (message.isKeyframe()) {
				log.info("Keyframe added. {} frames cleared", rtmpGopCache.size());
				rtmpGopCache.clear();
			}
		}
		rtmpGopCache.add(message);
		broadcastMessage(message);
	}

	public void broadcastMessage(RtmpMediaMessage message) {
		if (!readyToBroadcast.isDone()) {
			readyToBroadcast.complete(Boolean.TRUE);
		}
		Iterator<Channel> channelIterator = subscribers.iterator();
		while (channelIterator.hasNext()) {
			Channel next = channelIterator.next();
			if (next.isActive()) {
				next.writeAndFlush(RtmpMediaMessage.toRtmpMessage(message));
			} else {
				log.info("Inactive channel detected");
				channelIterator.remove();
			}
		}
	}

	public void addSubscriber(Channel channel) {
		log.info("Subscriber {} added to stream {}", channel.remoteAddress(), streamName);
		subscribers.add(channel);

		channel.writeAndFlush(RtmpMediaMessage.toRtmpMessage(videoConfig));
		channel.writeAndFlush(RtmpMediaMessage.toRtmpMessage(audioConfig));

		log.info("Sending group of pictures to client");
		for (RtmpMediaMessage message : rtmpGopCache) {
			channel.writeAndFlush(RtmpMediaMessage.toRtmpMessage(message));
		}
	}

	public void closeStream() {
		log.info("Closing stream");
		RtmpMessage eof = MessageProvider.userControlMessageEvent(RtmpConstants.STREAM_EOF);
		for (Channel channel : subscribers) {
			channel.writeAndFlush(eof).addListener(ChannelFutureListener.CLOSE);
		}
	}

	public void sendPublishMessage() {
		publisher.writeAndFlush(MessageProvider.onStatus(
			"status",
			"NetStream.Publish.Start",
			"Start publishing"));
	}

}