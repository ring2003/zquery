package op.services

import java.io.File
import java.time.Duration
import java.util.Date
import akka.event.LoggingAdapter
import com.taskadapter.redmineapi.bean._
import com.taskadapter.redmineapi.{Params, RedmineManager, RedmineManagerFactory}
import com.typesafe.config.{Config, ConfigFactory}
import scala.concurrent.duration._
import scala.collection.convert.ImplicitConversionsToScala._
import scala.util.{Failure, Success, Try}
import scalaz.concurrent.Task

/**
  * Created by codemonkey on 2017/3/28.
  */


sealed case class RedmineQuery(
                                project_id: String = "",
                                set_filter:Int = 1,
                                statusId: String = "status_id",
                                statusIdOp: String = "=",
                                statusIdValue: String = "",
                                customFieldEventId: String = "cf_5",
                                customFieldEventOp: String = "=",
                                customFieldEventValue: String = ""
                              ) {
  def toParams: Params = {
    new Params()
      .add("set_filter", set_filter.toString)
      .add("project_id", project_id)
      .add("f[]", statusId)
      .add("op[status_id]", statusIdOp)
      .add("v[status_id]", statusIdValue)
      .add("f[]", customFieldEventId)
      .add(s"op[$customFieldEventId]", customFieldEventOp)
      .add(s"v[$customFieldEventId][]", customFieldEventValue)
  }
}

sealed case class RedmineRuntimeDefaults(
                                          trackerName: String,
                                          statusId: Int,
                                          priority: Int,
                                          assignee: String,
                                          alertTypeId: Int = 0,
                                          priorityId: Int = 2,
                                          hostIpId: Int = 4,
                                          zabbixEventId: Int = 5
                                        ) {
  def getCustomFields(alertType: String, priority: String, hostIp: Option[String], zabbixEvent: String): Seq[CustomField] = {
    val customFields =
      Seq(
        CustomFieldFactory.create(alertTypeId, "告警类型", alertType),
        CustomFieldFactory.create(priorityId, " 事故级别", priority),
        CustomFieldFactory.create(zabbixEventId, "Zabbix事件ID", zabbixEvent)
      )
    if (hostIp.nonEmpty) customFields :+ CustomFieldFactory.create(hostIpId, "主机IP", hostIp.get)
    else customFields
  }

}

case class RedmineIssue(sendTo:String, issueSubject:String, issueContent: String)

case class RedmineNewAlert(
                            alertName: String,
                            alertBody: String,
                            alertType: String, // 告警类型
                            hostIp: Option[String], // 主机地址
                            eventId: String)

case class RedmineCloseAlert(eventId: String)

final class Redmine(config: Config) {
  private val url = config.getString("ops.redmine.url")
  private val username = config.getString("ops.redmine.username")
  private val password = config.getString("ops.redmine.password")
  private val apiKey = config.getString("ops.redmine.api-key")
  private val projKey = config.getString("ops.redmine.project-key")
  private val defaultsConfigFile = config.getString("ops.redmine.defaults-config-file")
  private val retries = Seq(1 seconds, 5 seconds, 15 seconds)

  def closeIssue(eventId: String, spentTime: Duration)(implicit logger:LoggingAdapter):Seq[Int] = {
    Try(
      Task(ConfigFactory.parseFile(new File(defaultsConfigFile))).retry(retries).unsafePerformSync
    )
    match {
      case Success(config) => {
        try {
          val red = RedmineManagerFactory.createWithUserAuth(url, username, password)
          val project = red.getProjectManager.getProjectByKey(projKey)
          val customFieldEventId = config.getString("ops.redmine.customfields.eventId")
          val issueManager = red.getIssueManager
          val cf = s"cf_$customFieldEventId"
          Try(
            Task(
              RedmineQuery(project_id = projKey, customFieldEventId=cf, customFieldEventValue = eventId).toParams
            )
              .retry(retries)
              .unsafePerformSync
          )
          match {
            case Success(params) => {
              Try(
                Task(issueManager.getIssues(params)).retry(retries).unsafePerformSync
              ) match {
                case Success(resultsWrapper) => {
                  for {
                    i <- `list asScalaBuffer`[Issue](resultsWrapper.getResults)
                    issueId = i.getId
                    statusId = i.getStatusId
                    if statusId < 5
                  } yield {
                    val now = new Date()
                    logger.info(s"Handling issue($issueId) - status($statusId).")
                    i.setDescription("This alert has automatically recovered.")
                    i.setStatusId(5)
                    i.setDoneRatio(100)
                    i.setClosedOn(now)
                    i.getCustomFieldById(6).setValue(spentTime.toString.dropWhile(ch => !ch.isDigit))
                    Try(Task(issueManager.update(i)).retry(retries).unsafePerformSync) match {
                      case Success(_) => {
                        logger.info(s"Completed update issue($issueId).")
                        Some(issueId)
                      }
                      case Failure(exc) => {
                        logger.error(exc, exc.getStackTrace.mkString("\n"))
                        None
                      }
                    }
                  }
                }
                  .filter(_.nonEmpty)
                  .map(_.get.toInt)
                case Failure(y) => {
                  logger.error("Got an error while retrieve issue from issueManager.",y.getCause)
                  Seq()
                }
              }
            }
            case Failure(y) => {
              logger.error("Failed to query redmine.", y.getCause)
              Seq()
            }
          }
        } catch {
          case exc:Throwable => {
            logger.error("Error raised during close issue.",exc)
            Seq()
          }
        }
      }
      case Failure(y) => {
        logger.error("Error raised during parse config file.", y.getCause)
        Seq()
      }
    }
  }


  def getRedmineClient()(implicit logger: LoggingAdapter): Either[Throwable,(RedmineManager,Project)] = {
    Try(
      Task(RedmineManagerFactory.createWithUserAuth(url, username, password))
        .retry(retries)
        .unsafePerformSync
    ) match {
      case Success(red) => {
        Try(
          Task(red.getProjectManager.getProjectByKey(projKey))
            .retry(retries)
            .unsafePerformSync
        ) match {
          case Success(project) => Right(red, project)
          case Failure(y) => {
            logger.error(y, y.getStackTrace.mkString("\n"))
            Left(y)
          }
        }
      }
      case Failure(y) => {
        logger.error(y, y.getStackTrace.mkString("\n"))
        Left(y)
      }
    }
  }

  def createIssue(alertName: String, alertBody: String, alertType: String, priority: String, hostIp: Option[String], eventId: String)(implicit logger:LoggingAdapter): Either[Throwable, String] = {
    Try(
      Task(ConfigFactory.parseFile(new File(defaultsConfigFile))).retry(retries).unsafePerformSync
    ) match {
      case Success(conf) => {
        Try(
          RedmineRuntimeDefaults(
            trackerName = conf.getString("ops.defaults.tracker-name"),
            statusId = conf.getInt("ops.defaults.status-id"),
            priority = conf.getInt("ops.defaults.priority-id"),
            assignee = conf.getString("ops.defaults.assignee"),
            hostIpId = conf.getInt("ops.defaults.hostip-id"),
            zabbixEventId = conf.getInt("ops.defaults.zabbix-event-id")
          )
        ) match {
          case Success(defaults) => {
            getRedmineClient()
              .fold({ exc =>
                logger.error("Initiate redmine client failed.")
                Left(exc)
              },{ x =>
                val red = x._1
                val project = x._2
                val now = new Date()
                logger.info("Load runtime defaults success.")
                val issue = IssueFactory.create(project.getId(), alertName)
                val tracker = project.getTrackerByName(defaults.trackerName)
                issue.setProjectId(project.getId)
                issue.setTracker(tracker)
                issue.setStatusId(defaults.statusId)
                issue.setPriorityId(defaults.priority)
                issue.setDescription(alertBody)
                issue.setAssigneeId(config.getInt(s"ops.redmine.groups.${defaults.assignee}"))
                issue.setCreatedOn(now)
                issue.setUpdatedOn(now)

                logger.info("Now adding custom fields.")
                defaults
                  .getCustomFields(alertType = alertType, priority=priority, hostIp = hostIp, zabbixEvent = eventId)
                  .foreach(i => issue.addCustomField(i))

                logger.info("Now start creating issue.")
                Try(
                  Task(red.getIssueManager.createIssue(issue)).retry(retries).unsafePerformSync
                ) match {
                  case Success(newIssue) => {
                    logger.info("Create issue succeed.")
                    Right(newIssue.getId.toString)
                  }
                  case Failure(exc) => {
                    logger.error("Create issue failed.", exc)
                    Left(exc)
                  }
                }
              })
          }
          case Failure(exc) => {
            logger.error(exc, exc.getStackTrace.mkString("\n"))
            Left(exc)
          }
        }
      }
      case Failure(exc) => {
        logger.error(exc, exc.getStackTrace.mkString("\n"))
        Left(exc)
      }
    }
  }

}