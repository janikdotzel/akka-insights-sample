## Instrumenting Akka Streams with Cinnamon (Akka Insights)

### Prerequisites

Add the Cinnamon Akka Stream dependency to your build:

```scala
// build.sbt
libraryDependencies += Cinnamon.library.cinnamonAkkaStream
```

Ensure the Cinnamon agent is enabled:

```scala
enablePlugins(Cinnamon)
run / cinnamon := true
```

### Step 1: Name your stream operators

Use `.named()` on each `Flow` stage to give it a human-readable name. These names appear as span names in traces.

```scala
val validate = Flow[SensorReading]
  .filter(reading => reading.value > -100 && reading.value < 500)
  .named("validate")

val enrich = Flow[SensorReading]
  .mapAsync(4)(reading => enrichFromExternalService(reading))
  .named("enrich")
```

### Step 2: Name the overall stream graph

Apply `.named()` to the **entire RunnableGraph including the sink**. This is required for Cinnamon to match the stream by name. Use `.toMat(...)(Keep.right)` instead of `.runWith()` so you can name the closed graph before running it.

```scala
source
  .via(validate)
  .via(enrich)
  .via(persist)
  .toMat(Sink.seq)(Keep.right)   // close the graph (keep sink's materialized value)
  .named("my-pipeline")          // name the full RunnableGraph
  .run()                          // materialize
```

**Important:** `.named()` on the graph propagates as a prefix to all operator span names. For example, operators will appear as `my-pipeline-validate-filter`, `my-pipeline-enrich-mapAsync`, etc.

### Step 3: Configure Cinnamon

Add to `cinnamon.conf` (or `application.conf`):

```hocon
# Match streams by name (preferred — use "name:" prefix)
cinnamon.akka.streams {
  "name:my-pipeline" {
    report-by = name    # aggregate metrics by stream name
    flows = on          # enable per-flow metrics
    traceable = on      # enable distributed tracing spans
  }
}

# Optional: enable stream-level metrics
cinnamon.akka.stream.metrics {
  demand = on
  latency = on
}
```

**Config key format:**
- `"name:my-pipeline"` — matches by the `.named()` attribute (recommended)
- `"com.example.MyClass.*"` — matches by class where stream is materialized (expensive, not recommended)

### Step 4: Configure trace export

Cinnamon exports traces via OpenTracing/Zipkin:

```hocon
cinnamon.opentracing {
  tracer {
    service-name = "my-service"
  }
  zipkin {
    url-connection {
      encoding = "json"
      endpoint = "http://localhost:9411/api/v2/spans"
    }
  }
}
```