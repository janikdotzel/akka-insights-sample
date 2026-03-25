package shopping.stream

import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

object SensorDataPipeline {

  private val logger = LoggerFactory.getLogger(getClass)

  private val sensorTypes = List("temperature", "pressure", "humidity")
  private val locations = Map(
    "temperature" -> "Building-A/Floor-3",
    "pressure" -> "Building-B/Basement",
    "humidity" -> "Building-A/Floor-1")
  private val units = Map(
    "temperature" -> "celsius",
    "pressure" -> "hPa",
    "humidity" -> "percent")

  def run(count: Int)(implicit system: ActorSystem[_]): Future[PipelineResult] = {
    import system.executionContext

    val startTime = System.currentTimeMillis()
    val random = new Random()

    // Counters for tracking messages at each stage
    val validatedCount = new AtomicInteger(0)
    val anomalyPassedCount = new AtomicInteger(0)
    val persistedCount = new AtomicInteger(0)
    val persistFailedCount = new AtomicInteger(0)

    // --- Source: Generate sensor readings (some intentionally malformed) ---
    val source = Source(1 to count).map { i =>
      if (i % 7 == 0) {
        // ~14% malformed: empty sensorId or out-of-range value
        if (i % 2 == 0)
          SensorReading("", sensorTypes(i % 3), random.nextDouble() * 50, System.currentTimeMillis())
        else
          SensorReading(s"sensor-$i", sensorTypes(i % 3), -999.0, System.currentTimeMillis())
      } else if (i % 20 == 0) {
        // ~5% anomalous: temperature reading of exactly 0.0 (sensor error)
        SensorReading(s"sensor-$i", "temperature", 0.0, System.currentTimeMillis())
      } else {
        val sType = sensorTypes(i % 3)
        val value = sType match {
          case "temperature" => 15.0 + random.nextDouble() * 25.0 // 15-40 celsius
          case "pressure"    => 980.0 + random.nextDouble() * 40.0 // 980-1020 hPa
          case "humidity"    => 30.0 + random.nextDouble() * 60.0 // 30-90 percent
        }
        SensorReading(s"sensor-$i", sType, value, System.currentTimeMillis())
      }
    }

    // --- Stage 1: Validate ---
    val validate = Flow[SensorReading]
      .filter { reading =>
        val valid = reading.sensorId.nonEmpty &&
          reading.value > -100 && reading.value < 500
        if (!valid) {
          logger.debug("Validation dropped reading: sensorId={}, value={}", reading.sensorId, reading.value)
        }
        valid
      }
      .map { reading =>
        validatedCount.incrementAndGet()
        reading
      }
      .named("validate")

    // --- Stage 2: Enrich (variable latency simulating external lookup) ---
    val enrich = Flow[SensorReading]
      .mapAsync(4) { reading =>
        val delay = 50 + random.nextInt(450) // 50-500ms
        akka.pattern.after(delay.millis) {
          Future.successful(
            EnrichedReading(
              reading = reading,
              location = locations.getOrElse(reading.sensorType, "unknown"),
              unit = units.getOrElse(reading.sensorType, "unknown")))
        }
      }
      .named("enrich")

    // --- Stage 3: Anomaly Detection (drops sensor errors) ---
    val anomalyDetect = Flow[EnrichedReading]
      .filter { enriched =>
        val isSensorError = enriched.reading.sensorType == "temperature" &&
          enriched.reading.value == 0.0
        if (isSensorError) {
          logger.warn(
            "Anomaly detection dropped sensor error: sensorId={}, value={}",
            enriched.reading.sensorId,
            enriched.reading.value)
        }
        !isSensorError
      }
      .map { enriched =>
        anomalyPassedCount.incrementAndGet()
        // Flag readings that are near thresholds as anomalies (but don't drop them)
        val isAnomaly = enriched.reading.sensorType match {
          case "temperature" => enriched.reading.value > 38.0
          case "pressure"    => enriched.reading.value < 985.0 || enriched.reading.value > 1015.0
          case "humidity"    => enriched.reading.value > 85.0
          case _             => false
        }
        if (isAnomaly) {
          logger.info("Anomaly flagged: sensorId={}, type={}, value={}",
            enriched.reading.sensorId, enriched.reading.sensorType, enriched.reading.value)
        }
        enriched.copy(isAnomaly = isAnomaly)
      }
      .named("anomaly-detect")

    // --- Stage 4: Normalize (fast, but occasionally slow) ---
    val normalize = Flow[EnrichedReading]
      .mapAsync(4) { enriched =>
        if (random.nextInt(50) == 0) {
          // ~2% chance of being slow (simulates GC pause or contention)
          logger.debug("Normalize slow for sensorId={}", enriched.reading.sensorId)
          akka.pattern.after(200.millis)(Future.successful(enriched))
        } else {
          Future.successful(enriched)
        }
      }
      .named("normalize")

    // --- Stage 5: Persist (simulates DB writes, ~8% fail) ---
    val persist = Flow[EnrichedReading]
      .mapAsync(2) { enriched =>
        if (random.nextInt(12) == 0) {
          // ~8% failure rate
          persistFailedCount.incrementAndGet()
          logger.error(
            "Persist failed for sensorId={}: simulated database timeout",
            enriched.reading.sensorId)
          Future.successful(None)
        } else {
          // Simulate DB write latency
          val writeDelay = 20 + random.nextInt(80) // 20-100ms
          akka.pattern.after(writeDelay.millis) {
            persistedCount.incrementAndGet()
            Future.successful(Some(enriched))
          }
        }
      }
      .collect { case Some(enriched) => enriched }
      .named("persist")

    // --- Run the pipeline (named on full RunnableGraph including sink for Cinnamon) ---
    source
      .via(validate)
      .via(enrich)
      .via(anomalyDetect)
      .via(normalize)
      .via(persist)
      .toMat(Sink.seq)(Keep.right)
      .named("sensor-data-pipeline")
      .run()
      .map { results =>
        val duration = System.currentTimeMillis() - startTime
        val validated = validatedCount.get()
        val anomalyPassed = anomalyPassedCount.get()
        val persisted = persistedCount.get()
        val persistFailed = persistFailedCount.get()

        val result = PipelineResult(
          totalIn = count,
          passedValidation = validated,
          passedAnomaly = anomalyPassed,
          persisted = persisted,
          droppedAtValidation = count - validated,
          droppedAtAnomaly = validated - anomalyPassed,
          failedAtPersist = persistFailed,
          durationMs = duration)

        logger.info(
          "Pipeline complete: {} in → {} persisted, {} dropped at validation, {} dropped at anomaly, {} failed at persist, took {}ms",
          Array[AnyRef](
            Int.box(result.totalIn),
            Int.box(result.persisted),
            Int.box(result.droppedAtValidation),
            Int.box(result.droppedAtAnomaly),
            Int.box(result.failedAtPersist),
            Long.box(result.durationMs)): _*)

        result
      }
  }
}
