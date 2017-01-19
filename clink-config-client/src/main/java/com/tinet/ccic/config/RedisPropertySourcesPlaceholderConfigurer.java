package com.tinet.ccic.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;

/**
 * 基于Redis的配置管理器
 * 
 * @author Jiangsl
 *
 */
public class RedisPropertySourcesPlaceholderConfigurer extends PropertySourcesPlaceholderConfigurer {
	private Logger logger = LoggerFactory.getLogger(getClass());
	private static final String REDIS_PROPERTIES_PROPERTY_SOURCE_NAME = "redisProperties";

	private MutablePropertySources propertySources;
	private Environment environment;
	private String appId;

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		if (this.propertySources == null) {
			this.propertySources = new MutablePropertySources();

			RedisPropertySource redisPropertySource = new RedisPropertySource(REDIS_PROPERTIES_PROPERTY_SOURCE_NAME,
					appId);
			this.propertySources.addLast(redisPropertySource);
			
			if (this.environment != null) {
				((AbstractEnvironment) environment).getPropertySources().addLast(redisPropertySource);
			}

			try {
				PropertySource<?> localPropertySource = new PropertiesPropertySource(
						LOCAL_PROPERTIES_PROPERTY_SOURCE_NAME, mergeProperties());
				this.propertySources.addLast(localPropertySource);
				
				if (this.environment != null) {
					((AbstractEnvironment) environment).getPropertySources().addLast(localPropertySource);
				}
			} catch (IOException ex) {
				throw new BeanInitializationException("Could not load properties", ex);
			}

		}

		processProperties(beanFactory, new PropertySourcesPropertyResolver(this.propertySources));
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

}
