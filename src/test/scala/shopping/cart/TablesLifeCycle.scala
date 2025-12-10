package shopping.cart

import akka.Done
import akka.actor.typed.ActorSystem
import akka.persistence.Persistence
import akka.persistence.r2dbc.internal.R2dbcExecutor
import akka.persistence.r2dbc.{ ConnectionFactoryProvider, R2dbcSettings }
import akka.projection.r2dbc.R2dbcProjectionSettings
import org.scalatest.{ BeforeAndAfterAll, Suite }
import org.slf4j.LoggerFactory

import java.io.File
import java.nio.file.Paths
import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._

trait TablesLifeCycle extends BeforeAndAfterAll { this: Suite =>

  private val createTablesFile =
    Paths.get("ddl-scripts/create_tables.sql").toFile

  private val dropTablesFile =
    Paths.get("ddl-scripts/drop_tables.sql").toFile

  private val createUserTablesFile =
    Paths.get("ddl-scripts/create_user_tables.sql").toFile

  def typedSystem: ActorSystem[_]
  implicit val ec: ExecutionContext = typedSystem.executionContext

  def testConfigPath: String = "akka.projection.r2dbc"

  lazy val r2dbcProjectionSettings: R2dbcProjectionSettings =
    R2dbcProjectionSettings(
      typedSystem.settings.config.getConfig(testConfigPath))
  lazy val r2dbcSettings: R2dbcSettings =
    R2dbcSettings(
      typedSystem.settings.config.getConfig(
        r2dbcProjectionSettings.useConnectionFactory
          .replace(".connection-factory", "")))
  lazy val r2dbcExecutor: R2dbcExecutor = {
    new R2dbcExecutor(
      ConnectionFactoryProvider(typedSystem).connectionFactoryFor(
        r2dbcProjectionSettings.useConnectionFactory),
      LoggerFactory.getLogger(getClass),
      r2dbcProjectionSettings.logDbCallsExceeding,
      ConnectionFactoryProvider(typedSystem)
        .connectionPoolSettingsFor(r2dbcProjectionSettings.useConnectionFactory)
        .closeCallsExceeding)(typedSystem.executionContext, typedSystem)
  }

  lazy val persistenceExt: Persistence = Persistence(typedSystem)

  override protected def beforeAll(): Unit = {
    // ok to block here, main thread
    Await.result(
      for {
        _ <- r2dbcExecutor.executeDdl("beforeAll dropTable")(
          _.createStatement(tablesSql(dropTablesFile)))
        _ <- r2dbcExecutor.executeDdl("beforeAll createTable")(
          _.createStatement(tablesSql(createTablesFile)))
      } yield Done,
      30.seconds)
    if (createUserTablesFile.exists()) {
      Await.result(
        for {
          _ <- r2dbcExecutor.executeDdl("beforeAll dropTable")(
            _.createStatement("DROP TABLE IF EXISTS public.item_popularity;"))
          _ <- r2dbcExecutor.executeDdl("beforeAll dropTable")(
            _.createStatement(tablesSql(createUserTablesFile)))
        } yield Done,
        30.seconds)
    }
    LoggerFactory
      .getLogger("shopping.cart.CreateTableTestUtils")
      .info("Created tables")

    super.beforeAll()
  }

  private def tablesSql(file: File): String = {
    val source = scala.io.Source.fromFile(file)
    val contents = source.mkString
    source.close()
    contents
  }
}
