package com.tinet.ccic.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

/**Redis配置消息发布异步监听类
 * 
 * @author lihf
 * @date 2017年9月7日
 */
public class RedisAsynListener implements Runnable {
	private Map<String, String> localCache = null; // 本地缓存索引
	private JedisPool jedisPool; // Redis连接池对象

	public RedisAsynListener(JedisPool jedisPool, Map<String, String> localCache) {
		this.localCache = localCache;
		this.jedisPool = jedisPool;
	}

	@Override
	public void run() {
		// 设置监听redis配置中心得异步监听线程
		jedisPool.getResource().subscribe(new ConfigJedisPubSub(), "configChannel");
	}

	/**
	 * 定义内部类，用于实现redis消息的订阅处理
	 * 
	 * @author lihf
	 * @date 2017年9月6日
	 */
	class ConfigJedisPubSub extends JedisPubSub {
		private Logger logger = LoggerFactory.getLogger(getClass());

		@Override
		public void onMessage(String channel, String message) {
			logger.info("channel:" + channel + " receives message :" + message);

			// 清空本地缓存对应的项，从redis服务器加载
			if (localCache.containsKey(message)) {
				localCache.remove(message);
			}
		}
	}
}
