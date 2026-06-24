package com.nimbleways.springboilerplate.controllers;

import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.entities.ProductType;
import com.nimbleways.springboilerplate.repositories.OrderRepository;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.services.implementations.NotificationService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@SpringBootTest
@AutoConfigureMockMvc
public class MyControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @AfterEach
    void cleanUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    public void processOrder_shouldReturnOkForMixedProductOrder() throws Exception {
        Set<Product> products = new HashSet<>();
        products.add(new Product(null, 15, 30, ProductType.NORMAL, "USB Cable", null, null, null));
        products.add(new Product(null, 10, 0, ProductType.NORMAL, "USB Dongle", null, null, null));
        products.add(new Product(null, 15, 30, ProductType.EXPIRABLE, "Butter", LocalDate.now().plusDays(26), null, null));
        products.add(new Product(null, 90, 6, ProductType.EXPIRABLE, "Milk", LocalDate.now().minusDays(2), null, null));
        products.add(new Product(null, 15, 30, ProductType.SEASONAL, "Watermelon", null,
                LocalDate.now().minusDays(2), LocalDate.now().plusDays(58)));
        products.add(new Product(null, 15, 30, ProductType.SEASONAL, "Grapes", null,
                LocalDate.now().plusDays(180), LocalDate.now().plusDays(240)));

        productRepository.saveAll(products);
        Order order = orderRepository.save(new Order(null, products));

        mockMvc.perform(post("/orders/{orderId}/processOrder", order.getId())
                .contentType("application/json"))
                .andExpect(status().isOk());

        assertEquals(order.getId(), orderRepository.findById(order.getId()).get().getId());
    }

    @Test
    public void processOrder_normalProductWithStock_shouldDecrementAvailable() throws Exception {
        Product product = productRepository.save(
                new Product(null, 15, 10, ProductType.NORMAL, "USB Cable", null, null, null));
        Order order = orderRepository.save(new Order(null, Set.of(product)));

        mockMvc.perform(post("/orders/{orderId}/processOrder", order.getId())
                .contentType("application/json"))
                .andExpect(status().isOk());

        assertEquals(9, productRepository.findById(product.getId()).get().getAvailable());
    }

    @Test
    public void processOrder_normalProductOutOfStock_shouldSendDelayNotification() throws Exception {
        Product product = productRepository.save(
                new Product(null, 15, 0, ProductType.NORMAL, "USB Dongle", null, null, null));
        Order order = orderRepository.save(new Order(null, Set.of(product)));

        mockMvc.perform(post("/orders/{orderId}/processOrder", order.getId())
                .contentType("application/json"))
                .andExpect(status().isOk());

        Mockito.verify(notificationService, Mockito.times(1)).sendDelayNotification(15, "USB Dongle");
    }

    @Test
    public void processOrder_expirableProductNotExpired_shouldDecrementAvailable() throws Exception {
        Product product = productRepository.save(
                new Product(null, 0, 10, ProductType.EXPIRABLE, "Butter", LocalDate.now().plusDays(5), null, null));
        Order order = orderRepository.save(new Order(null, Set.of(product)));

        mockMvc.perform(post("/orders/{orderId}/processOrder", order.getId())
                .contentType("application/json"))
                .andExpect(status().isOk());

        assertEquals(9, productRepository.findById(product.getId()).get().getAvailable());
    }

    @Test
    public void processOrder_expirableProductExpired_shouldSetAvailableToZeroAndNotify() throws Exception {
        LocalDate expiryDate = LocalDate.now().minusDays(1);
        Product product = productRepository.save(
                new Product(null, 0, 5, ProductType.EXPIRABLE, "Milk", expiryDate, null, null));
        Order order = orderRepository.save(new Order(null, Set.of(product)));

        mockMvc.perform(post("/orders/{orderId}/processOrder", order.getId())
                .contentType("application/json"))
                .andExpect(status().isOk());

        assertEquals(0, productRepository.findById(product.getId()).get().getAvailable());
        Mockito.verify(notificationService, Mockito.times(1)).sendExpirationNotification("Milk", expiryDate);
    }

    @Test
    public void processOrder_seasonalProductInSeason_shouldDecrementAvailable() throws Exception {
        Product product = productRepository.save(new Product(null, 5, 10, ProductType.SEASONAL, "Watermelon", null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));
        Order order = orderRepository.save(new Order(null, Set.of(product)));

        mockMvc.perform(post("/orders/{orderId}/processOrder", order.getId())
                .contentType("application/json"))
                .andExpect(status().isOk());

        assertEquals(9, productRepository.findById(product.getId()).get().getAvailable());
    }

    @Test
    public void processOrder_seasonalProductOutOfSeason_shouldSendOutOfStockNotification() throws Exception {
        Product product = productRepository.save(new Product(null, 5, 10, ProductType.SEASONAL, "Grapes", null,
                LocalDate.now().plusDays(180), LocalDate.now().plusDays(240)));
        Order order = orderRepository.save(new Order(null, Set.of(product)));

        mockMvc.perform(post("/orders/{orderId}/processOrder", order.getId())
                .contentType("application/json"))
                .andExpect(status().isOk());

        Mockito.verify(notificationService, Mockito.times(1)).sendOutOfStockNotification("Grapes");
    }
}
