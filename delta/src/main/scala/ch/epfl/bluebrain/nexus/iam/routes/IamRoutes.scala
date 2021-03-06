package ch.epfl.bluebrain.nexus.iam.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.iam.acls.Acls
import ch.epfl.bluebrain.nexus.iam.permissions.Permissions
import ch.epfl.bluebrain.nexus.iam.realms.Realms
import ch.epfl.bluebrain.nexus.delta.config.AppConfig
import ch.epfl.bluebrain.nexus.delta.config.AppConfig.{HttpConfig, PersistenceConfig}
import monix.eval.Task

object IamRoutes {

  final def apply(
      acls: Acls[Task],
      realms: Realms[Task],
      perms: Permissions[Task]
  )(implicit as: ActorSystem, cfg: AppConfig): Route = {
    implicit val hc: HttpConfig        = cfg.http
    implicit val pc: PersistenceConfig = cfg.persistence

    val eventsRoutes = new EventRoutes(acls, realms).routes
    val idsRoutes    = new IdentitiesRoutes(acls, realms).routes
    val permsRoutes  = new PermissionsRoutes(perms, acls, realms).routes
    val realmsRoutes = new RealmsRoutes(acls, realms).routes
    val aclsRoutes   = new AclsRoutes(acls, realms).routes

    pathPrefix(cfg.http.prefix) {
      eventsRoutes ~ aclsRoutes ~ permsRoutes ~ realmsRoutes ~ idsRoutes
    }
  }
}
