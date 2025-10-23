package tw.fengqing.spring.springbucks.barista.integration;

/**
 * Spring Cloud Stream 函數式編程模型配置
 * 使用函數式編程模型，通過 application.properties 配置綁定
 */
public interface Waiter {
    String NEW_ORDERS = "newOrders";
    String FINISHED_ORDERS = "finishedOrders";
}