package com.lemonair.stream.contents.domain;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

import com.lemonair.stream.contents.dto.channel.ChannelInfoDto;

@Getter
@Table(name = "channel")
@NoArgsConstructor
@AllArgsConstructor
public class Channel {
	@Id
	private Long id;
	private String title;
	private String streamer;
	private String chattingAddress;
	private Boolean onAir;

	public Channel(String nickname) {
		this.title = nickname + "님의 방송 (^・ω・^ ) <( Commeow!)";
		this.streamer = nickname;
		this.chattingAddress = UUID.randomUUID().toString();
		this.onAir = false;
	}

	public Channel channelOn() {
		this.onAir = true;
		return this;
	}

	public Channel channelOff() {
		this.onAir = false;
		return this;
	}

	public Channel changeInfo(ChannelInfoDto infoDto) {
		this.title = infoDto.getTitle();
		return this;
	}

}