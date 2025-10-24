# kafka-barista-service

> SpringBucks barista service for coffee brewing with Spring Cloud Stream Kafka message consumption

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2024.0.2-blue.svg)](https://spring.io/projects/spring-cloud)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.x-black.svg)](https://kafka.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A microservice that consumes coffee order events from Kafka, processes brewing operations, and publishes completion notifications using Spring Cloud Stream functional programming model.

## Features

- **Event-Driven Processing** with Spring Cloud Stream
- **Kafka Consumer** with functional programming model
- **Dynamic Message Sending** with StreamBridge
- **Transaction Management** for data consistency
- **Random UUID Generation** for barista identification
- **JPA Persistence** with MariaDB
- **Health Monitoring** with Spring Boot Actuator
- **Minimal Dependencies** - focused service design

## Tech Stack

- **Spring Boot** 3.4.5
- **Spring Cloud Stream** 2024.0.2
- **Apache Kafka** (KRaft mode)
- **Spring Data JPA** for persistence
- **MariaDB** database
- **Lombok** for code simplification
- **Maven** 3.8+

## Getting Started

### Prerequisites

- **JDK 21** or higher
- **Maven 3.8+** (or use included Maven Wrapper)
- **Running Kafka** (from kafka-waiter-service docker-compose)
- **MariaDB** with springbucks database

### Installation & Run

```bash
# Clone the repository
git clone https://github.com/SpringMicroservicesCourse/spring-cloud-stream-kafka
cd kafka-barista-service

# Ensure Kafka is running (from kafka-waiter-service directory)
cd ../kafka-waiter-service
docker-compose up -d
cd ../kafka-barista-service

# Run the application
./mvnw spring-boot:run
```

### Alternative: Run as JAR

```bash
# Build
./mvnw clean package

# Run
java -jar target/kafka-barista-service-0.0.1-SNAPSHOT.jar
```

## Configuration

### Application Properties

```properties
# Server Configuration
spring.application.name=barista-service
server.port=8070

# Barista ID Configuration
order.barista-prefix=springbucks-

# Database Configuration
spring.datasource.url=jdbc:mariadb://localhost:3306/springbucks
spring.datasource.username=springbucks
spring.datasource.password=springbucks

# Kafka Binder Configuration
spring.cloud.stream.kafka.binder.brokers=localhost
spring.cloud.stream.kafka.binder.defaultBrokerPort=9092

# Functional Programming Model
spring.cloud.function.definition=newOrders

# Input Binding (Receive new orders from waiter)
spring.cloud.stream.bindings.newOrders-in-0.destination=newOrders
spring.cloud.stream.bindings.newOrders-in-0.group=barista-service

# Output Binding (Send finished order notifications)
spring.cloud.stream.bindings.finishedOrders-out-0.destination=finishedOrders
```

### Configuration Highlights

| Property | Value | Description |
|----------|-------|-------------|
| `spring.cloud.function.definition` | newOrders | Function bean to bind |
| `group` | barista-service | Consumer group for load balancing |
| `order.barista-prefix` | springbucks- | Barista ID prefix |
| `${random.uuid}` | Auto-generated | Unique barista identifier |

## Message Flow

```
Waiter Service                Kafka                   Barista Service
      │                        │                            │
      ├──(1) Send order ID────>│                            │
      │     (newOrders topic)  │                            │
      │                        ├──(2) Deliver message──────>│
      │                        │                            │
      │                        │            (3) Process order & brew coffee
      │                        │                            │
      │                        │<──(4) Send completion──────┤
      │                        │   (finishedOrders topic)   │
      │<──(5) Deliver message──┤                            │
      │                        │                            │
```

## Key Components

### 1. New Order Listener

**File:** `integration/OrderListener.java`

```java
@Component
@Slf4j
@Transactional
public class OrderListener {
    @Autowired
    private CoffeeOrderRepository orderRepository;
    @Autowired
    private StreamBridge streamBridge;
    
    @Value("${order.barista-prefix}${random.uuid}")
    private String barista;
    
    @Value("${stream.bindings.finished-orders-binding}")
    private String finishedOrdersBindingFromConfig;
    
    /**
     * Functional bean to process new orders
     * Receives order ID, brews coffee, sends completion notification
     */
    @Bean
    public Consumer<Long> newOrders() {
        return id -> {
            // Fetch order from database
            CoffeeOrder o = orderRepository.findById(id).orElse(null);
            if (o == null) {
                throw new IllegalArgumentException("Order ID is INVALID!");
            }
            
            // Brew coffee and update state
            o.setState(OrderState.BREWED);
            o.setBarista(barista);
            orderRepository.save(o);
            
            // Send completion notification
            Message<Long> message = MessageBuilder.withPayload(id).build();
            streamBridge.send(finishedOrdersBindingFromConfig, message);
        };
    }
}
```

**Key Features:**
- **Functional Programming Model**: Uses `Consumer<Long>` for clean message handling
- **Transaction Management**: `@Transactional` ensures atomicity
- **Dynamic Barista ID**: Generated with `${random.uuid}` for instance identification
- **StreamBridge**: Dynamic message sending without pre-defined channels

### 2. Simplified Entity Model

**File:** `model/CoffeeOrder.java`

```java
@Entity
@Table(name = "T_ORDER")
@Data
public class CoffeeOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String customer;
    private String waiter;
    private String barista;
    
    @Enumerated
    @Column(nullable = false)
    private OrderState state;
    
    @CreationTimestamp
    private Date createTime;
    
    @UpdateTimestamp
    private Date updateTime;
}
```

**Design Rationale:**
- ✅ **Minimal Fields**: Only what's needed for processing
- ✅ **State Tracking**: Records order processing state
- ✅ **Actor Identification**: Tracks waiter and barista
- ✅ **Audit Trail**: Automatic timestamps

## Monitoring

### Health Check

```bash
curl http://localhost:8070/actuator/health
```

### Application Info

```bash
curl http://localhost:8070/actuator/info
```

### Stream Bindings

```bash
curl http://localhost:8070/actuator/bindings
```

## Best Practices Demonstrated

1. **Consumer Group**: Enables horizontal scaling and fault tolerance
2. **Idempotency**: Handle duplicate messages gracefully
3. **Transaction Boundaries**: Clear transaction scope with `@Transactional`
4. **Exception Handling**: Throw exceptions for invalid messages
5. **Logging**: Comprehensive logging for debugging
6. **Configuration Externalization**: Use properties for binding names

## Scaling

### Running Multiple Instances

```bash
# Terminal 1 (Instance 1)
SERVER_PORT=8071 ./mvnw spring-boot:run

# Terminal 2 (Instance 2)
SERVER_PORT=8072 ./mvnw spring-boot:run
```

**Load Balancing:**
- Kafka automatically distributes messages across consumer group members
- Each order is processed by exactly one barista instance
- Provides fault tolerance and increased throughput

## Troubleshooting

### Messages Not Consumed

**Check:**
1. ✅ Kafka container is running: `docker ps | grep kafka`
2. ✅ Consumer group configured: Check `application.properties`
3. ✅ Topic exists: List topics in Kafka container
4. ✅ No exception logs: Check application logs

### Database Connection Failed

**Check:**
1. ✅ MariaDB is accessible: `docker ps | grep mariadb`
2. ✅ Database `springbucks` exists
3. ✅ Credentials match configuration

### Duplicate Processing

**Solution:**
- Implement idempotency check based on order ID
- Use database constraints to prevent duplicate updates
- Consider Kafka exactly-once semantics

## Extended Practice

**Suggested Enhancements:**

1. Add brewing time simulation (Thread.sleep or async processing)
2. Implement error handling with DLQ (Dead Letter Queue)
3. Add custom metrics for brewing count
4. Create brew priority queue
5. Implement skill-based routing (different baristas for different coffees)
6. Add integration tests with Testcontainers
7. Implement compensating transactions for failures

## References

- [Spring Cloud Stream Functional Model](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream.html#_functional_binding_names)
- [Kafka Consumer Groups](https://kafka.apache.org/documentation/#consumerconfigs)
- [StreamBridge Documentation](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream.html#_streambridge)

## License

MIT License - see [LICENSE](LICENSE) file for details.

## About Us

我們主要專注在敏捷專案管理、物聯網（IoT）應用開發和領域驅動設計（DDD）。喜歡把先進技術和實務經驗結合，打造好用又靈活的軟體解決方案。近來也積極結合 AI 技術，推動自動化工作流，讓開發與運維更有效率、更智慧。持續學習與分享，希望能一起推動軟體開發的創新和進步。

## Contact

**風清雲談** - 專注於敏捷專案管理、物聯網（IoT）應用開發和領域驅動設計（DDD）。

- 🌐 官方網站：[風清雲談部落格](https://blog.fengqing.tw/)
- 📘 Facebook：[風清雲談粉絲頁](https://www.facebook.com/profile.php?id=61576838896062)
- 💼 LinkedIn：[Chu Kuo-Lung](https://www.linkedin.com/in/chu-kuo-lung)
- 📺 YouTube：[雲談風清頻道](https://www.youtube.com/channel/UCXDqLTdCMiCJ1j8xGRfwEig)
- 📧 Email：[fengqing.tw@gmail.com](mailto:fengqing.tw@gmail.com)

---

**⭐ 如果這個專案對您有幫助，歡迎給個 Star！**
