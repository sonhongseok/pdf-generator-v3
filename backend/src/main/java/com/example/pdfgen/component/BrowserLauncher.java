// backend/src/main/java/com/example/pdfgen/component/BrowserLauncher.java
package com.example.pdfgen.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 애플리케이션 기동 완료(ApplicationReadyEvent) 시 브라우저를 앱 모드로 자동 실행합니다.
 *
 * 실행 우선순위:
 *   1. Chrome 앱 모드 (--app 플래그, 주소창/탭 없는 독립 창)
 *   2. Edge 앱 모드 (Chrome이 없을 경우)
 *   3. Desktop.browse() (Chrome/Edge 모두 없을 경우 기본 브라우저)
 *   4. Windows start 명령어 폴백 (jpackage 환경 대비)
 *
 * 크롬/엣지 앱 모드로 실행된 경우, 해당 브라우저 창이 닫히면
 * Spring Boot의 Graceful Shutdown을 통해 진행 중인 요청을 안전하게 마무리한 뒤
 * 백그라운드 서버도 함께 종료됩니다.
 */
@Component
public class BrowserLauncher {

    private static final Logger logger = LoggerFactory.getLogger(BrowserLauncher.class);

    private static final String APP_URL = "http://localhost:8080";

    // 브라우저 창이 닫힌 후 서버 종료까지 대기하는 시간 (밀리초)
    private static final long SHUTDOWN_DELAY_MS = 1000L;

    // Chrome 앱 모드 창 크기
    private static final String WINDOW_SIZE = "--window-size=600,540";

    // Windows 64비트/32비트 Chrome 설치 경로 후보
    private static final List<String> CHROME_PATHS = List.of(
        System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files")
            + "\\Google\\Chrome\\Application\\chrome.exe",
        System.getenv().getOrDefault("ProgramFiles(x86)", "C:\\Program Files (x86)")
            + "\\Google\\Chrome\\Application\\chrome.exe",
        System.getenv().getOrDefault("LOCALAPPDATA", "")
            + "\\Google\\Chrome\\Application\\chrome.exe"
    );

    // Windows 64비트/32비트 Edge 설치 경로 후보
    private static final List<String> EDGE_PATHS = List.of(
        System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files")
            + "\\Microsoft\\Edge\\Application\\msedge.exe",
        System.getenv().getOrDefault("ProgramFiles(x86)", "C:\\Program Files (x86)")
            + "\\Microsoft\\Edge\\Application\\msedge.exe"
    );

    // Spring Application Context (Graceful Shutdown에 사용)
    private final ConfigurableApplicationContext applicationContext;

    @Value("${app.browser.launch:false}")
    private boolean launchBrowser;

    public BrowserLauncher(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openAppModeBrowser() {
        if (!launchBrowser) {
            logger.info("[BrowserLauncher] 브라우저 자동 실행이 비활성화되어 있습니다. (app.browser.launch=false)");
            return;
        }

        logger.info("[BrowserLauncher] 앱 모드로 브라우저를 엽니다: {}", APP_URL);

        // 방법 1: Chrome 앱 모드
        String chromePath = findExecutable(CHROME_PATHS);
        if (chromePath != null) {
            Process chromeProcess = launchAppMode(chromePath, "Chrome");
            if (chromeProcess != null) {
                watchBrowserProcess(chromeProcess, "Chrome");
                return;
            }
        }

        // 방법 2: Edge 앱 모드
        String edgePath = findExecutable(EDGE_PATHS);
        if (edgePath != null) {
            Process edgeProcess = launchAppMode(edgePath, "Edge");
            if (edgeProcess != null) {
                watchBrowserProcess(edgeProcess, "Edge");
                return;
            }
        }

        // 방법 3: 기본 브라우저 (Desktop API) - 프로세스 감시 불가
        logger.warn("[BrowserLauncher] Chrome/Edge를 찾을 수 없어 기본 브라우저로 실행합니다.");
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI(APP_URL));
                logger.info("[BrowserLauncher] 기본 브라우저 실행 성공.");
                return;
            } catch (Exception desktopException) {
                logger.warn("[BrowserLauncher] Desktop API 실행 실패: {}", desktopException.getMessage());
            }
        }

        // 방법 4: Windows start 명령어 폴백 - 프로세스 감시 불가
        try {
            new ProcessBuilder("cmd.exe", "/c", "start", APP_URL).start();
            logger.info("[BrowserLauncher] Windows 'start' 명령으로 브라우저 실행 성공.");
        } catch (Exception cmdException) {
            logger.error("[BrowserLauncher] 브라우저를 열 수 없습니다. 직접 {} 에 접속해 주세요.", APP_URL);
        }
    }

    /**
     * 지정된 브라우저 실행파일로 앱 모드 창을 엽니다.
     *
     * @return 실행된 Process 객체 (성공 시), 실패 시 null
     */
    private Process launchAppMode(String executablePath, String browserName) {
        try {
            // 기존 Chrome 프로세스와 분리되어 독립적으로 실행되도록 임시 프로필 폴더 사용
            Path tempProfileDir = Files.createTempDirectory("pdfgen-" + browserName.toLowerCase() + "-profile");
            
            // [V3 패치] 브라우저 기본 다운로드 폴더를 '바탕화면'으로 고정하기 위한 Preferences 주입
            Path defaultDir = tempProfileDir.resolve("Default");
            Files.createDirectories(defaultDir);
            String desktopPath = System.getProperty("user.home") + "\\Desktop";
            String prefsJson = "{\n" +
                "  \"download\": {\n" +
                "    \"default_directory\": \"" + desktopPath.replace("\\", "\\\\") + "\",\n" +
                "    \"prompt_for_download\": true,\n" +
                "    \"directory_upgrade\": true\n" +
                "  },\n" +
                "  \"savefile\": {\n" +
                "    \"default_directory\": \"" + desktopPath.replace("\\", "\\\\") + "\"\n" +
                "  }\n" +
                "}";
            Files.writeString(defaultDir.resolve("Preferences"), prefsJson);

            Process process = new ProcessBuilder(
                executablePath,
                "--app=" + APP_URL,
                WINDOW_SIZE,
                "--disable-extensions",
                "--no-first-run",
                "--user-data-dir=" + tempProfileDir.toAbsolutePath().toString()
            ).start();
            logger.info("[BrowserLauncher] {} 앱 모드 실행 성공: {}", browserName, executablePath);
            return process;
        } catch (Exception exception) {
            logger.warn("[BrowserLauncher] {} 앱 모드 실행 실패: {}", browserName, exception.getMessage());
            return null;
        }
    }

    /**
     * 브라우저 프로세스를 감시하는 데몬 스레드를 시작합니다.
     * 브라우저 창이 닫혀 프로세스가 종료되면, Spring Boot의 Graceful Shutdown을 통해
     * 진행 중인 요청을 안전하게 마무리한 뒤 서버를 종료합니다.
     *
     * @param browserProcess 감시할 브라우저 Process 객체
     * @param browserName    로그 출력용 브라우저 이름
     */
    private void watchBrowserProcess(Process browserProcess, String browserName) {
        Thread watcherThread = new Thread(() -> {
            try {
                int exitCode = browserProcess.waitFor();
                logger.info("[BrowserLauncher] {} 창이 닫혔습니다. (종료 코드: {})", browserName, exitCode);
                logger.info("[BrowserLauncher] {}ms 후 Graceful Shutdown을 시작합니다...", SHUTDOWN_DELAY_MS);
                Thread.sleep(SHUTDOWN_DELAY_MS);
                logger.info("[BrowserLauncher] 진행 중인 요청을 마무리한 뒤 서버를 안전하게 종료합니다.");
                applicationContext.close();
            } catch (InterruptedException interruptedException) {
                logger.warn("[BrowserLauncher] 브라우저 감시 스레드가 중단되었습니다.");
                Thread.currentThread().interrupt();
            }
        });
        watcherThread.setName("browser-watcher-thread");
        watcherThread.setDaemon(true);
        watcherThread.start();
        logger.info("[BrowserLauncher] {} 창 감시를 시작합니다. 창을 닫으면 서버도 자동 종료됩니다.", browserName);
    }

    /**
     * 후보 경로 목록에서 실제로 존재하는 첫 번째 실행파일 경로를 반환합니다.
     *
     * @return 존재하는 경로 문자열, 없으면 null
     */
    private String findExecutable(List<String> candidatePaths) {
        for (String path : candidatePaths) {
            if (path != null && !path.isBlank() && Files.exists(Path.of(path))) {
                return path;
            }
        }
        return null;
    }
}
