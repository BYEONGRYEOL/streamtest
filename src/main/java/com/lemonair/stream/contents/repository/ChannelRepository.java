package com.lemonair.stream.contents.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.lemonair.stream.contents.domain.Channel;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ChannelRepository extends ReactiveCrudRepository<Channel, Long> {
	
	// Reactive Programming Model 사용시 db data 반환 형식
	// Flux : List<T>
	// Mono : Optional<T>


	Flux<Channel> findAllByOnAirTrue();
	Mono<Channel> findByStreamer(String streamer);
	@Query("SELECT c.* FROM channel c JOIN member m ON c.streamer = m.nickname WHERE m.user_id = :id")
	Mono<Channel> findById(String id);
}