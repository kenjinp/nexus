package ch.epfl.bluebrain.nexus.sourcing.projections

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.testkit._
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Promise
import scala.concurrent.duration._

class ActorSystemFixture(name: String, startCluster: Boolean = false)
    extends TestKit(ActorSystem(name, ConfigFactory.load("service-test.conf")))
    with AnyWordSpecLike
    with PatienceConfiguration
    with BeforeAndAfterAll {

  implicit override def patienceConfig: PatienceConfig = PatienceConfig(3.seconds.dilated, 100.milliseconds.dilated)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    if (startCluster) {
      val promise = Promise[Unit]
      Cluster(system).registerOnMemberUp(promise.success(()))
      Cluster(system).join(Cluster(system).selfAddress)
      promise.future.futureValue
    }
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
}
