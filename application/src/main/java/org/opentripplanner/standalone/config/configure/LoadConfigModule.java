package org.opentripplanner.standalone.config.configure;

import java.io.File;
import org.opentripplanner.core.framework.di.TransitServicePeriod;
import org.opentripplanner.core.model.time.LocalDateInterval;
import org.opentripplanner.datastore.api.OtpBaseDirectory;
import org.opentripplanner.datastore.api.OtpDataStoreConfig;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.standalone.config.CommandLineParameters;
import org.opentripplanner.standalone.config.ConfigModel;
import org.opentripplanner.standalone.config.OtpConfigLoader;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

/**
 * Load and create the {@link ConfigModel} using the provided configuration file
 * directory.
 * <p>
 * The included {@link ConfigModule} is used to bind/map {@link ConfigModel} to more specific
 * types. The {@link ConfigModule} is a separate module to be able to use it without this module;
 * If the {@link ConfigModel} is already instantiated.
 * <p>
 * The binding to {@link OtpDataStoreConfig} and {@link TransitServicePeriod} is done
 * here, not in the {@link ConfigModel}, because they are only needed at load time - if this change,
 * then move the binding to the {@link ConfigModule}.
 */
@Configuration(proxyBeanMethods = false)
@Import(ConfigModule.class)
public class LoadConfigModule {

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  OtpConfigLoader providesConfigLoader(@OtpBaseDirectory File configDirectory) {
    return new OtpConfigLoader(configDirectory);
  }

  @Bean
  ConfigModel providesModel(OtpConfigLoader loader) {
    return new ConfigModel(loader);
  }

  /**
   * {@code @Primary} disambiguates {@link OtpDataStoreConfig} injection: {@link BuildConfig} also
   * implements {@link OtpDataStoreConfig}, so without this both this bean and the {@code BuildConfig}
   * bean would match. Dagger had no such ambiguity (its keys are exact types).
   */
  @Bean
  @Primary
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  OtpDataStoreConfig providesDataStoreConfig(BuildConfig buildConfig) {
    return buildConfig;
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @TransitServicePeriod
  LocalDateInterval providesTransitServicePeriod(BuildConfig buildConfig) {
    return buildConfig.getTransitServicePeriod();
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @OtpBaseDirectory
  File baseDirectory(CommandLineParameters cli) {
    return cli.getBaseDirectory();
  }
}
