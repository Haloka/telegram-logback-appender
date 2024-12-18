package io.github.haloka.telegram.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AlertFormatter {
    private static final int MAX_STACK_LINES = 8;
    private final String timezone;

    AlertFormatter(String timezone) {
        this.timezone = timezone;
    }

    public String formatError(String hostName, String hostIp, String appName, ILoggingEvent event) {
        StringBuilder sb = new StringBuilder();

        sb.append("🚨 <b>ALARM ")
            .append(Utils.formatDateTime(Instant.ofEpochMilli(event.getTimeStamp()), timezone))
            .append("</b>\n");

        appendBasicInfo(hostName, hostIp, appName, event, sb);
        appendMdcInfo(sb, event);
        appendSystemStats(sb);
        appendError(sb, event);

        if (event.getThrowableProxy() != null) {
            appendDetails(sb, event.getThrowableProxy());
        }

        sb.append("\n");
        return sb.toString();
    }

    private void appendSystemStats(StringBuilder sb) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");

        sb.append("\n\n<b>Detailed Statistics:</b>\n");

        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024);

        sb.append("<pre>");
        sb.append("Name: ").append(osName).append(", OS Version: ").append(osVersion).append(" (").append(osArch).append(")\n");

        sb.append("Memory Heap: ").append(heapUsed).append("MB/").append(heapMax).append("MB, ");
        sb.append("Non-Heap: ").append(nonHeapUsed).append("MB, ");
        sb.append("GC Count: ").append(getGCCount()).append("\n");

        sb.append("Threads Active: ").append(threadBean.getThreadCount()).append(", ");
        sb.append("Peak: ").append(threadBean.getPeakThreadCount()).append(", ");
        sb.append("Daemon: ").append(threadBean.getDaemonThreadCount()).append("\n");
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
            sb.append("System Load: ").append(String.format("%.1f%%", osBean.getProcessCpuLoad() * 100)).append("\n");
        } catch (Exception e) {
            sb.append("System Load: N/A\n");
        }

        sb.append("Uptime: ").append(formatUptime(runtimeBean.getUptime())).append(", ");
        sb.append("Started: ").append(formatStartTime(runtimeBean.getStartTime())).append(", ");
        sb.append("Args: ").append(formatJVMArgs(runtimeBean.getInputArguments()));
        sb.append("</pre>");
    }

    private long getGCCount() {
        long count = 0;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long gcCount = gcBean.getCollectionCount();
            if (gcCount != -1) {
                count += gcCount;
            }
        }
        return count;
    }

    private String formatUptime(long uptime) {
        long hours = uptime / (60 * 60 * 1000);
        long minutes = (uptime % (60 * 60 * 1000)) / (60 * 1000);
        long seconds = (uptime % (60 * 1000)) / 1000;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String formatStartTime(long startTime) {
        return Utils.formatDateTime(Instant.ofEpochMilli(startTime), timezone);
    }

    private String formatJVMArgs(List<String> args) {
        if (args.isEmpty()) return "none";
        return args.size() + " args";
    }

    private void appendBasicInfo(String hostName, String hostIp, String appName, ILoggingEvent event,
        StringBuilder sb) {
        sb.append("<b>App:</b> ")
            .append(appName)
            .append("\n");
        sb.append("<b>Host:</b> ")
            .append(hostName)
            .append(" (<code>")
            .append(hostIp)
            .append("</code>)\n");
        sb.append("<b>Level:</b> ")
            .append(event.getLevel())
            .append("\n");
        String loggerName = event.getLoggerName();
        sb.append("<b>Source:</b> ")
            .append("<code>")
            .append(loggerName)
            .append("</code>");
    }

    private void appendError(StringBuilder sb, ILoggingEvent event) {
        String message = Utils.htmlEscape(event.getFormattedMessage());
        message = message.length() > 800 ? message.substring(0, 800) + "..." : message;
        sb.append("\n\n<b>Message:</b>")
            .append("<pre>")
            .append(message)
            .append("</pre>");
    }

    private void appendMdcInfo(StringBuilder sb, ILoggingEvent event) {
        Map<String, String> mdcProps = event.getMDCPropertyMap();
        if (!mdcProps.isEmpty()) {
            sb.append("\n\n🔍 <b>Context Info:</b>\n");
            mdcProps.forEach((key, value) ->
                sb.append(Utils.htmlEscape(key)).append(": ")
                    .append("<code>").append(Utils.htmlEscape(value)).append("</code>")
            );
        }
    }

    private void appendDetails(StringBuilder sb, IThrowableProxy throwable) {
        StringBuilder sub = new StringBuilder();

        sb.append("\n\n⚠️ <b>Exception Details:</b>\n");
        sb.append("<pre>");

        sub.append("Type: ").append(throwable.getClassName()).append("\n");
        if (Utils.hasText(throwable.getMessage())) {
            sub.append("Message: ").append(Utils.htmlEscape(throwable.getMessage())).append("\n");
        }

        sub.append("\nStack Trace:\n");
        StackTraceElementProxy[] stack = throwable.getStackTraceElementProxyArray();
        List<String> relevantStack = filterStackTrace(stack);
        sub.append(String.join("\n", relevantStack));

        if (stack.length > MAX_STACK_LINES) {
            sub.append("\n... ").append(stack.length - MAX_STACK_LINES)
                .append(" more lines");
        }

        if (throwable.getCause() != null) {
            sub.append("\n\nCaused by: ")
                .append(formatCause(throwable.getCause(), "  "));
        }


        sb.append(sub.length() > 1000 ? sub.substring(0, 1000) + "..." : sub);
        sb.append("</pre>");
    }

    private List<String> filterStackTrace(StackTraceElementProxy[] stack) {
        List<String> relevantLines = new ArrayList<>();
        int count = 0;

        for (StackTraceElementProxy element : stack) {
            if (count >= MAX_STACK_LINES) {
                break;
            }

            String line = element.getStackTraceElement().toString();
            if (isRelevantStackLine(line)) {
                relevantLines.add(Utils.htmlEscape(line));
                count++;
            }
        }

        return relevantLines;
    }

    private String formatCause(IThrowableProxy cause, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(cause.getClassName());
        if (Utils.hasText(cause.getMessage())) {
            sb.append(": ").append(Utils.htmlEscape(cause.getMessage()));
        }
        sb.append("\n");

        StackTraceElementProxy[] causeStack = cause.getStackTraceElementProxyArray();
        for (int i = 0; i < Math.min(3, causeStack.length); i++) {
            sb.append(indent)
                .append("at ")
                .append(Utils.htmlEscape(causeStack[i].getStackTraceElement().toString()))
                .append("\n");
        }

        return sb.toString();
    }

    private boolean isRelevantStackLine(String line) {
        return !line.startsWith("org.springframework") &&
                !line.startsWith("java.") &&
                !line.startsWith("javax.") &&
                !line.startsWith("sun.");
    }
}
