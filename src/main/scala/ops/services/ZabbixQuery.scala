package ops.services

import java.util.concurrent.TimeUnit
import akka.event.LoggingAdapter
import com.squareup.okhttp.{MediaType, OkHttpClient, Request, RequestBody}
import com.typesafe.config.Config
import io.circe
import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, ObjectEncoder}
import scala.annotation.tailrec
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scalaz.concurrent.Task
import scalaz.{-\/, \/-}

trait ZQueryParams {
  val method: String
}

trait ZResponseResults

sealed case class ZResponse[T <: ZResponseResults](result: List[T], jsonrpc: String = "2.0", id: Int = 1)

sealed case class ZQuery[T <: ZQueryParams](method: String, params: T,jsonrpc: String = "2.0", id: Int = 1)

case class ZQuerySessioned[T <: ZQueryParams](auth: String, method: String, params: T, jsonrpc: String = "2.0", id: Int = 1)

case class ZQueryLoginResponse(result:String)

case class ZQueryParamsEvent(eventids: String) extends ZQueryParams {
  val method = "event.get"
}

case class ZQueryParamsAlert(alertids: String) extends ZQueryParams {
  val method = "alert.get"
}

case class ZQueryParamsItem(hostids: String) extends ZQueryParams {
  val method = "item.get"
}

case class ZQueryParamsLogin(user: String, password: String) extends ZQueryParams {
  val method = "user.login"
}

case class ZQueryParamsProblem(eventids: String) extends ZQueryParams {
  val method = "problem.get"
}

final case class ZProblemResult(
                                 eventid: String,
                                 source: String,
                                 `object`: String,
                                 objectid: String,
                                 clock: String,
                                 ns: String,
                                 r_eventid: String,
                                 r_clock: String,
                                 r_ns: String,
                                 correlationid: String,
                                 userid: String
                               ) extends ZResponseResults

final case class ZAlertResult(
                               alertid: String,
                               actionid: String,
                               eventid: String,
                               userid: String,
                               clock: String,
                               mediatypeid: String,
                               sendto: String,
                               subject: String,
                               message: String,
                               status: String,
                               retries: String,
                               error: String,
                               esc_step: String,
                               alerttype: String
                             ) extends ZResponseResults

final case class ZEventResult(
                               eventid: String,
                               source: String,
                               `object`: String,
                               objectid: String,
                               acknowledged:String,
                               clock:String,
                               ns:String,
                               value:String,
                               r_eventid: String,
                               c_eventid: String,
                               correlationid: String,
                               userid: String
                             ) extends ZResponseResults {
  def isRecoveried: Boolean = {
    this.r_eventid != "0"
  }

  def getEventType: String =
    if (source == "0") "Trigger"
    else if (source == "1") "Discovery"
    else if (source == "1") "AgentRegistration"
    else "Internal"

  def getEventStatus: String = {
    if (value == "0") {
      if (source == "0") "恢复"
      else if (source == "1") "主机或者服务启动"
      else if (source == "2") "正常"
      else "未知状态"
    }
    else if (value == "1") {
      if (source == "0") "告警"
      else if (source == "1") "主机或者服务宕机"
      else if (source == "2") "未知"
      else "未知状态"
    }
    else if (value == "2") "主机或者服务被发现"
    else if (value == "3") "主机或者服务丢失"
    else "未知"
  }
}

final case class ZItemResult(
                              itemid:String,
                              `type`: String,
                              snmp_community: String,
                              snmp_oid: String,
                              hostid: String,
                              name: String,
                              `key_`: String,
                              delay: String,
                              history: String,
                              trends: String,
                              lastvalue: String,
                              lastclock: String,
                              prevvalue: String,
                              state: String,
                              status: String,
                              value_type: String,
                              trapper_hosts: String,
                              units: String,
                              multiplier: String,
                              delta: String,
                              snmpv3_securityname: String,
                              snmpv3_securitylevel: String,
                              snmpv3_authpassphrase: String,
                              snmpv3_privpassphrase: String,
                              snmpv3_authprotocol: String,
                              snmpv3_privprotocol: String,
                              snmpv3_contextname: String,
                              formula: String,
                              error: String,
                              lastlogsize: String,
                              logtimefmt: String,
                              templateid: String,
                              valuemapid: String,
                              delay_flex: String,
                              params: String,
                              ipmi_sensor: String,
                              data_type: String,
                              authtype: String,
                              username: String,
                              password: String,
                              publickey: String,
                              privatekey: String,
                              mtime: String,
                              lastns: String,
                              flags: String,
                              interfaceid: String,
                              port: String,
                              description: String,
                              inventory_link: String,
                              lifetime: String,
                              evaltype: String
                            ) extends ZResponseResults

object ZabbixJSON {

  // inner
  implicit val enLoginParams: ObjectEncoder[ZQueryParamsLogin] = deriveEncoder[ZQueryParamsLogin]
  implicit val deLoginParams: Decoder[ZQueryParamsLogin] = deriveDecoder[ZQueryParamsLogin]
  implicit val enAlertParams: ObjectEncoder[ZQueryParamsAlert] = deriveEncoder[ZQueryParamsAlert]
  implicit val deAlertResults: Decoder[ZAlertResult] = deriveDecoder[ZAlertResult]
  implicit val deEventResults: Decoder[ZEventResult] = deriveDecoder[ZEventResult]
  implicit val enEventParams: ObjectEncoder[ZQueryParamsEvent] = deriveEncoder[ZQueryParamsEvent]
  implicit val deItemResults: Decoder[ZItemResult] = deriveDecoder[ZItemResult]
  implicit val enItemParams: ObjectEncoder[ZQueryParamsItem] = deriveEncoder[ZQueryParamsItem]
  implicit val enProblemParams: ObjectEncoder[ZQueryParamsProblem] = deriveEncoder[ZQueryParamsProblem]
  implicit val deProblemResults: Decoder[ZProblemResult] = deriveDecoder[ZProblemResult]

  // special
  implicit val zqLoginJson: ObjectEncoder[ZQuery[ZQueryParamsLogin]] = deriveEncoder[ZQuery[ZQueryParamsLogin]]
  implicit val zqResponseLoginJson: Decoder[ZQueryLoginResponse] = deriveDecoder[ZQueryLoginResponse]

  // outter
  implicit val enZQSAlert: ObjectEncoder[ZQuerySessioned[ZQueryParamsAlert]] = deriveEncoder[ZQuerySessioned[ZQueryParamsAlert]]
  implicit val deZRAlert: Decoder[ZResponse[ZAlertResult]] = deriveDecoder[ZResponse[ZAlertResult]]
  implicit val enZQSEvent: ObjectEncoder[ZQuerySessioned[ZQueryParamsEvent]] = deriveEncoder[ZQuerySessioned[ZQueryParamsEvent]]
  implicit val deZREvent: Decoder[ZResponse[ZEventResult]] = deriveDecoder[ZResponse[ZEventResult]]
  implicit val enZQSItem: ObjectEncoder[ZQuerySessioned[ZQueryParamsItem]] = deriveEncoder[ZQuerySessioned[ZQueryParamsItem]]
  implicit val deZRItem: Decoder[ZResponse[ZItemResult]] = deriveDecoder[ZResponse[ZItemResult]]
  implicit val enZQSProblem: ObjectEncoder[ZQuerySessioned[ZQueryParamsProblem]] = deriveEncoder[ZQuerySessioned[ZQueryParamsProblem]]
  implicit val deZRProblem: Decoder[ZResponse[ZProblemResult]] = deriveDecoder[ZResponse[ZProblemResult]]

}


class ZabbixQuery(config: Config)(implicit logger: LoggingAdapter) {
  import ZabbixJSON._

  private val port: Int = config.getInt("ops.zabbix.port")
  private val zbxUser: String = config.getString("ops.zabbix.user")
  private val zbxPassword: String = config.getString("ops.zabbix.password")
  private val api: String = config.getString("ops.zabbix.api")
  private val mediaType = MediaType.parse("application/json")
  private val retryDuration = Seq(1 seconds, 3 seconds, 5 seconds, 15 seconds, 30 seconds)
  private val client: OkHttpClient = this.getHttpClient()
  private var token: String = getToken(zbxUser, zbxPassword)


  def getHttpClient(): OkHttpClient = {
    val client: OkHttpClient = new OkHttpClient()
    client.setRetryOnConnectionFailure(true)
    client.setConnectTimeout(30, TimeUnit.SECONDS)
    client
  }

  def get[T1 <: ZQueryParams, T2 <: ZResponseResults](
                                                       param: T1,
                                                       v: Promise[Either[circe.Error,ZResponse[T2]]]
                                                     )(implicit
                                                       encoder: Encoder[ZQuerySessioned[T1]],
                                                       decoder: Decoder[ZResponse[T2]]
                                                     ): Unit = {
    Task {
      val q = ZQuerySessioned[T1](method = param.method, params = param, auth = this.token).asJson.toString()
      logger.info(s"Sending ZQuery(${q}).")
      val rb = RequestBody.create(mediaType, q)
      val req = new Request.Builder().url(api).post(rb).build()
      Try(client.newCall(req).execute()) match {
        case Success(response) => {
          val code = response.code()
          val body = response.body().string()
          if (code == 200) { // ok
            logger.info(body)
            v.complete(Try(decode[ZResponse[T2]](body)))
          }
          else {
            this.token = getToken(zbxUser, zbxPassword)
            logger.error(body)
            new RuntimeException(s"ZQuery failed, server answers[$code].($body)")
          }
        }
        case Failure(y) => y
      }
    }
      .retry(retryDuration)
      .unsafePerformAsyncInterruptibly {
        case \/-(success) => logger.info(s"ZQuery successfully completed.")
        case -\/(exc) => {
          exc.printStackTrace()
          logger.error(exc.getStackTrace.mkString("\n"))
        }
      }
  }

  @tailrec
  final def getToken(user: String, password: String): String = {
    val p = ZQueryParamsLogin(zbxUser,zbxPassword)
    val q = ZQuery[ZQueryParamsLogin](method=p.method, params=p).asJson.toString()
    val rb = RequestBody.create(mediaType,q)
    val req = new Request.Builder().url(api).post(rb).build()

    val response = Task(client.newCall(req)).retry(retryDuration).unsafePerformSync.execute()
    val code = response.code()
    val body = response.body().string()
    response.body().close()
    if (code == 200) { //ok
      decode[ZQueryLoginResponse](body) match {
        case Left(exc) => {
          exc.printStackTrace()
          logger.error(exc.getStackTrace.mkString("\n"))
          getToken(user,password)
        }
        case Right(pass) => pass.result
      }
    }
    else {
      logger.error(s"Zabbix login failed[${code.toString}].")
      getToken(user, password)
    }
  }
}