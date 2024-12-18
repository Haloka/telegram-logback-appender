package io.github.haloka.telegram.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.haloka.telegram.logback.config.HttpConfig;
import io.github.haloka.telegram.logback.config.RateConfig;
import io.github.haloka.telegram.logback.config.GuardConfig;
import io.github.haloka.telegram.logback.config.GuardStage;
import io.github.haloka.telegram.logback.config.ThreadConfig;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TelegramAppender extends AppenderBase<ILoggingEvent> {
    private static final long ONE_MINUTE_MS = 60 * 1000;


    // Components
    ExecutorService executor;
    BlockingQueue<TelegramMessage> queue;
    HttpClient httpClient;
    RateGuard rateGuard;
    final AtomicInteger messageCount = new AtomicInteger(0);
    final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());
    final AtomicBoolean rateLimitExceeded = new AtomicBoolean(false);
    AlertFormatter formatter;
    Window window;

    // Host information
    String hostIp;
    String hostName;


    // Bot Configurations
    private String appName;
    private String apiUrl;
    private String botToken;
    private String chatId;
    private String timezone = "Asia/Tokyo";


    public void setUrl(String url) {
        this.apiUrl = url;
    }
    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }
    public void setChatId(String chatId) {
        this.chatId = chatId;
    }
    public void setAppName(String appName) {
        this.appName = appName;
    }
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    // Configurations
    private final GuardConfig guardConfig = new GuardConfig();
    private final HttpConfig httpConfig = new HttpConfig();
    private final ThreadConfig threadConfig = new ThreadConfig();
    private final RateConfig rateConfig = new RateConfig();

    // Suppression config
    public void addGuardStage(GuardStage stage) {
        guardConfig.getStages().add(stage);
    }
    public void setWindowSize(String windowSize) {
        guardConfig.setWindowSize(windowSize);
    }


    // HTTP config
    public void setHttpConnectTimeout(String timeout) {
        httpConfig.setConnectTimeout(timeout);
    }
    public void setHttpReadTimeout(String timeout) {
        httpConfig.setReadTimeout(timeout);
    }
    public void setHttpFollowRedirects(boolean follow) {
        httpConfig.setFollowRedirects(follow);
    }

    // Thread pool config
    public void setThreadCorePoolSize(int size) {
        threadConfig.setCorePoolSize(size);
    }
    public void setThreadMaxPoolSize(int size) {
        threadConfig.setMaxPoolSize(size);
    }
    public void setThreadQueueCapacity(int capacity) {
        threadConfig.setQueueCapacity(capacity);
    }
    public void setThreadKeepAliveTime(String time) {
        threadConfig.setKeepAliveTime(time);
    }

    // Rate limit config
    public void setRateLimitMaxMessages(int max) {
        rateConfig.setMaxMessagesPerMinute(max);
    }
    public void setRateLimitWindow(String window) {
        rateConfig.setWindow(window);
    }

    // Dependencies
    private ObjectMapper objectMapper = new ObjectMapper();

    public TelegramAppender() {
        initializeHostInfo();
    }

    private void initializeHostInfo() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            this.hostIp = localHost.getHostAddress();
            this.hostName = localHost.getHostName();
        } catch (UnknownHostException e) {
            this.hostIp = "unknown";
            this.hostName = "unknown";
            addError("Failed to get host information", e);
        }
    }

    @Override
    public void start() {
        if (!validateConfigurations()) {
            return;
        }

        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            addError("Invalid timezone: " + timezone, e);
            return;
        }

        initializeComponents();

        super.start();
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        try {
            long timestamp = System.currentTimeMillis();

            String groupKey = Utils.generateGroupKey(event.getMessage(), event.getThrowableProxy());
            SendDecision decision = rateGuard.shouldSendAlert(timestamp, groupKey);

            if (!decision.isShouldSend()) {
                return;
            }

            if (decision.isSuppressionNotification()) {
                queue.offer(new TelegramMessage(chatId, decision.getSuppressionMessage()));
                return;
            }

            if (!checkRateLimit(timestamp)) {
                if (!rateLimitExceeded.get()) {
                    sendAlarmLimit();
                }
                rateLimitExceeded.set(true);
                return;
            }
            rateLimitExceeded.set(false);

            String message = formatter.formatError(hostName, hostIp, appName, event);
            queue.offer(new TelegramMessage(chatId, message));
        } catch (Exception e) {
            addError("Error sending message to Telegram", e);
        }
    }

    private void sendAlarmLimit() {
        queue.offer(new TelegramMessage(
                chatId,
                "🚨<b>ERROR REPORT:</b> Rate limit reached. Messages suspended until next minute. Please check logs for details."));
    }

    private boolean checkRateLimit(long timestamp) {
        window.recordEvent(timestamp);
        return window.getEventCount(timestamp) <= rateConfig.getMaxMessagesPerMinute();
    }

    private void processMessageQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                TelegramMessage message = queue.poll(200, TimeUnit.MILLISECONDS);
                if (message != null) {
//                    System.out.println("Sending message: " + message.getText());
                    sendMessage(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                addError("Error processing message queue", e);
            }
        }
    }

    private void sendMessage(TelegramMessage message) {
        String url = MessageFormat.format(apiUrl, botToken);

        Map<String, String> headers = Map.of("Content-Type", "application/json");

        String text = message.getText();
        Map<String, String> payloadMap = Map.of(
            "chat_id", message.getChatId(),
            "text", text,
            "parse_mode", message.getParseMode()
        );

        try {
            String payLoad = objectMapper.writeValueAsString(payloadMap);
            httpClient.post(url, payLoad, headers);
        } catch (Exception e) {
            addError("Failed to send message to Telegram", e);
        }
    }


    private boolean validateConfigurations() {
        try {
            // Validate required fields
            if (apiUrl == null || botToken == null || chatId == null) {
                addError("Missing required configuration: url, botToken, or chatId");
                return false;
            }

            // Validate durations
            guardConfig.getWindow();
            httpConfig.getConnectTimeoutAsJava();
            httpConfig.getReadTimeoutAsJava();
            threadConfig.getKeepAliveTimeAsJava();
            rateConfig.getWindowAsJava();

            if (threadConfig.getMaxPoolSize() < threadConfig.getCorePoolSize()) {
                threadConfig.setMaxPoolSize(threadConfig.getCorePoolSize());
            }

            // Validate numeric values
            if (threadConfig.getCorePoolSize() < 1 ||
                threadConfig.getQueueCapacity() < 1) {
                addError("Invalid thread pool configuration");
                return false;
            }

            return true;
        } catch (Exception e) {
            addError("Configuration validation failed", e);
            return false;
        }
    }


    private void initializeComponents() {
        try {
            initializeExecutorService();
            formatter = new AlertFormatter(timezone);
            this.queue = new ArrayBlockingQueue<>(threadConfig.getQueueCapacity());
            this.httpClient = HttpClient.of(httpConfig.getConnectTimeoutAsJava(), httpConfig.getReadTimeoutAsJava(), httpConfig.isFollowRedirects());
            this.rateGuard = new RateGuard(guardConfig, timezone);
            window = new Window(
                    rateConfig.getWindowAsJava(),
                    rateConfig.getMaxMessagesPerMinute() + 1
            );
            startMessageProcessors();

//            addShutdownHook();

        } catch (Exception e) {
            addError("Failed to initialize components", e);
            throw new RuntimeException("Component initialization failed", e);
        }
    }

    private void initializeExecutorService() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("telegram-logging-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };

        this.executor = new ThreadPoolExecutor(
            threadConfig.getCorePoolSize(),
            threadConfig.getMaxPoolSize(),
            threadConfig.getKeepAliveTimeAsJava().toSeconds(),
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(threadConfig.getQueueCapacity()),
            threadFactory,
            new ThreadPoolExecutor.DiscardPolicy() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                }
            }
        );

    }

    private void startMessageProcessors() {
        int processorCount = threadConfig.getCorePoolSize();
        for (int i = 0; i < processorCount; i++) {
            executor.submit(this::processMessageQueue);
        }
    }

//    private void addShutdownHook() {
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            addInfo("Shutting down TelegramAppender...");
//            if (executor != null) {
//                executor.shutdown();
//                try {
//                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
//                        executor.shutdownNow();
//                    }
//                } catch (InterruptedException e) {
//                    executor.shutdownNow();
//                }
//            }
//        }));
//    }

}