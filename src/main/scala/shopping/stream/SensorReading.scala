package shopping.stream

case class SensorReading(
    sensorId: String,
    sensorType: String, // "temperature", "pressure", "humidity"
    value: Double,
    timestamp: Long)

case class EnrichedReading(
    reading: SensorReading,
    location: String,
    unit: String,
    isAnomaly: Boolean = false)

case class PipelineResult(
    totalIn: Int,
    passedValidation: Int,
    passedAnomaly: Int,
    persisted: Int,
    droppedAtValidation: Int,
    droppedAtAnomaly: Int,
    failedAtPersist: Int,
    durationMs: Long)
