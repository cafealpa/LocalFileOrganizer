package com.chochocho.lforganizer.controller;

import com.chochocho.lforganizer.service.FileSortingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class FileSortingController {

    private static final Logger log = LoggerFactory.getLogger(FileSortingController.class);
    private final FileSortingService fileSortingService;

    // SSE 타임아웃 (1시간)
    private static final Long SSE_TIMEOUT = 3600_000L;

    public FileSortingController(FileSortingService fileSortingService) {
        this.fileSortingService = fileSortingService;
    }

    /**
     * 파일 분류를 시작하고 진행 상황을 SSE로 스트리밍합니다.
     * 클라이언트는 이 엔드포인트로 EventSource 연결을 시도합니다.
     */
    @GetMapping("/sort-and-progress")
    public SseEmitter sortAndProgress() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        log.info("SSE Emitter 생성됨. 타임아웃: {}ms", SSE_TIMEOUT);

        // Emitter 완료 또는 타임아웃 시 로그
        emitter.onCompletion(() -> log.info("SSE Emitter 완료됨."));
        emitter.onTimeout(() -> {
            log.warn("SSE Emitter 타임아웃.");
            emitter.complete();
        });
        emitter.onError(e -> {
            log.error("SSE Emitter 오류 발생.", e);
            emitter.completeWithError(e);
        });

        // 비동기 서비스 호출
        // 컨트롤러 스레드는 즉시 emitter를 반환하고,
        // 별도 스레드에서 파일 분류 작업이 시작됩니다.
        try {
            // 초기 연결 메시지
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data("파일 분류 서버에 연결되었습니다... 작업 시작 대기 중."));
        } catch (IOException e) {
            log.warn("초기 SSE 메시지 전송 실패", e);
        }

        fileSortingService.sortFiles(emitter);

        return emitter;
    }
}