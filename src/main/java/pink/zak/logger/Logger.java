package pink.zak.logger;

import com.google.common.collect.Lists;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import pink.zak.logger.model.LoggerQuery;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

public class Logger {
    private final Config config;
    private final InfluxDBClient databaseClient;
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    public Logger(Config config) {
        this.config = config;
        this.databaseClient = this.config.getDatabaseClient();
        this.startScheduledCleanup();
    }

    /*
     * Naming of the following fields is incorrect according to java naming conventions.
     * This style is being skipped so my eyes don't bleed - Hyfe 2020
     */
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final List<Point> pendingResults = Lists.newCopyOnWriteArrayList();

    /**
     * Start the scheduler which writes and flushes the pending results.
     */
    public void startScheduledCleanup() {
        this.scheduledExecutorService.scheduleAtFixedRate(() -> {
            this.databaseClient.getWriteApi().writePoints(pendingResults);
            pendingResults.clear();
        }, this.config.getLatency(), this.config.getLatency(), TimeUnit.SECONDS);
    }

    /**
     * This is the primary logging method. It creates a {@link LoggerQuery}
     * and thereafter it executes and terminates it.
     *
     * Executing entails pushing it onto the pending results {@link java.util.concurrent.CopyOnWriteArrayList}.
     *
     * @param queryFunction the {@link LoggerQuery} modifier
     * @param <T> the type you're modifying
     */
    public static <T> void log(UnaryOperator<LoggerQuery<T>> queryFunction) {
        executorService.execute(() -> {
            queryFunction.apply(new LoggerQuery<T>()).executeAndTerminate();
        });
    }

    public static void log(String message) {

    }

    public static void queueResult(Point result) {
        pendingResults.add(result.time(System.currentTimeMillis(), WritePrecision.MS));
    }

    public static class Config {
        private final String url;
        private final char[] token;
        private final String organization;
        private final String bucket;
        private final long latency; // in seconds

        public Config(String url, char[] token, String organization, String bucket, long latency) {
            this.url = url;
            this.token = token;
            this.organization = organization;
            this.bucket = bucket;
            this.latency = latency;
        }

        public InfluxDBClient getDatabaseClient() {
            return InfluxDBClientFactory.create(this.url, this.token, this.organization, this.bucket);
        }

        public long getLatency() {
            return this.latency;
        }
    }
}