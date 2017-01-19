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
    <property name="locations">
        <list>
            <value>classpath*:app.properties</value>
        </list>
    </property>
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

## 文件缓存
客户端会在${user.home}/${appId}.properties中缓存Redis中的配置项内容。
若没有连接Redis或Redis中找不到配置项，客户端会从文件缓存中加载。

## 配置项的加载顺序
Redis > 文件缓存 > 配置文件(app.properties)
即Redis和文件缓存中都找不到配置项时，客户端会从配置文件(app.properties)中加载。
在本地开发环境，可以不连接Redis，只使用配置文件(app.properties)，以便于测试。

# 服务端
GUI尚未实现，暂时只能在key=config:{$config.environment}的Redis Hash结构中手工添加。
