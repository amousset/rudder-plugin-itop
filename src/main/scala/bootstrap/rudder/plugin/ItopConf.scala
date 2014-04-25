package bootstrap.rudder.plugin

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import com.normation.plugins.itop.ItopPluginDef
import com.normation.plugins.itop.service.ItopApi
import com.normation.plugins.itop.service.ItopComplianceService

import bootstrap.liftweb.RudderConfig

/**
 * A simple module which add a new API URL and allows to
 * serve data describing the compliance of rules/nodes/directives.
 */
@Configuration
class ItopConf {

  @Bean def itopPlugin = new ItopPluginDef(itopApi)

  @Bean def itopApi = new ItopApi(
      RudderConfig.restExtractorService
    , new ItopComplianceService(
          RudderConfig.roRuleRepository
        , RudderConfig.nodeInfoService
        , RudderConfig.roNodeGroupRepository
        , RudderConfig.reportingService
      )
  )
}

