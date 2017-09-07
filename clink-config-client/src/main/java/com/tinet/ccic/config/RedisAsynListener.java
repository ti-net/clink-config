package com.tinet.ccic.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

/**
 * 
 * @author lihf
 * @date 2017年9月7日
 */
public class RedisAsynListener implements Runnable {
	private static boolean single = false;
	private Map<String, String> localCache = null; // 本地缓存索引
	private JedisPool jedisPool; // Redis连接池对象
	private String item = null;
	private String value = null;

	public RedisAsynListener(JedisPool jedisPool, Map<String, String> localCache) {
		this.localCache = localCache;
		this.jedisPool = jedisPool;
	}

	public  void singleThreadListenerAction() {
		if (!single) {
			this.run();
			single=true;
		}
	}

	@Override
	public void run() {
		// 设置监听redis配置中心得异步监听线程
		jedisPool.getResource().subscribe(new TinetJedisPubSub(), "itemChannel", "valueChannel");
	}

	/**
	 * 定义内部类，用于实现redis消息的订阅处理
	 * 
	 * @author lihf
	 * @date 2017年9月6日
	 */
	class TinetJedisPubSub extends JedisPubSub {
		private Logger logger = LoggerFactory.getLogger(getClass());

		@Override
		public void onMessage(String channel, String message) {
			logger.info("channel:" + channel + " receives message :" + message);

			if (channel.equals("itemChannel")) {
				item = message;
			} else if (channel.equals("valueChannel")) {
				value = message;
			}
			// 保存到本地缓存
			if (item != null && !item.isEmpty() && value != null && !value.isEmpty()) {
				if (localCache.containsKey(item)) {
					localCache.replace(item, value);
				} else {
					localCache.put(item, value);
				}
				item = null;
				value = null;
			}

		}

		@Override
		public void onSubscribe(String channel, int subscribedChannels) {
			logger.info("channel:" + channel + "has been subscribed:" + subscribedChannels);
		}

	}

}
