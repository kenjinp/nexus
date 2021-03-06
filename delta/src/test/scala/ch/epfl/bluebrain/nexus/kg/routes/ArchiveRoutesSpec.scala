package ch.epfl.bluebrain.nexus.kg.routes

import java.time.{Clock, Instant, ZoneId}

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, Location}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import ch.epfl.bluebrain.nexus.admin.index.{OrganizationCache, ProjectCache}
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticSearchClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient._
import ch.epfl.bluebrain.nexus.commons.http.RdfMediaTypes.`application/ld+json`
import ch.epfl.bluebrain.nexus.commons.search.QueryResults
import ch.epfl.bluebrain.nexus.commons.sparql.client.BlazegraphClient
import ch.epfl.bluebrain.nexus.iam.acls.{AccessControlList, AccessControlLists, Acls}
import ch.epfl.bluebrain.nexus.iam.realms.Realms
import ch.epfl.bluebrain.nexus.iam.types.Identity.Anonymous
import ch.epfl.bluebrain.nexus.iam.types.Permission
import ch.epfl.bluebrain.nexus.kg.TestHelper
import ch.epfl.bluebrain.nexus.kg.archives.ArchiveCache
import ch.epfl.bluebrain.nexus.kg.async._
import ch.epfl.bluebrain.nexus.kg.cache._
import ch.epfl.bluebrain.nexus.kg.config.Contexts._
import ch.epfl.bluebrain.nexus.kg.config.Schemas._
import ch.epfl.bluebrain.nexus.kg.marshallers.instances._
import ch.epfl.bluebrain.nexus.kg.resources.ResourceF.Value
import ch.epfl.bluebrain.nexus.kg.resources._
import ch.epfl.bluebrain.nexus.kg.storage.AkkaSource
import ch.epfl.bluebrain.nexus.rdf.Graph
import ch.epfl.bluebrain.nexus.rdf.Graph.Triple
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import ch.epfl.bluebrain.nexus.delta.config.Settings
import ch.epfl.bluebrain.nexus.storage.client.StorageClient
import ch.epfl.bluebrain.nexus.util.{CirceEq, EitherValues, Resources => TestResources}
import io.circe.Json
import io.circe.generic.auto._
import monix.eval.Task
import org.mockito.matchers.MacroBasedMatchers
import org.mockito.{ArgumentMatchersSugar, IdiomaticMockito, Mockito}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfter, Inspectors, OptionValues}

import scala.concurrent.duration._

//noinspection TypeAnnotation
class ArchiveRoutesSpec
    extends AnyWordSpecLike
    with Matchers
    with EitherValues
    with OptionValues
    with ScalatestRouteTest
    with TestResources
    with ScalaFutures
    with IdiomaticMockito
    with ArgumentMatchersSugar
    with MacroBasedMatchers
    with BeforeAndAfter
    with TestHelper
    with Inspectors
    with CirceEq
    with Eventually {

  implicit override def patienceConfig: PatienceConfig = PatienceConfig(3.second, 15.milliseconds)

  implicit private val appConfig = Settings(system).appConfig
  implicit private val clock     = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())

  implicit private val projectCache  = mock[ProjectCache[Task]]
  implicit private val viewCache     = mock[ViewCache[Task]]
  implicit private val resolverCache = mock[ResolverCache[Task]]
  implicit private val storageCache  = mock[StorageCache[Task]]
  implicit private val archiveCache  = mock[ArchiveCache[Task]]
  implicit private val archives      = mock[Archives[Task]]
  implicit private val resources     = mock[Resources[Task]]
  implicit private val tagsRes       = mock[Tags[Task]]
  implicit private val aclsApi       = mock[Acls[Task]]
  private val realms                 = mock[Realms[Task]]

  implicit private val cacheAgg =
    Caches(mock[OrganizationCache[Task]], projectCache, viewCache, resolverCache, storageCache, archiveCache)

  implicit private val ec            = system.dispatcher
  implicit private val utClient      = untyped[Task]
  implicit private val qrClient      = withUnmarshaller[Task, QueryResults[Json]]
  implicit private val jsonClient    = withUnmarshaller[Task, Json]
  implicit private val sparql        = mock[BlazegraphClient[Task]]
  implicit private val elasticSearch = mock[ElasticSearchClient[Task]]
  implicit private val storageClient = mock[StorageClient[Task]]
  implicit private val clients       = Clients()

  before {
    Mockito.reset(archives)
  }

  private val archivesWrite = Permission.unsafe("archives/write")

  private val manageArchive = Set(Permission.unsafe("resources/read"), archivesWrite)
  // format: off
  private val routes = new KgRoutes(resources, mock[Resolvers[Task]], mock[Views[Task]], mock[Storages[Task]], mock[Schemas[Task]], mock[Files[Task]], archives, tagsRes, aclsApi, realms, mock[ProjectViewCoordinator[Task]]).routes
  // format: on

  //noinspection NameBooleanParameters
  abstract class Context(perms: Set[Permission] = manageArchive) extends RoutesFixtures {

    projectCache.getBy(label) shouldReturn Task.pure(Some(projectMeta))
    projectCache.getBy(projectRef) shouldReturn Task.pure(Some(projectMeta))
    projectCache.get(projectRef.id) shouldReturn Task.pure(Some(projectMeta))
    realms.caller(token.value) shouldReturn Task(caller)
    implicit val acls = AccessControlLists(/ -> resourceAcls(AccessControlList(Anonymous -> perms)))
    aclsApi.list(label.organization / label.value, ancestors = true, self = true)(caller) shouldReturn Task.pure(acls)

    val metadataRanges = Seq(Accept(`application/json`.mediaType), Accept(`application/ld+json`))

    val json: Json = Json.obj(genString() -> Json.fromString(genString()))

    val resource =
      ResourceF.simpleF(id, json, created = user, updated = user, schema = archiveRef)

    val resourceV =
      ResourceF.simpleV(id, Value(Json.obj(), Json.obj(), Graph(id.value, Set.empty[Triple])))

    def response(): Json =
      response(archiveRef) deepMerge Json.obj(
        "_self" -> Json.fromString(s"http://127.0.0.1:8080/v1/archives/$organization/$project/nxv:$genUuid")
      )
  }

  "The archive routes" should {

    "create an archive without @id" in new Context {
      archives.create(json) shouldReturn EitherT.rightT[Task, Rejection](resource)
      aclsApi.hasPermission(organization / project, archivesWrite)(caller) shouldReturn Task.pure(true)

      forAll(metadataRanges) { accept =>
        Post(s"/v1/archives/$organization/$project", json) ~> addCredentials(oauthToken) ~> accept ~> routes ~> check {
          status shouldEqual StatusCodes.Created
          responseAs[Json] should equalIgnoreArrayOrder(response())
        }
      }

      Post(s"/v1/archives/$organization/$project", json) ~> addCredentials(oauthToken) ~> Accept(
        MediaRanges.`*/*`
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.SeeOther
        header[Location].value.value() should startWith(s"http://127.0.0.1:8080/v1/archives/$organization/$project/")
      }
    }

    "create an archive with @id" in new Context {
      aclsApi.hasPermission(organization / project, archivesWrite)(caller) shouldReturn Task.pure(true)
      archives.create(id, json) shouldReturn EitherT.rightT[Task, Rejection](resource)

      forAll(metadataRanges) { accept =>
        Put(s"/v1/archives/$organization/$project/$urlEncodedId", json) ~> addCredentials(
          oauthToken
        ) ~> accept ~> routes ~> check {
          status shouldEqual StatusCodes.Created
          responseAs[Json] should equalIgnoreArrayOrder(response())
        }
      }

      Put(s"/v1/archives/$organization/$project/$urlEncodedId", json) ~> addCredentials(oauthToken) ~> Accept(
        MediaRanges.`*/*`
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[Json] should equalIgnoreArrayOrder(response())
      }
    }

    "fetch an archive with ignoreNotFound = false (default)" in new Context {
      val content = genString()

      aclsApi.list(anyProject, ancestors = true, self = true)(caller) shouldReturn Task.pure(acls)

      archives.fetchArchive(id, ignoreNotFound = false) shouldReturn
        EitherT.rightT[Task, Rejection](Source.single(ByteString(content)): AkkaSource)

      val accepted =
        List(Accept(MediaRanges.`*/*`), Accept(MediaRanges.`application/*`), Accept(MediaTypes.`application/x-tar`))

      forAll(accepted) { accept =>
        Get(s"/v1/archives/$organization/$project/$urlEncodedId") ~> addCredentials(
          oauthToken
        ) ~> accept ~> routes ~> check {
          status shouldEqual StatusCodes.OK
          consume(responseEntity.dataBytes) shouldEqual content
        }
      }
    }

    "fetch an archive with ignoreNotFound = true" in new Context {
      val content = genString()
      aclsApi.list(anyProject, ancestors = true, self = true)(caller) shouldReturn Task.pure(acls)
      archives.fetchArchive(id, ignoreNotFound = true) shouldReturn
        EitherT.rightT[Task, Rejection](Source.single(ByteString(content)): AkkaSource)

      val accepted =
        List(Accept(MediaRanges.`*/*`), Accept(MediaRanges.`application/*`), Accept(MediaTypes.`application/x-tar`))

      forAll(accepted) { accept =>
        Get(s"/v1/archives/$organization/$project/$urlEncodedId?ignoreNotFound=true") ~> addCredentials(
          oauthToken
        ) ~> accept ~> routes ~> check {
          status shouldEqual StatusCodes.OK
          consume(responseEntity.dataBytes) shouldEqual content
        }
      }
    }

    "fetch an archives' source" in new Context {
      archives.fetch(any[ResId]) shouldReturn EitherT.rightT[Task, Rejection](resourceV)
      forAll(metadataRanges) { accept =>
        Get(s"/v1/archives/$organization/$project/$urlEncodedId") ~> addCredentials(
          oauthToken
        ) ~> accept ~> routes ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[Json] shouldEqual Json.obj(
            "@context" -> Json.fromString(resourceCtxUri.asString),
            "@id"      -> Json.fromString(resourceV.id.value.asUri)
          )
        }
      }
    }
  }
}
