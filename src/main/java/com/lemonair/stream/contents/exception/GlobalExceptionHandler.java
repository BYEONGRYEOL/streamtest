package com.lemonair.stream.contents.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
// import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import javax.naming.AuthenticationException;
import java.util.NoSuchElementException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
	@ExceptionHandler( {IllegalArgumentException.class, NoSuchElementException.class, RuntimeException.class, AuthenticationException.class})
	public Mono<ResponseEntity<String>> handleException(Exception e){
		return Mono.just(ResponseEntity.badRequest().body(e.getMessage()));
	}
}