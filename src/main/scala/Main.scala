import java.time.{LocalDateTime, ZoneOffset}
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.circe
import ops.services.{ZEventResult, ZQueryParamsEvent, ZResponse, ZabbixQuery}
import scala.concurrent.Promise

/**
  * Created by CodeMonkey on 2017/7/25.
  */

object Main extends App {
  import ops.services.ZabbixJSON._

  import scala.concurrent.ExecutionContext.Implicits.global

  val config = ConfigFactory.load()
  val sys = ActorSystem()
  implicit val log = sys.log

  val zQuery = new ZabbixQuery(config)
  val zrParams: Promise[Either[circe.Error, ZResponse[ZEventResult]]] = Promise()
  zQuery.get[ZQueryParamsEvent, ZEventResult](ZQueryParamsEvent(eventids = "11154506"), zrParams)
  for {
    r <- zrParams.future
  } yield {
    r match {
      case Left(err) => log.error(s"ZQuery for recovery(11154506) failed.", err)
      case Right(response) => {
        log.info("ZQuery recovery succeed.")
        for {
          zRecovery <- response.result
        } yield {
            val currentClock = LocalDateTime.ofEpochSecond(zRecovery.clock.toLong, 0, ZoneOffset.UTC)
            log.info(s"RecoveryClock($currentClock)")
        }
      }
    }
  }
}