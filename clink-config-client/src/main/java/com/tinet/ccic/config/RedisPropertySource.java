package com.tinet.ccic.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.PropertySource;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Redis配置源，实现从Redis中动态加载配置项，并且通过监听Redis事件实现配置项的动态更新
 * 配置项加载顺序：本地缓存(HashMap)>Redis>文件缓存>配置文件(app.properties)
 * 
 * @author Jiangsl
 *
 */
public class RedisPropertySource extends PropertySource<String> {
	private Logger logger = LoggerFactory.getLogger(getClass());
	private static final String SYSTEM_PROPERTY_CONFIG_ENVIRONMENT = "config.environment";// 配置环境，需要在环境变量或JVM参数中设置
	private static final String SYSTEM_PROPERTY_CONFIG_ENVIRONMENT_DEFAULT = "dev";// 配置环境的默认值
	private static final String SYSTEM_PROPERTY_CONFIG_SERVER = "config.server";// 配置中心服务器，即Redis的地址，需要在环境变量或JVM参数中设置
	private static final String SYSTEM_PROPERTY_CONFIG_SERVER_DEFAULT = "localhost:6379";// 配置中心服务器的默认值
	private Map<String, String> localCache;
	private String fileCacheLocation;// 文件缓存的保存地址
	private Properties fileCacheProperties;// 从文件缓存中解析出的配置项
	private JedisPool jedisPool;// Redis连接池对象
	private String redisKey;// 在Redis中存储配置项的key

	/**
	 * @param name
	 * @param source
	 */
	public RedisPropertySource(String name, String source) {
		super(name, source);

		localCache = new HashMap<>();

		// 设置文件缓存路径，默认保存至用户目录下的appId.properties
		if (source != null && !source.isEmpty()) {
			String userHome = System.getProperty("user.home");
			fileCacheLocation = userHome + File.separator + "clink" + File.separator + source + ".properties";
			File cacheFile = new File(fileCacheLocation);
			if (!cacheFile.exists()) {
				//如果目标文件所在的目录不存在，则创建父目录  
				if(!cacheFile.getParentFile().exists()) {  
		            cacheFile.getParentFile().mkdirs();
		        } 
				
				//如果目标文件不存在，则创建文件
				try {
					cacheFile.createNewFile();
				} catch (IOException e) {
					logger.error("文件缓存创建失败", e);
				}
			}
		}

		// 读取环境变量
		String env = System.getProperty(SYSTEM_PROPERTY_CONFIG_ENVIRONMENT, SYSTEM_PROPERTY_CONFIG_ENVIRONMENT_DEFAULT);
		String redisUrl = System.getProperty(SYSTEM_PROPERTY_CONFIG_SERVER, SYSTEM_PROPERTY_CONFIG_SERVER_DEFAULT);
		redisKey = "config:" + env;
		String redisHost = redisUrl.substring(0, redisUrl.indexOf(':'));
		int redisPort = Integer.parseInt(redisUrl.substring(redisUrl.indexOf(':') + 1, redisUrl.length()));

		// 初始化Redis连接池
		JedisPoolConfig poolConfig = new JedisPoolConfig();
		poolConfig.setMaxTotal(10);
		poolConfig.setMaxIdle(10);
		poolConfig.setMinIdle(1);
		jedisPool = new JedisPool(poolConfig, redisHost, redisPort);
		new Thread(new RedisAsynListener(jedisPool,localCache)).start();;
	}

	@Override
	public Object getProperty(String key) {
		// 从本地缓存中获取
		String value = loadPropertyFromLocalCache(key);
		if (value == null) {
			// 从Redis中获取
			value = loadPropertyFromRedis(key);
			if (value != null) {

				// 保存到本地缓存
				savePropertyToLocalCache(key, value);

				// 保存到文件缓存
				savePropertyToFileCache(key, value);
			} else {

				// 从文件缓存中加载
				value = loadPropertyFromFileCache(key);
				if (value != null) {
					// 保存到本地缓存
					savePropertyToLocalCache(key, value);
				}
			}
		}

		if (value == null) {
			logger.warn("在Redis和文件缓存中都找不到配置项：" + key);
		}

		return value;
	}

	/**
	 * 从Redis中加载配置项
	 * 
	 * @param key
	 *            配置项的key
	 * @return 配置项的value
	 */
	private String loadPropertyFromRedis(String key) {
		String value = null;
		Jedis jedis = null;

		try {
			jedis = jedisPool.getResource();
			value = jedis.hget(redisKey, key);
		} catch (Exception e) {
			logger.error("从Redis中加载配置项时出错", e);
		} finally {
			if (jedis != null) {
				jedis.close();
			}
		}

		if (value == null) {
			logger.warn("在Redis中找不到配置项：" + key);
		} else {
			logger.debug("从Redis中加载了配置项：" + key + "=" + value);
		}

		return value;
	}

	/**
	 * 从本地缓存中加载配置项
	 * 
	 * @param key
	 *            配置项的key
	 * @return 配置项的value
	 */
	private String loadPropertyFromLocalCache(String key) {
		return localCache.get(key);
	}

	/**
	 * 将配置项写入本地缓存
	 * 
	 * @param key
	 *            配置项的key
	 * @param value
	 *            配置项的value
	 */
	private void savePropertyToLocalCache(String key, String value) {
		localCache.put(key, value);
	}

	/**
	 * 从文件缓存中加载配置项
	 * 
	 * @param key
	 *            配置项的key
	 * @return 配置项的value
	 */
	private String loadPropertyFromFileCache(String key) {
		if (fileCacheLocation == null) {
			logger.error("文件缓存路径有误，appId未设置。");
			return null;
		}

		if (fileCacheProperties == null) {

			FileInputStream fis = null;
			InputStreamReader isr = null;
			try {
				fis = new FileInputStream(fileCacheLocation);
				isr = new InputStreamReader(fis, "UTF-8");
				fileCacheProperties = new Properties();
				fileCacheProperties.load(isr);
			} catch (IOException e) {
				logger.error("从文件缓存加载配置项时发生错误 ", e);
				return null;
			} finally {
				try {
					isr.close();
				} catch (IOException e) {
					logger.error("从文件缓存加载配置项时发生错误  ", e);
				}

				try {
					fis.close();
				} catch (IOException e) {
					logger.error("从文件缓存加载配置项时发生错误 ", e);
				}
			}
		}

		String value = fileCacheProperties.getProperty(key);

		if (value == null) {
			logger.warn("在文件缓存中找不到配置项：" + key);
		} else {
			logger.debug("从文件缓存中加载了配置项：" + key + "=" + value);
		}

		return value;
	}

	/**
	 * 将配置项写入文件缓存
	 * 
	 * @param key
	 *            配置项的key
	 * @param value
	 *            配置项的value
	 */
	private void savePropertyToFileCache(String key, String value) {
		if (fileCacheLocation == null) {
			logger.error("文件缓存路径有误，appId未设置。");
			return;
		}

		if (fileCacheProperties == null) {
			fileCacheProperties = new Properties();
		}

		FileOutputStream fos = null;
		OutputStreamWriter osw = null;
		try {
			fos = new FileOutputStream(fileCacheLocation, false);
			osw = new OutputStreamWriter(fos, "UTF-8");
			fileCacheProperties.setProperty(key, value);
			fileCacheProperties.store(osw, null);
		} catch (IOException e) {
			logger.error("将配置项写入文件缓存时发生错误 ", e);
		} finally {
			try {
				osw.close();
			} catch (IOException e) {
				logger.error("将配置项写入文件缓存时发生错误 ", e);
			}

			try {
				fos.close();
			} catch (IOException e) {
				logger.error("将配置项写入文件缓存时发生错误 ", e);
			}
		}

		logger.debug("将配置项写入文件缓存：" + key + "=" + value);
	}

}
