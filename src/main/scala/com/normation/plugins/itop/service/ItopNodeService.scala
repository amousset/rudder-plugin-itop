package com.normation.plugins.itop.service

import com.normation.inventory.domain._
import com.normation.rudder.domain.policies.DirectiveId
import com.normation.rudder.domain.policies.RuleId
import com.normation.rudder.domain.reports.ComplianceLevel
import com.normation.rudder.repository.RoNodeGroupRepository
import com.normation.rudder.repository.RoRuleRepository
import com.normation.rudder.services.nodes.NodeInfoService
import com.normation.rudder.services.reports.ReportingService

import net.liftweb.common._




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
  , compliance     : ComplianceLevel
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
  , compliance          : ComplianceLevel
  , directiveCompliances: Seq[(DirectiveId, ComplianceLevel)]
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



  /**
   * Get the compliance for everything
   */
  def getItopCompliance() : Box[Seq[ItopRuleCompliance]] = {

    for {
      rules     <- rulesRepo.getAll()
      groupLib  <- nodeGroupRepo.getFullGroupLibrary()
      nodeInfos <- nodeInfoService.getAll()
    } yield {


      // get an empty-initialized array of compliances to be used
      // as defaults
      val initializedCompliances : Map[RuleId, ItopRuleCompliance] = {
        (rules.map { rule =>
          val nodeIds = groupLib.getNodeIds(rule.targets, nodeInfos)

          (rule.id, ItopRuleCompliance(
              rule.id
            , ComplianceLevel(noAnswer = nodeIds.size)
            , (nodeIds.map { nodeId =>
                ItopNodeCompliance(
                    nodeId
                  , ComplianceLevel(noAnswer = rule.directiveIds.size)
                  , rule.directiveIds.map { id => (id, ComplianceLevel(noAnswer = 1))}.toSeq
                )
              }).toSeq
          ))
        }).toMap
      }

      //reports by rules
      //that's a *little* gruikkk because, well,
      //we just plainly ignore errors

      //Box[Set[RuleNodeStatusReport]]

      val reportsByNodes = (
          reportingService.findRuleNodeStatusReports(nodeInfos.keySet, rules.map(_.id).toSet)
          .toSeq.flatten
          .groupBy( _.ruleId )
      )

      //for each rule for each node, we want to have a
      //directiveId -> reporttype map
      val nonEmptyRules = reportsByNodes.map { case (ruleId, nodeStatusReports) =>
        (
          ruleId,
          ItopRuleCompliance(
              ruleId
            , ComplianceLevel.sum(nodeStatusReports.map(_.compliance))
            , nodeStatusReports.map(r =>
                ItopNodeCompliance(
                    r.nodeId
                  , r.compliance
                  , r.directives.toSeq.map { case (directiveId, directiveReport) => (directiveId, directiveReport.compliance) }
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

