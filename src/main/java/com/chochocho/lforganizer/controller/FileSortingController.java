package com.chochocho.lforganizer.controller;

import com.chochocho.lforganizer.dto.ProgressEvent;
import com.chochocho.lforganizer.service.FileSortingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class FileSortingController {

    private static final Logger log = LoggerFactory.getLogger(FileSortingController.class);
    private final FileSortingService fileSortingService;
    private final ObjectMapper objectMapper; // JSON 직렬화를 위해 추가

    // SSE 타임아웃 (1시간)
    private static final Long SSE_TIMEOUT = 3600_000L;
    // --- 생성자 주입 방식 변경 ---
    public FileSortingController(FileSortingService fileSortingService, ObjectMapper objectMapper) {
        this.fileSortingService = fileSortingService;
        this.objectMapper = objectMapper; // 주입
    }

    /**
     * 파일 분류를 시작하고 진행 상황을 SSE로 스트리밍합니다.
     * 클라이언트는 이 엔드포인트로 EventSource 연결을 시도합니다.
     * @param sourceDir (선택적) 웹 UI에서 직접 입력한 소스 디렉토리 경로
     * @param targetDir (선택적) 웹 UI에서 직접 입력한 대상 디렉토리 경로
     */
    @GetMapping("/sort-and-progress")
    public SseEmitter sortAndProgress(@RequestParam(value = "sourceDir", required = false) String sourceDir,
                                      @RequestParam(value = "targetDir", required = false) String targetDir) { // targetDir 추가
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        log.info("SSE Emitter 생성됨. 타임아웃: {}ms", SSE_TIMEOUT);
        if (sourceDir != null && !sourceDir.isBlank()) {
            log.info("웹 요청으로 Source Dir 수신: {}", sourceDir);
        }
        if (targetDir != null && !targetDir.isBlank()) { // targetDir 로그 추가
            log.info("웹 요청으로 Target Dir 수신: {}", targetDir);
        }

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
            String connectMessage = "파일 분류 서버에 연결되었습니다...";
            if (sourceDir != null && !sourceDir.isBlank()) {
                connectMessage += " (Source: " + sourceDir + ")";
            }
            if (targetDir != null && !targetDir.isBlank()) { // targetDir 메시지 추가
                connectMessage += " (Target: " + targetDir + ")";
            }

            // --- 초기 메시지를 JSON 객체로 전송 ---
            ProgressEvent connectEvent = ProgressEvent.connect(connectMessage);
            String jsonMessage = objectMapper.writeValueAsString(connectEvent);
            emitter.send(SseEmitter.event().name("message").data(jsonMessage));
            // ---

        } catch (IOException e) {
            log.warn("초기 SSE 메시지 전송 실패", e);
        }

        // 비동기 서비스 호출 (sourceDir 및 targetDir 파라미터 전달)
        fileSortingService.sortFiles(emitter, sourceDir, targetDir); // targetDir 전달

        return emitter;
    }
}