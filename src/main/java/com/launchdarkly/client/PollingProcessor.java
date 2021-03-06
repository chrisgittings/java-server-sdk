package com.launchdarkly.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.launchdarkly.client.Util.httpErrorMessage;
import static com.launchdarkly.client.Util.isHttpErrorRecoverable;

final class PollingProcessor implements UpdateProcessor {
  private static final Logger logger = LoggerFactory.getLogger(PollingProcessor.class);

  @VisibleForTesting final FeatureRequestor requestor;
  private final FeatureStore store;
  @VisibleForTesting final long pollIntervalMillis;
  private AtomicBoolean initialized = new AtomicBoolean(false);
  private ScheduledExecutorService scheduler = null;

  PollingProcessor(FeatureRequestor requestor, FeatureStore featureStore, long pollIntervalMillis) {
    this.requestor = requestor; // note that HTTP configuration is applied to the requestor when it is created
    this.store = featureStore;
    this.pollIntervalMillis = pollIntervalMillis;
  }

  @Override
  public boolean initialized() {
    return initialized.get();
  }

  @Override
  public void close() throws IOException {
    logger.info("Closing LaunchDarkly PollingProcessor");
    if (scheduler != null) {
      scheduler.shutdown();
    }
    requestor.close();
  }

  @Override
  public Future<Void> start() {
    logger.info("Starting LaunchDarkly polling client with interval: "
        + pollIntervalMillis + " milliseconds");
    final SettableFuture<Void> initFuture = SettableFuture.create();
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setNameFormat("LaunchDarkly-PollingProcessor-%d")
        .build();
    scheduler = Executors.newScheduledThreadPool(1, threadFactory);

    scheduler.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        try {
          FeatureRequestor.AllData allData = requestor.getAllData();
          store.init(DefaultFeatureRequestor.toVersionedDataMap(allData));
          if (!initialized.getAndSet(true)) {
            logger.info("Initialized LaunchDarkly client.");
            initFuture.set(null);
          }
        } catch (HttpErrorException e) {
          logger.error(httpErrorMessage(e.getStatus(), "polling request", "will retry"));
          if (!isHttpErrorRecoverable(e.getStatus())) {
            scheduler.shutdown();
            initFuture.set(null); // if client is initializing, make it stop waiting; has no effect if already inited
          }
        } catch (IOException e) {
          logger.error("Encountered exception in LaunchDarkly client when retrieving update: {}", e.toString());
          logger.debug(e.toString(), e);
        }
      }
    }, 0L, pollIntervalMillis, TimeUnit.MILLISECONDS);

    return initFuture;
  }
}