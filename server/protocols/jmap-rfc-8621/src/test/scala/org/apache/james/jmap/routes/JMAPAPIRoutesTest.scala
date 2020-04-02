package org.apache.james.jmap.routes

import java.nio.charset.StandardCharsets

import io.netty.handler.codec.http.HttpMethod
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.config.EncoderConfig.encoderConfig
import io.restassured.config.RestAssuredConfig.newConfig
import io.restassured.http.ContentType
import org.apache.http.HttpStatus
import org.apache.james.jmap.JMAPRoute
import org.apache.james.jmap.JMAPUrls.JMAP
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.model.RequestObject
import org.apache.james.jmap.json.Fixture._
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reactor.netty.DisposableServer
import reactor.netty.http.server.{HttpServer, HttpServerRequest, HttpServerResponse, HttpServerRoutes}


class JMAPAPIRoutesTest extends AnyFlatSpec with BeforeAndAfter with Matchers {

  var server: DisposableServer = _

  private val REQUEST_OBJECT: String =
    new Serializer().serialize(RequestObject(Seq(coreIdentifier), Seq(invocation1))).toString()

  private val REQUEST_OBJECT_WITH_UNSUPPORTED_METHOD: String =
    new Serializer().serialize(RequestObject(Seq(coreIdentifier), Seq(invocation1, unsupportedInvocation))).toString()

  private val RESPONSE_OBJECT: String = new Serializer().serialize(responseObject1).toString()
  private val RESPONSE_OBJECT_WITH_UNSUPPORTED_METHOD: String = new Serializer().serialize(responseObjectWithUnsupportedMethod).toString()

  before {
    val jmapApiRoutes: JMAPAPIRoutes = new JMAPAPIRoutes()
    val postApiRoute = jmapApiRoutes.routes().filter((jmapRoute: JMAPRoute) => jmapRoute.getEndpoint.getMethod == HttpMethod.POST).findFirst.get

    server = HttpServer.create.port(0)
      .route((routes: HttpServerRoutes) => routes.post(postApiRoute.getEndpoint.getPath, (req: HttpServerRequest, res: HttpServerResponse) =>
        postApiRoute.getAction.handleRequest(req, res))).bindNow()

    RestAssured.requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setAccept(ContentType.JSON)
      .setConfig(newConfig.encoderConfig(encoderConfig.defaultContentCharset(StandardCharsets.UTF_8)))
      .setPort(server.port)
      .setBasePath(JMAP)
      .build
  }

  after {
    server.disposeNow()
  }

  "get" should "not supported and return 404 status" in {
    RestAssured.when()
      .get
      .then
      .statusCode(HttpStatus.SC_NOT_FOUND)
  }

  "post" should "return 200 status" in {
    RestAssured.when()
      .post
      .then
      .statusCode(HttpStatus.SC_OK)
  }

  "post" should "return OK status when call only supported method" in {
    val response = RestAssured.`given`()
        .body(REQUEST_OBJECT)
      .when()
        .post()
      .then
        .statusCode(HttpStatus.SC_OK)
        .contentType(ContentType.JSON)
      .extract()
        .body()
        .asString()

    response shouldBe (RESPONSE_OBJECT)
  }

  "post" should "return OK status when call with unsupported method" in {

    val response = RestAssured.`given`()
        .body(REQUEST_OBJECT_WITH_UNSUPPORTED_METHOD)
      .when()
        .post()
      .then
        .statusCode(HttpStatus.SC_OK)
        .contentType(ContentType.JSON)
      .extract()
        .body()
        .asString()

    response shouldBe (RESPONSE_OBJECT_WITH_UNSUPPORTED_METHOD)
  }
}
