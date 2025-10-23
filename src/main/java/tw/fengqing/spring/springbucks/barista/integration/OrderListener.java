package tw.fengqing.spring.springbucks.barista.integration;

import tw.fengqing.spring.springbucks.barista.model.CoffeeOrder;
import tw.fengqing.spring.springbucks.barista.model.OrderState;
import tw.fengqing.spring.springbucks.barista.repository.CoffeeOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

/**
 * 訂單監聽器 - 處理新訂單並製作咖啡
 * 使用 Spring Cloud Stream 函數式編程模型
 */
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
     * 處理新訂單的函數式 Bean
     * 接收新訂單 ID，製作咖啡並發送完成消息
     * @return 訂單處理函數
     */
    @Bean
    public Consumer<Long> newOrders() {
        return id -> {
            CoffeeOrder o = orderRepository.findById(id).orElse(null);
            if (o == null) {
                log.warn("Order id {} is NOT valid.", id);
                throw new IllegalArgumentException("Order ID is INVALID!");
            }
            log.info("Receive a new Order {}. Waiter: {}. Customer: {}",
                    id, o.getWaiter(), o.getCustomer());
            
            // 設置為製作完成狀態
            o.setState(OrderState.BREWED);
            o.setBarista(barista);
            orderRepository.save(o);
            log.info("Order {} is READY.", id);
            Message<Long> message = MessageBuilder.withPayload(id).build();
            // 使用StreamBridge發送完成訂單消息
            streamBridge.send(finishedOrdersBindingFromConfig, message);
        };
    }
}