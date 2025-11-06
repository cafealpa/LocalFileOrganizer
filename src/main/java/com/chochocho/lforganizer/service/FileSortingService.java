package com.chochocho.lforganizer.service;

import com.chochocho.lforganizer.dto.ProgressEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

@Service
public class FileSortingService {

    private static final Logger log = LoggerFactory.getLogger(FileSortingService.class);

    // --- 의존성 주입 방식 변경 (필드 주입 -> 생성자 주입) ---
    private final String sourceDir;
    private final String targetDir;
    private final ObjectMapper objectMapper; // JSON 직렬화를 위해 추가

    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");

    // 생성자를 통한 의존성 주입
    public FileSortingService(@Value("${file.source-dir}") String sourceDir, @Value("${file.target-dir}") String targetDir, ObjectMapper objectMapper // Spring Boot가 자동으로 주입
    ) {
        this.sourceDir = sourceDir;
        this.targetDir = targetDir;
        this.objectMapper = objectMapper;
    }
    // --- 의존성 주입 방식 변경 완료 ---


    /**
     * 지정된 소스 디렉토리의 파일을 날짜별로 분류합니다.
     * 이 메서드는 비동기로 실행됩니다.
     *
     * @param emitter 진행 상황을 클라이언트에 전송할 SseEmitter
     */
    @Async
    public void sortFiles(SseEmitter emitter, String sourceDirFromWeb, String targetDirFromWeb) {
        log.info("파일 분류 작업 시작...");

        // --- sourceDir 우선순위 결정 ---
        String effectiveSourceDir;

        if (sourceDirFromWeb != null && !sourceDirFromWeb.isBlank()) {
            effectiveSourceDir = sourceDirFromWeb;
            log.info("웹 UI에서 제공된 소스 디렉토리를 사용합니다: {}", effectiveSourceDir);
        } else {
            effectiveSourceDir = sourceDir;
            log.info("application.properties의 기본 소스 디렉토리를 사용합니다: {}", effectiveSourceDir);
        }

        String effectiveTargetDir;
        if (targetDirFromWeb != null && !targetDirFromWeb.isBlank()) {
            effectiveTargetDir = targetDirFromWeb;
            log.info("웹 UI에서 제공된 대상 디렉토리를 사용합니다: {}", effectiveTargetDir);
        } else {
            effectiveTargetDir = targetDir;
            log.info("application.properties의 기본 대상 디렉토리를 사용합니다: {}", effectiveTargetDir);
        }

        Path sourcePath = Paths.get(effectiveSourceDir);
        Path targetPathBase = Paths.get(effectiveTargetDir);

        if (!Files.isDirectory(sourcePath)) {

            sendEvent(emitter, ProgressEvent.error("소스 폴더 '" + effectiveSourceDir + "'를 찾을 수 없거나 디렉토리가 아닙니다."));
            emitter.complete();
            return;
        }

        long totalFiles = 0;
        long processedFiles = 0;
        long skippedFiles = 0;

        try (Stream<Path> stream = Files.walk(sourcePath)) {
            // 파일을 스트림으로 처리합니다.
            var filesToProcess = stream.filter(Files::isRegularFile).toList();
            totalFiles = filesToProcess.size();

            sendEvent(emitter, ProgressEvent.info("총 " + totalFiles + "개의 파일을 발견했습니다. (하위 폴더 포함, 소스: " + effectiveSourceDir + ")"));

            for (Path file : filesToProcess) {
                try {
                    // 파일명에서 날짜 추출
                    String fileName = file.getFileName().toString();
                    if (fileName.length() < 8) {

                        sendEvent(emitter, ProgressEvent.warn("'" + fileName + "'의 파일명이 yyyyMMdd로 시작하지 않아 건너뜁니다."));
                        skippedFiles++;
                        continue;
                    }

                    // yyyyMMdd 추출 및 검증
                    String datePrefix = fileName.substring(0, 8); // 파일명 첫 8자리 추출
                    LocalDate localDateTime;
                    try {
                        localDateTime = LocalDate.parse(datePrefix, DateTimeFormatter.ofPattern("yyyyMMdd"));
                    } catch (Exception e) {

                        sendEvent(emitter, ProgressEvent.error("'" + fileName + "'의 날짜 형식이 유효하지 않아 건너뜁니다."));
                        skippedFiles++;
                        continue;
                    }

                    // 날짜 기준으로 년/월 폴더 생성
                    String year = localDateTime.format(YEAR_FORMAT);
                    String mm = localDateTime.format(MONTH_FORMAT);

                    Path targetDirWithDate = targetPathBase.resolve(year).resolve(year + "_" + mm);
                    Files.createDirectories(targetDirWithDate); // 대상 폴더 생성 (존재하면 무시됨)

                    Path targetFile = targetDirWithDate.resolve(file.getFileName());

                    if (Files.exists(targetFile)) {

                        sendEvent(emitter, ProgressEvent.warn("'" + file.getFileName() + "' 파일이 대상 폴더에 이미 존재하여 건너뜁니다."));
                        skippedFiles++;
                    } else {
                        // 파일 이동
//                        Files.move(file, targetFile);
                        Files.copy(file, targetFile);

                        sendEvent(emitter, ProgressEvent.info("이동: '" + file.getFileName() + "' -> " + year + "/" + mm));
                        processedFiles++;
                    }

                } catch (IOException e) {
                    log.error("파일 처리 중 오류 발생: {}", file.getFileName(), e);

                    sendEvent(emitter, ProgressEvent.error("'" + file.getFileName() + "' 처리 중 오류 발생 - " + e.getMessage()));
                    skippedFiles++;
                }
            }

            String summary = String.format("작업 완료: 총 %d개 파일 중 %d개 이동, %d개 건너뜀.", totalFiles, processedFiles, skippedFiles);
            sendEvent(emitter, ProgressEvent.info(summary));
            log.info(summary);

            sendEvent(emitter, ProgressEvent.complete("작업을 완료 하였습니다."));

        } catch (IOException e) {
            log.error("소스 디렉토리 읽기 오류", e);
            sendEvent(emitter, ProgressEvent.error("치명적 오류: 소스 폴더를 읽는 중 오류가 발생했습니다. " + e.getMessage()));
        } finally {
            // 작업 완료 후 Emitter 종료
            emitter.complete();
            log.info("파일 분류 작업 스레드 종료 및 Emitter 완료.");
        }
    }

    /**
     * SseEmitter를 통해 ProgressEvent를 JSON으로 전송하는 헬퍼 메서드
     *
     * @param emitter Emitter 인스턴스
     * @param event   전송할 ProgressEvent 객체
     */
    private void sendEvent(SseEmitter emitter, ProgressEvent event) {
        String eventName;
        if (ProgressEvent.ERROR.equals(event.level())) {
            eventName = "error";
        } else if (ProgressEvent.COMPLETE.equals(event.level())) {
            eventName = "complete";
        } else {
            eventName = "message";
        }


        try {
            // ProgressEvent 객체를 JSON 문자열로 직렬화
            String jsonMessage = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event().name(eventName).data(jsonMessage));
        } catch (IOException e) {
            // 클라이언트 연결이 끊어진 경우 등
            log.warn("SSE 메시지 전송 실패: {}", event, e);
        }
    }
}