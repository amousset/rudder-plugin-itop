package com.normation.plugins.itop

import scala.xml.NodeSeq
import com.normation.plugins.PluginName
import com.normation.plugins.PluginVersion
import com.normation.plugins.RudderPluginDef
import bootstrap.liftweb.ClassPathResource
import net.liftweb.common.Loggable
import net.liftweb.http.LiftRules
import com.normation.plugins.itop.service.ItopApi

class ItopPluginDef(itopApi: ItopApi) extends RudderPluginDef with Loggable {

  val name = PluginName("iTop Compliance API")
  val basePackage = "com.normation.plugins.itop"
  val version = PluginVersion(1,0,0)
  val description : NodeSeq  =
    <div>
    This plugin allows to use a new API which allows to
    get the current compliance for all rules, for all nodes,
    for all directives.

    The API URL is: <b>/api/latest/itopcompliance</b>

    JSON format:
    <pre>
{
  "action": "itopCompliance",
  "result": "success",
  "data": "rules": [
    {
      "id": "dff88a69-8988-4655-bc6b-0890f74207d7",
      "compliance": {
        "noReport": 100
      },
      "nodes": [
        {
          "id": "root",
          "compliance": {
            "noReport": 100
          },
          "directives": [
            {
              "id": "53714a9c-04d7-48f9-a043-c966ea674ae7",
              "status": "NoAnswer"
            }
          ]
        }
      ]
    }
  ]
}
    </pre>
    The compliance is a percent for the corresponding state.

    </div>

  val configFiles = Seq(ClassPathResource("demo-config-1.properties"), ClassPathResource("demo-config-2.properties"))


  def init = {
    logger.info("loading iTop plugin")
    LiftRules.statelessDispatch.append(itopApi)
  }

  def oneTimeInit : Unit = {}

}
