package com.normation.plugins.itop.service

import com.normation.rudder.web.rest.RestExtractorService
import com.normation.rudder.web.rest.RestUtils._

import net.liftweb.common._
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.JField
import net.liftweb.json.JString
import net.liftweb.json.JsonDSL._
import com.normation.rudder.domain.reports._

/**
 * Class in charge of generating the JSON from the
 * iTop compliance status and serving it to the API URL.
 */
class ItopApi(
    restExtractor: RestExtractorService
  , itopService  : ItopComplianceService
) extends RestHelper with Loggable {

  implicit class ComplianceLevelPercent(c: ComplianceLevel) {
    def percents: Map[String, Float] = Map(
        NotApplicableReportType.severity -> c.pc_notApplicable
      , SuccessReportType.severity       -> c.pc_success
      , RepairedReportType.severity      -> c.pc_repaired
      , ErrorReportType.severity         -> c.pc_error
      , UnexpectedReportType.severity    -> c.pc_unexpected
      , MissingReportType.severity       -> c.pc_missing
      , NoAnswerReportType.severity      -> c.pc_noAnswer
      , PendingReportType.severity       -> c.pc_pending
    )
  }

  import net.liftweb.json.JsonDSL._

  val requestDispatch : PartialFunction[Req, () => Box[LiftResponse]] = {

    case Get(Nil, req) => {
      implicit val action = "itopCompliance"
      implicit val prettify = restExtractor.extractPrettify(req.params)

      itopService.getItopCompliance match {
        case Full(rules) =>
          val json =
            (
            "rules" -> rules.map( ruleCompliance =>
              (
                  ("id" -> ruleCompliance.id.value)
                ~ ("compliance" -> ruleCompliance.compliance.percents)
                ~ ("nodes" -> ruleCompliance.nodeCompliances.map { nodeCompliance =>
                    (
                        ("id" -> nodeCompliance.id.value)
                      ~ ("compliance" -> nodeCompliance.compliance.percents)
                      ~ ("directives" -> nodeCompliance.directiveCompliances.map { case (id, directiveCompliance) =>
                          (
                              ("id" -> id.value)
                            ~ ("compliance" -> directiveCompliance.percents)
                          )
                      })
                    )
                  })
              )
            ) )

          toJsonResponse(None, json)

        case eb: EmptyBox =>
          val message = (eb ?~ ("Could not fetch Rules")).msg
          toJsonError(None, JString(message))
      }
    }

  }

  serve( "api" / "latest" / "itopcompliance" prefix requestDispatch)

}
