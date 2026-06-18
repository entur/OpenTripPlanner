package org.opentripplanner.ext.interactivelauncher.configuration;

import org.opentripplanner.ext.interactivelauncher.api.LauncherRequestDecorator;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration(proxyBeanMethods = false)
public class InteractiveLauncherModule {

  static LauncherRequestDecorator decorator = request -> request;

  public static void setRequestInterceptor(LauncherRequestDecorator decorator) {
    InteractiveLauncherModule.decorator = decorator;
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  LauncherRequestDecorator requestDecorator() {
    return decorator;
  }
}
