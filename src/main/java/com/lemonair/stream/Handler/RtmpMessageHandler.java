package com.lemonair.stream.Handler;

import static com.lemonair.stream.rmtp.model.messages.RtmpConstants.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;

import com.lemonair.stream.Amf0Rules;
import com.lemonair.stream.rmtp.model.Stream;
import com.lemonair.stream.rmtp.model.StreamContext;
import com.lemonair.stream.rmtp.model.messages.RtmpMediaMessage;
import com.lemonair.stream.rmtp.model.messages.RtmpMessage;
import com.lemonair.stream.rmtp.model.util.MessageProvider;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

// import static com.example.streamingservice.rtmp.model.messages.RtmpConstants.*;

@Slf4j
public class RtmpMessageHandler extends MessageToMessageDecoder<RtmpMessage> {

	private final StreamContext context;
	@Autowired
	WebClient webClient;
	private String currentSessionStream;
	@Value("${auth.server}")
	private String authAddress;

	public RtmpMessageHandler(StreamContext context) {
		this.context = context;
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		Stream stream = context.getStream(currentSessionStream);
		if (stream != null) {
			if (ctx.channel().id().equals(stream.getPublisher().id())) {
				stream.closeStream();
				context.deleteStream(currentSessionStream);
			}
		}
		super.handlerRemoved(ctx);
	}

	@Override
	protected void decode(ChannelHandlerContext channelHandlerContext, RtmpMessage in, List<Object> out) {
		short type = in.header().getType();
		ByteBuf payload = in.payload();

		switch (type) {
			case RTMP_MSG_COMMAND_TYPE_AMF0 -> handleCommand(channelHandlerContext, payload, out);
			case RTMP_MSG_DATA_TYPE_AMF0 -> handleData(payload);
			case RTMP_MSG_USER_CONTROL_TYPE_AUDIO,
				RTMP_MSG_USER_CONTROL_TYPE_VIDEO -> handleMedia(in);
			case RTMP_MSG_USER_CONTROL_TYPE_EVENT -> handleEvent(in);
			default -> log.info("Unsupported message/ Type id: {}", type);
		}
		// Clear ByteBUf
		payload.release();
	}

	private void handleCommand(ChannelHandlerContext ctx, ByteBuf payload, List<Object> out) {
		List<Object> decoded = Amf0Rules.decodeAll(payload);
		String command = (String)decoded.get(0);
		log.info(command + ">>>" + decoded);
		switch (command) {
			case "connect" -> onConnect(ctx, decoded);
			case "createStream" -> onCreate(ctx, decoded);
			case "publish" -> onPublish(ctx, decoded, out);
			case "play" -> onPlay(ctx, decoded);
			case "closeStream" -> onClose(ctx);
			case "deleteStream" -> onDelete(ctx);
			default -> log.info("Unsupported command type {}", command);
		}
	}

	private void onConnect(ChannelHandlerContext ctx, List<Object> message) {
		log.info("Client connection from {}, channel id is {}", ctx.channel().remoteAddress(), ctx.channel().id());

		String app = (String)((Map<String, Object>)message.get(2)).get("app");
		Integer clientEncodingFormat = (Integer)((Map<String, Object>)message.get(2)).get("objectEncoding");

		if (clientEncodingFormat != null && clientEncodingFormat == 3) {
			log.error("AMF3 format is not supported. Closing connection to {}", ctx.channel().remoteAddress());
			ctx.close();
			return;
		}

		// app = stream name
		this.currentSessionStream = app;

		// window acknowledgement size
		//log.info("Sending window ack size message");
		ctx.writeAndFlush(MessageProvider.setWindowAcknowledgement(RTMP_DEFAULT_OUTPUT_ACK_SIZE));

		// set peer bandwidth
		//log.info("Sending set peer bandwidth message");
		ctx.writeAndFlush(MessageProvider.setPeerBandwidth(RTMP_DEFAULT_OUTPUT_ACK_SIZE, 2));

		// set chunk size
		//log.info("Sending set chunk size message");
		ctx.writeAndFlush(MessageProvider.setChunkSize(RTMP_DEFAULT_CHUNK_SIZE));

		List<Object> result = new ArrayList<>();

		Amf0Rules.Amf0Object cmdObj = new Amf0Rules.Amf0Object();
		cmdObj.put("fmsVer", "FMS/3,0,1,123");
		cmdObj.put("capabilities", 31);

		Amf0Rules.Amf0Object info = new Amf0Rules.Amf0Object();
		info.put("level", "status");
		info.put("code", "NetConnection.Connect.Success");
		info.put("description", "Connection succeeded");
		info.put("objectEncoding", 0);

		result.add("_result");
		result.add(message.get(1)); //transaction id
		result.add(cmdObj);
		result.add(info);

		ctx.writeAndFlush(MessageProvider.commandMessage(result));
	}

	private void onCreate(ChannelHandlerContext ctx, List<Object> message) {
		log.info("Create stream");

		List<Object> result = new ArrayList<>();
		result.add("_result");
		result.add(message.get(1)); // transaction id
		result.add(null); // properties
		result.add(RTMP_DEFAULT_MESSAGE_STREAM_ID_VALUE); // stream id

		ctx.writeAndFlush(MessageProvider.commandMessage(result));
	}

	private void onPublish(ChannelHandlerContext ctx, List<Object> message, List<Object> output) {
		log.info("Stream publishing");
		String streamType = (String)message.get(4);
		if (!"live".equals(streamType)) {
			log.error("Stream type {} is not supported", streamType);
			ctx.channel().disconnect();
		}

		Stream stream = new Stream(currentSessionStream);
		String secret = (String)message.get(3);
		stream.setStreamKey(secret);
		stream.setPublisher(ctx.channel());
		context.addStream(stream);

		// Push stream further and handle everything(metadata, credentials, etc)
		output.add(stream);
	}

	private void onPlay(ChannelHandlerContext ctx, List<Object> message) {
		// String secret = (String) message.get(3);

		Stream stream = context.getStream(currentSessionStream);
		if (stream != null) {
			ctx.writeAndFlush(MessageProvider.userControlMessageEvent(STREAM_BEGIN));
			ctx.writeAndFlush(MessageProvider.onStatus("status", "NetStream.Play.Start", "Strat live"));

			List<Object> args = new ArrayList<>();
			args.add("|RtmpSampleAccess");
			args.add(true);
			args.add(true);
			ctx.writeAndFlush(MessageProvider.commandMessage(args));

			List<Object> metadata = new ArrayList<>();
			metadata.add("onMetaData");
			metadata.add(stream.getMetadata());
			ctx.writeAndFlush(MessageProvider.dataMessage(metadata));

			stream.addSubscriber(ctx.channel());

		} else {
			log.info("Stream doesn't exist");
			ctx.writeAndFlush(MessageProvider.onStatus("error", "NetStream.Play.StreamNotFound", "No Such Stream"));
			ctx.channel().close();
		}
	}

	private void onClose(ChannelHandlerContext ctx) {
		Stream stream = context.getStream(currentSessionStream);
		if (stream == null) {
			ctx.writeAndFlush(MessageProvider.onStatus("status", "NetStream.Unpublish.Success", "Stop publishing"));
		} else if (ctx.channel().id().equals(stream.getPublisher().id())) {
			ctx.writeAndFlush(MessageProvider.onStatus("status", "NetStream.Unpublish.Success", "Stop publishing"));
			webClient
				.post()
				.uri(authAddress + "/broadcasts/" + stream.getStreamName() + "/offair")
				.retrieve()
				.bodyToMono(Boolean.class)
				.retryWhen(Retry.fixedDelay(3, Duration.ofMillis(500)))
				.doOnError(e -> log.info(e.getMessage()))
				.onErrorReturn(Boolean.FALSE)
				.subscribeOn(Schedulers.parallel())
				.subscribe((s) -> {
					if (s) {
						log.info("방송이 종료됩니다.");
					} else {
						log.info("ContentService 서버와 통신 에러 발생");
					}
				});
			stream.closeStream();
			context.deleteStream(stream.getStreamName());
			ctx.close();
		} else {
			log.info("Subscriber closed stream");
		}
	}

	private void onDelete(ChannelHandlerContext ctx) {
		onClose(ctx);
	}

	private void handleData(ByteBuf payload) {
		List<Object> decoded = Amf0Rules.decodeAll(payload);
		String dataType = (String)decoded.get(0);
		if ("@setDataFrame".equals(dataType)) {
			// handle metadata
			Map<String, Object> metadata = (Map<String, Object>)decoded.get(2);
			metadata.remove("filesize");
			String encoder = (String)metadata.get("encoder");
			if (encoder != null && encoder.contains("obs")) {
				log.info("OBS client detected");
			}
			Stream stream = context.getStream(this.currentSessionStream);
			if (stream != null) {
				log.info("Stream metadata set");
				stream.setMetadata(metadata);
			}
		}
	}

	private void handleMedia(RtmpMessage message) {
		Stream stream = context.getStream(currentSessionStream);
		if (stream != null) {
			stream.addMedia(RtmpMediaMessage.fromRtmpMessage(message));
		} else {
			log.info("Stream does not exist");
		}
	}

	private void handleEvent(RtmpMessage message) {
		log.info("User event type {}, value {}", message.payload().readShort(), message.payload().readInt());
	}
}