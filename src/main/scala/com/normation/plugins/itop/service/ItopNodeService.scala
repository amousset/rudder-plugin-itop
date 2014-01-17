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


case class CompositeStatus(
    itemNumber   : Int
  , notApplicable: Int = 0
  , success      : Int = 0
  , repaired     : Int = 0
  , error        : Int = 0
  , unknown      : Int = 0
  , noReport     : Int = 0
  , applying     : Int = 0
) {
  private[this] def percent(i:Int) = Math.round(100 * i.toFloat / itemNumber)

  //percents for items - approximated !!!
  //only for displaying
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


case class ItopRuleCompliance(
    id             : RuleId
    //compliance by nodes
  , compliance     : CompositeStatus
  , nodeCompliances: Seq[ItopNodeCompliance]
)

case class ItopNodeCompliance(
    id                  : NodeId
    //compliance by directive (by nodes)
  , compliance          : CompositeStatus
  , directiveCompliances: Seq[(DirectiveId, ReportType)]
)

/**
 * The logic of interaction with itop
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
        val nodes = nodeStatusReports.map(r =>

          ItopNodeCompliance(
              r.nodeId
            , CompositeStatus(
                  r.directives.size
                , notApplicable = r.directives.count( _.directiveReportType == NotApplicableReportType)
                , success       = r.directives.count( _.directiveReportType == SuccessReportType)
                , repaired      = r.directives.count( _.directiveReportType == RepairedReportType)
                , error         = r.directives.count( _.directiveReportType == ErrorReportType)
                , unknown       = r.directives.count( _.directiveReportType == UnknownReportType)
                , noReport      = r.directives.count( _.directiveReportType == NoAnswerReportType)
                , applying      = r.directives.count( _.directiveReportType == PendingReportType)
              )
            , r.directives.map(d => (d.directiveId, d.directiveReportType))
          )
        )

        (
          ruleId,
          ItopRuleCompliance(
              ruleId
            , CompositeStatus(
                  nodes.size
                , notApplicable = nodeStatusReports.count( _.nodeReportType == NotApplicableReportType)
                , success       = nodeStatusReports.count( _.nodeReportType == SuccessReportType)
                , repaired      = nodeStatusReports.count( _.nodeReportType == RepairedReportType)
                , error         = nodeStatusReports.count( _.nodeReportType == ErrorReportType)
                , unknown       = nodeStatusReports.count( _.nodeReportType == UnknownReportType)
                , noReport      = nodeStatusReports.count( _.nodeReportType == NoAnswerReportType)
                , applying      = nodeStatusReports.count( _.nodeReportType == PendingReportType)
              )
            , nodes
          )
        )
      }.toMap

      //(initializedCompliances ++
          (nonEmptyRules).values.toSeq

    }
  }
}

