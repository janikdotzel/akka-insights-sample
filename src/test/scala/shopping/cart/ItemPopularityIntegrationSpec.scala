package shopping.cart

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorSystem
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.OptionValues
import org.scalatest.wordspec.AnyWordSpecLike
import shopping.cart.repository.ItemPopularityRepositoryImpl

object ItemPopularityIntegrationSpec {
  val config: Config =
    ConfigFactory.load("item-popularity-integration-test.conf")
}

class ItemPopularityIntegrationSpec
    extends ScalaTestWithActorTestKit(ItemPopularityIntegrationSpec.config)
    with TablesLifeCycle
    with AnyWordSpecLike
    with OptionValues {

  override def typedSystem: ActorSystem[_] = system

  private lazy val itemPopularityRepository =
    new ItemPopularityRepositoryImpl()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
  }

  "Item popularity projection" should {
    "init and join Cluster" in {}

  }
}
