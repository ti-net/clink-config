# 客户端
## Maven配置
```xml
<dependency>
    <groupId>com.tinet</groupId>
    <artifactId>clink-config-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Spring配置文件
```xml
<bean class="com.tinet.ccic.config.RedisPropertySourcesPlaceholderConfigurer">
		<property name="appId" value="myAppId" />
</bean>
```

## 设置环境变量或JVM启动参数
### 环境变量方式
```
export config.server=localhost:6379
export config.environment=dev
```
### JVM启动参数方式
```
-Dconfig.server=localhost:6379
-Dconfig.environment=dev
```

## 在程序中获取配置
```java
import org.springframework.core.env.Environment;

@Autowired
private Environment environment;

String value = environment.getProperty(key);
```

# 服务端
GUI尚未实现，暂时只能在key=config:{$config.environment}的Redis Hash结构中手工添加。
