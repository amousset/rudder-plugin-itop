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

/**
 * Class in charge of generating the JSON from the
 * iTop compliance status and serving it to the API URL.
 */
class ItopApi(
    restExtractor: RestExtractorService
  , itopService  : ItopComplianceService
) extends RestHelper with Loggable {

  import net.liftweb.json.JsonDSL._

  val requestDispatch : PartialFunction[Req, () => Box[LiftResponse]] = {

    case Get(Nil, req) => {
      implicit val action = "itopCompliance"
      implicit val prettify = restExtractor.extractPrettify(req.params)

      itopService.getItopCompliance match {
        case Full(rules) =>
          val json = JField( "rules",
            rules.map( ruleCompliance =>
              (
                  ("id" -> ruleCompliance.id.value)
                ~ ("compliance" -> ruleCompliance.compliance.percents)
                ~ ("nodes" -> ruleCompliance.nodeCompliances.map { nodeCompliance =>
                    (
                        ("id" -> nodeCompliance.id.value)
                      ~ ("compliance" -> nodeCompliance.compliance.percents)
                      ~ ("directives" -> nodeCompliance.directiveCompliances.map { case (id,status) =>
                          (
                              ("id" -> id.value)
                            ~ ("status" -> status.severity)
                          )
                      })
                    )
                  })
              )
            )
          )

          toJsonResponse(None, json)

        case eb: EmptyBox =>
          val message = (eb ?~ ("Could not fetch Rules")).msg
          toJsonError(None, JString(message))
      }
    }

  }

  serve( "api" / "latest" / "itopcompliance" prefix requestDispatch)

}
