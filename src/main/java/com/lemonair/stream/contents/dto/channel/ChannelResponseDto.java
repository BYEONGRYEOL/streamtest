package com.lemonair.stream.contents.dto.channel;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lemonair.stream.contents.domain.Channel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Slf4j(topic = "ChannelResponseDto")
public class ChannelResponseDto {
	private Long channelId;
	private String streamer;
	private String title;
	private List<Map<String, String>> thumbnailFiles;

	public ChannelResponseDto(Channel channel) {
		this.channelId = channel.getId();
		this.streamer = channel.getStreamer();
		this.title = channel.getTitle();
		this.thumbnailFiles = findThumbnailFiles(channel);
	}

	private List<Map<String, String>> findThumbnailFiles(Channel channel) {
		String owner = channel.getStreamer();
		String thumbnailPath = String.format("/home/streams/%s/thumbnail/", owner);

		// 일치하는 파일 이름을 필터링하는 FilenameFilter 생성
		FilenameFilter filter = (dir, name) -> name.matches(String.format("%s_thumbnail_\\d{4}.jpg", owner));

		// 지정된 디렉토리에서 파일 필터링
		File directory = new File(thumbnailPath);
		File[] files = directory.listFiles(filter);

		List<Map<String, String>> thumbnailFiles = new ArrayList<>();
		if (files != null) {
			for (File file : files) {
				Map<String, String> fileInfo = new HashMap<>();
				fileInfo.put("fileName", file.getName());

				try {
					byte[] fileImage = Files.readAllBytes(file.toPath());
					String encodedFileImage = Base64.getEncoder().encodeToString(fileImage);
					fileInfo.put("fileImg", encodedFileImage);
				} catch (IOException e) {
					log.error("IOException 발생 :" + e);
				}
				thumbnailFiles.add(fileInfo);
			}
		}
		return thumbnailFiles;
	}
}