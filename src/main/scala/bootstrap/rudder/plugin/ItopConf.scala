package bootstrap.rudder.plugin

import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import com.normation.plugins.itop.ItopPluginDef
import com.normation.plugins.itop.service._

import bootstrap.liftweb.RudderConfig
import net.liftweb.common.Loggable

/**
 * Definition of services for the HelloWorld plugin.
 */
@Configuration
class ItopConf extends Loggable with  ApplicationContextAware with InitializingBean {

  var appContext : ApplicationContext = null

  override def afterPropertiesSet() : Unit = {
    //nothgin to do
  }

  override def setApplicationContext(applicationContext:ApplicationContext) = {
    appContext = applicationContext
  }

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

