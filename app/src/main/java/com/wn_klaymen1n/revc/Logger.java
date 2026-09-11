package com.wn_klaymen1n.revc;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Centralized logger for the REVC application.
 *
 * Features:
 * - Log levels: VERBOSE, DEBUG, INFO, WARN, ERROR
 * - Dual output: Android Logcat + persistent file on external storage
 * - Automatic timestamps, thread names, and caller class/method info
 * - Asynchronous file writes to avoid blocking the main thread
 * - Configurable minimum log level at runtime
 */
public final class Logger {

    public static final int VERBOSE = Log.VERBOSE; // 2
    public static final int DEBUG   = Log.DEBUG;   // 3
    public static final int INFO    = Log.INFO;    // 4
    public static final int WARN    = Log.WARN;    // 5
    public static final int ERROR   = Log.ERROR;   // 6
    public static final int NONE    = 7;

    private static final String TAG_PREFIX = "REVC";
    private static final String LOG_DIR_NAME = "revc_logs";
    private static final String LOG_FILE_NAME = "revc.log";
    private static final int MAX_LOG_FILE_SIZE = 2 * 1024 * 1024; // 2 MB, rotate after this

    private static volatile int minLevel = DEBUG;
    private static volatile boolean fileLoggingEnabled = true;

    private static final SimpleDateFormat TIMESTAMP_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static final LinkedBlockingQueue<String> logQueue = new LinkedBlockingQueue<>(4096);
    private static Thread writerThread;
    private static volatile boolean writerRunning;
    private static File currentLogFile;

    private Logger() {}

    // ── Initialization ──────────────────────────────────────────────

    /**
     * Initialize file logging. Call once from Application or first Activity onCreate.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    public static synchronized void init() {
        if (writerRunning) return;

        File logDir = getLogDir();
        if (logDir == null) {
            fileLoggingEnabled = false;
            Log.w(TAG_PREFIX, "Logger: cannot create log directory, file logging disabled");
            return;
        }

        currentLogFile = new File(logDir, LOG_FILE_NAME);
        rotateIfNeeded();

        writerRunning = true;
        writerThread = new Thread(Logger::writerLoop, "revc-log-writer");
        writerThread.setDaemon(true);
        writerThread.start();

        Log.i(TAG_PREFIX, "Logger initialized. Log file: " + currentLogFile.getAbsolutePath());
        writeToFileInternal("=== Logger initialized ===");
    }

    /**
     * Set the minimum log level. Messages below this level are discarded.
     */
    public static void setMinLevel(int level) {
        minLevel = level;
    }

    /**
     * Enable or disable file logging at runtime.
     */
    public static void setFileLoggingEnabled(boolean enabled) {
        fileLoggingEnabled = enabled;
    }

    // ── Public log methods ──────────────────────────────────────────

    public static void v(String tag, String msg) {
        log(VERBOSE, tag, msg, null);
    }

    public static void d(String tag, String msg) {
        log(DEBUG, tag, msg, null);
    }

    public static void i(String tag, String msg) {
        log(INFO, tag, msg, null);
    }

    public static void w(String tag, String msg) {
        log(WARN, tag, msg, null);
    }

    public static void w(String tag, String msg, Throwable tr) {
        log(WARN, tag, msg, tr);
    }

    public static void e(String tag, String msg) {
        log(ERROR, tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable tr) {
        log(ERROR, tag, msg, tr);
    }

    /**
     * Convenience: log an exception at ERROR level.
     */
    public static void e(String tag, Throwable tr) {
        log(ERROR, tag, tr.getMessage() != null ? tr.getMessage() : tr.getClass().getSimpleName(), tr);
    }

    // ── Core ────────────────────────────────────────────────────────

    private static void log(int level, String tag, String msg, Throwable tr) {
        if (level < minLevel) return;

        String fullTag = TAG_PREFIX + "/" + tag;
        String timestamp = TIMESTAMP_FMT.format(new Date());
        String threadName = Thread.currentThread().getName();

        // Build the logcat line
        String logcatLine = "[" + timestamp + "] [" + threadName + "] " + msg;

        // Output to Logcat
        switch (level) {
            case VERBOSE: Log.v(fullTag, logcatLine); break;
            case DEBUG:   Log.d(fullTag, logcatLine); break;
            case INFO:    Log.i(fullTag, logcatLine); break;
            case WARN:    Log.w(fullTag, logcatLine); break;
            case ERROR:
                if (tr != null) {
                    Log.e(fullTag, logcatLine, tr);
                } else {
                    Log.e(fullTag, logcatLine);
                }
                break;
        }

        // Build file line and enqueue
        if (fileLoggingEnabled && writerRunning) {
            String levelStr = levelToString(level);
            StringBuilder sb = new StringBuilder(128);
            sb.append('[').append(timestamp).append(']')
              .append(" [").append(levelStr).append(']')
              .append(" [").append(threadName).append(']')
              .append(" [").append(tag).append(']')
              .append(' ').append(msg);

            if (tr != null) {
                sb.append('\n').append(getStackTraceString(tr));
            }

            String fileLine = sb.toString();
            if (!logQueue.offer(fileLine)) {
                // Queue full — drop the oldest to make room
                logQueue.poll();
                logQueue.offer(fileLine);
            }
        }
    }

    // ── File writer ─────────────────────────────────────────────────

    private static void writerLoop() {
        while (writerRunning) {
            try {
                String line = logQueue.take(); // blocks until available
                writeToFileInternal(line);
                // Drain any additional queued lines without blocking
                String extra;
                while ((extra = logQueue.poll()) != null) {
                    writeToFileInternal(extra);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // Final drain
        String remaining;
        while ((remaining = logQueue.poll()) != null) {
            writeToFileInternal(remaining);
        }
    }

    private static void writeToFileInternal(String line) {
        if (currentLogFile == null) return;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(currentLogFile, true))) {
            bw.write(line);
            bw.newLine();
        } catch (IOException e) {
            // Last resort — can't log a logging failure to the file
            Log.e(TAG_PREFIX, "Failed to write to log file", e);
        }
    }

    private static void rotateIfNeeded() {
        if (currentLogFile != null && currentLogFile.exists()
                && currentLogFile.length() > MAX_LOG_FILE_SIZE) {
            File backup = new File(currentLogFile.getParent(),
                    "revc.log." + System.currentTimeMillis() + ".bak");
            //noinspection ResultOfMethodCallIgnored
            currentLogFile.renameTo(backup);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static File getLogDir() {
        try {
            File dir;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                    && Environment.isExternalStorageManager()) {
                dir = new File(Environment.getExternalStorageDirectory(), LOG_DIR_NAME);
            } else {
                dir = new File(Environment.getExternalStorageDirectory(), LOG_DIR_NAME);
            }
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            return dir;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the absolute path of the current log file, or null if not initialized.
     */
    public static String getLogFilePath() {
        return currentLogFile != null ? currentLogFile.getAbsolutePath() : null;
    }

    /**
     * Returns the log directory, or null if not available.
     */
    public static File getLogDirectory() {
        return getLogDir();
    }

    private static String levelToString(int level) {
        switch (level) {
            case VERBOSE: return "V";
            case DEBUG:   return "D";
            case INFO:    return "I";
            case WARN:    return "W";
            case ERROR:   return "E";
            default:      return "?";
        }
    }

    private static String getStackTraceString(Throwable tr) {
        StringWriter sw = new StringWriter(256);
        tr.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
