package org.examples.flinkdemo;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransitSimulationSource extends RichParallelSourceFunction<Map> {
  private static final Logger LOGGER = LoggerFactory.getLogger(TransitSimulationSource.class);
  private volatile boolean cancelled = false;
  private Random random;
  private float starRadius;
  private Timestamp nextTimestamp;
  private int counter = 0;
  private int nonTransitWindow = 50000;

  @Override
  public void open(Configuration parameters) throws Exception {
    super.open(parameters);
    random = new Random();
  }

  @Override
  public void run(SourceContext<Map> ctx) throws Exception {
    Star star = new Star(starRadius);
    while (!cancelled) {
      TimeUnit.MILLISECONDS.sleep(1);  // processing delay between each record
      counter += 1;
      if (counter == nonTransitWindow) {
        LOGGER.info("Counter has reached the non transit window length " + nonTransitWindow + ", generating a light curve");
        Planet planet = new Planet(random.nextFloat());
        LightCurve lightCurve = new LightCurve(star, planet);
        String planetId = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        while (lightCurve.getPosition() != Position.COMPLETED) {
          TimeUnit.MILLISECONDS.sleep(1);
          LOGGER.info("Processing next light curve flux value");
          collectRecord(ctx, planetId, lightCurve.curve(), incrementTimestamp());
        }
        LOGGER.info("Light curve data processed");
        LOGGER.info("Resetting counter and generating random non transit window length");
        counter = 0;
        nonTransitWindow = random.nextInt(200000 - 50000);
      }
      collectRecord(ctx, "", 1.0f, incrementTimestamp());
    }
  }

  private void collectRecord(SourceContext<Map> ctx, String planetId, float flux, Timestamp eventTime) {
    Map<String, Object> record = new HashMap<>();
    record.put("planet_id", planetId);
    record.put("flux", flux);
    record.put("event_time", eventTime.toInstant());
    synchronized (ctx.getCheckpointLock()) {
      ctx.collect(record);
    }
  }

  private Timestamp incrementTimestamp() {
    if (nextTimestamp == null) {
      String dateTime = "2022-01-01 00:00:00";
      nextTimestamp = Timestamp.valueOf(dateTime);
    }
    nextTimestamp = new Timestamp(nextTimestamp.getTime() + 1000);
    return nextTimestamp;
  }

  @Override
  public void cancel() {
    cancelled = true;
  }

  public void setStarRadius(float starRadius) {
    this.starRadius = starRadius;
  }
}