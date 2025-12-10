package shopping.cart.repository

import akka.projection.r2dbc.scaladsl.R2dbcSession

import scala.concurrent.Future


trait ItemPopularityRepository {
  def update(session: R2dbcSession, itemId: String, delta: Int): Future[Long]
  def getItem(session: R2dbcSession, itemId: String): Future[Option[Long]]
}



class ItemPopularityRepositoryImpl() extends ItemPopularityRepository {

  override def update(
      session: R2dbcSession,
      itemId: String,
      delta: Int): Future[Long] = {
    session.updateOne(session
      .createStatement(
        "INSERT INTO item_popularity (itemid, count) VALUES ($1, $2) " +
        "ON CONFLICT(itemid) DO UPDATE SET count = item_popularity.count + $3")
      .bind(0, itemId)
      .bind(1, delta)
      .bind(2, delta))
  }

  override def getItem(
      session: R2dbcSession,
      itemId: String): Future[Option[Long]] = {
    session.selectOne(
      session
        .createStatement("SELECT count FROM item_popularity WHERE itemid = $1")
        .bind(0, itemId)) { row =>
      row.get("count", classOf[java.lang.Long])
    }
  }

}

