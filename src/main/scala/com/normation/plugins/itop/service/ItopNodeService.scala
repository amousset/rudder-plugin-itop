package com.normation.plugins.itop.service

import com.normation.inventory.domain._
import com.normation.rudder.domain.policies.DirectiveId
import com.normation.rudder.domain.policies.RuleId
import com.normation.rudder.domain.reports.bean.NodeStatusReport
import com.normation.rudder.domain.reports.bean.ReportType
import com.normation.rudder.repository.RoNodeGroupRepository
import com.normation.rudder.repository.RoRuleRepository
import com.normation.rudder.services.nodes.NodeInfoService
import com.normation.rudder.services.reports.ReportingService
import net.liftweb.common._
import net.liftweb.http.LiftResponse
import net.liftweb.http.rest.RestHelper
import com.normation.rudder.domain.reports.bean._


/**
 * Describe the status of a number of items.
 */
case class CompositeStatus(
    notApplicable: Int = 0
  , success      : Int = 0
  , repaired     : Int = 0
  , error        : Int = 0
  , unknown      : Int = 0
  , noReport     : Int = 0
  , applying     : Int = 0
) {
  private[this] def percent(i:Int) = Math.round(100 * i.toFloat / itemNumber)

  val itemNumber = notApplicable + success + repaired + error + unknown + noReport + applying

  /**
   * Get a list of percents of items - approximated !!!
   * in each status.
   * Only get non zero value.
   */
  def percents: Map[String, Int] = Map(
      "notApplicable" -> percent(notApplicable)
    , "success"       -> percent(success)
    , "repaired"      -> percent(repaired)
    , "error"         -> percent(error)
    , "unknown"       -> percent(unknown)
    , "noReport"      -> percent(noReport)
    , "applying"      -> percent(applying)
  ).filter( _._2 != 0)
}

/**
 * Factory from CompositeStatus from a set of objects
 */
object CompositeStatus {

  def apply[T](reports: Seq[ReportType]) = {
    new CompositeStatus(
        notApplicable = reports.count( _ == NotApplicableReportType)
      , success       = reports.count( _ == SuccessReportType)
      , repaired      = reports.count( _ == RepairedReportType)
      , error         = reports.count( _ == ErrorReportType)
      , unknown       = reports.count( _ == UnknownReportType)
      , noReport      = reports.count( _ == NoAnswerReportType)
      , applying      = reports.count( _ == PendingReportType)
    )
  }
}


/**
 * Compliance for a rules.
 * It lists:
 * - id: the rule id
 * - compliance: the compliance of the rule by node
 *   (total number of node, repartition of node by status)
 * - nodeCompliance: the list of compliance for each node, for that that rule
 */
case class ItopRuleCompliance(
    id             : RuleId
    //compliance by nodes
  , compliance     : CompositeStatus
  , nodeCompliances: Seq[ItopNodeCompliance]
)

/**
 * Compliance for node, for a given set of directives
 * (normally, all the directive for a given rule)
 * It lists:
 * - id: the node id
 * - compliance: total number of directives and
 *   repartition of directive compliance by status
 * - directiveCompliances: status for each directive, for that node.
 */
case class ItopNodeCompliance(
    id                  : NodeId
    //compliance by directive (by nodes)
  , compliance          : CompositeStatus
  , directiveCompliances: Seq[(DirectiveId, ReportType)]
)

/**
 * The class in charge of getting and calculating
 * compliance for all rules/nodes/directives.
 */
class ItopComplianceService(
    rulesRepo       : RoRuleRepository
  , nodeInfoService : NodeInfoService
  , nodeGroupRepo   : RoNodeGroupRepository
  , reportingService: ReportingService
) {



  /*
   * For all rule, find:
   * - nodes targetted by that rules
   * - directives of the rule
   * - initialize the ItopRuleCompliance with "no reports"
   */

  private[this] def initializeCompliances() : Box[Map[RuleId, ItopRuleCompliance]] = {

    for {
      groupLib <- nodeGroupRepo.getFullGroupLibrary()
      nodeInfos <- nodeInfoService.getAll
      rules <- rulesRepo.getAll()
    } yield {

      (rules.map { rule =>
        val nodeIds = groupLib.getNodeIds(rule.targets, nodeInfos)

        (rule.id, ItopRuleCompliance(
            rule.id
          , CompositeStatus(nodeIds.size, noReport = nodeIds.size)
          , (nodeIds.map { nodeId =>
              ItopNodeCompliance(
                  nodeId
                , CompositeStatus(rule.directiveIds.size, noReport = rule.directiveIds.size)
                , rule.directiveIds.map { id => (id, NoAnswerReportType)}.toSeq
              )
            }).toSeq
        ))
      }).toMap
    }
  }



  def getItopCompliance() : Box[Seq[ItopRuleCompliance]] = {

    for {
      rules                  <- rulesRepo.getAll()
      initializedCompliances <- initializeCompliances
    } yield {

      val x = reportingService.findImmediateReportsByRules(rules.map(_.id).toSet)

      //reports by rules
      //that's a *little* gruikkk because, well,
      //we just plainly ignore errors
      val reportsByNodes = (
          reportingService.findImmediateReportsByRules(rules.map(_.id).toSet)
          .flatMap( _._2.flatMap( _.map( _.getNodeStatus )))
          .flatten.toSeq
          .groupBy( _.ruleId )
      )

      //for each rule for each node, we want to have a
      //directiveId -> reporttype map
      val nonEmptyRules = reportsByNodes.map { case (ruleId, nodeStatusReports) =>
        (
          ruleId,
          ItopRuleCompliance(
              ruleId
            , CompositeStatus(nodeStatusReports.map( _.nodeReportType))
            , nodeStatusReports.map(r =>
                ItopNodeCompliance(
                    r.nodeId
                  , CompositeStatus(r.directives.map( _.directiveReportType))
                  , r.directives.map(d => (d.directiveId, d.directiveReportType))
                )
              )
          )
        )
      }.toMap


      //return the full list, even for non responding nodes/directives
      //but override with values when available.
      (initializedCompliances ++ nonEmptyRules).values.toSeq

    }
  }
}

