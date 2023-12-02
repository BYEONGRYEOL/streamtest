package com.lemonair.stream.contents.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lemonair.stream.contents.service.ChannelService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
@RequestMapping("broadcasts")
@RequiredArgsConstructor
public class StreamController {
	private final ChannelService channelService;

	// @GetMapping
	// public Mono<ResponseEntity<Flux<ChannelResponseDto>>> getAllOnAirChannels() {
	// 	return channelService.getAllOnAirChannels();
	// }
	//
	// @GetMapping("/{id}")
	// public Mono<ResponseEntity<ChannelDetailResponseDto>> getChannelDetail(@PathVariable("id") Long id) {
	// 	return channelService.getChannelDetail(id);
	// }
	//
	// @PostMapping("/{streamer}/check")
	// public Mono<Boolean> checkStreamer(@PathVariable("streamer") String streamer, @RequestBody StreamerCheckRequestDto streamerCheckRequestDto) {
	// 	return channelService.checkBroadcast(streamer, streamerCheckRequestDto);
	// }

	@PostMapping("/{streamer}/onair")
	public Mono<Boolean> startBroadcast(@PathVariable("streamer") String streamer) {
		return channelService.startBroadcast(streamer);
	}

	@PostMapping("/{streamer}/offair")
	public Mono<Boolean> endBroadcast(@PathVariable("streamer") String streamer) {
		return channelService.endBroadcast(streamer);
	}
}