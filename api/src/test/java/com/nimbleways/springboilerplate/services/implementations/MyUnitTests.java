package com.nimbleways.springboilerplate.services.implementations;

import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.entities.ProductType;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.utils.Annotations.UnitTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

@ExtendWith(SpringExtension.class)
@UnitTest
public class MyUnitTests {

    @Mock
    private NotificationService notificationService;
    @Mock
    private ProductRepository productRepository;
    @InjectMocks
    private ProductService productService;

    // --- NORMAL product ---

    @Test
    public void normalProductWithStock_shouldDecrementAvailable() {
        Product product = new Product(null, 15, 10, ProductType.NORMAL, "USB Cable", null, null, null);
        Mockito.when(productRepository.save(product)).thenReturn(product);

        productService.processProduct(product);

        assertEquals(9, product.getAvailable());
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
        Mockito.verifyNoInteractions(notificationService);
    }

    @Test
    public void normalProductOutOfStockWithLeadTime_shouldNotifyDelay() {
        Product product = new Product(null, 15, 0, ProductType.NORMAL, "RJ45 Cable", null, null, null);
        Mockito.when(productRepository.save(product)).thenReturn(product);

        productService.processProduct(product);

        assertEquals(0, product.getAvailable());
        assertEquals(15, product.getLeadTime());
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
        Mockito.verify(notificationService, Mockito.times(1)).sendDelayNotification(15, "RJ45 Cable");
    }

    @Test
    public void normalProductOutOfStockWithNoLeadTime_shouldDoNothing() {
        Product product = new Product(null, 0, 0, ProductType.NORMAL, "USB Dongle", null, null, null);

        productService.processProduct(product);

        Mockito.verifyNoInteractions(notificationService);
        Mockito.verifyNoInteractions(productRepository);
    }

    // --- SEASONAL product ---

    @Test
    public void seasonalProductInSeasonWithStock_shouldDecrementAvailable() {
        Product product = new Product(null, 5, 10, ProductType.SEASONAL, "Watermelon", null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));
        Mockito.when(productRepository.save(product)).thenReturn(product);

        productService.processProduct(product);

        assertEquals(9, product.getAvailable());
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
        Mockito.verifyNoInteractions(notificationService);
    }

    @Test
    public void seasonalProductLeadTimeExceedsSeasonEnd_shouldSendOutOfStockAndSetAvailableToZero() {
        // in season but available=0, leadTime=10, season ends in 3 days → now+10 > seasonEnd
        Product product = new Product(null, 10, 0, ProductType.SEASONAL, "Watermelon", null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(3));
        Mockito.when(productRepository.save(product)).thenReturn(product);

        productService.processProduct(product);

        assertEquals(0, product.getAvailable());
        Mockito.verify(notificationService, Mockito.times(1)).sendOutOfStockNotification("Watermelon");
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
    }

    @Test
    public void seasonalProductSeasonNotYetStarted_shouldSendOutOfStockNotification() {
        Product product = new Product(null, 5, 0, ProductType.SEASONAL, "Grapes", null,
                LocalDate.now().plusDays(180), LocalDate.now().plusDays(240));
        Mockito.when(productRepository.save(product)).thenReturn(product);

        productService.processProduct(product);

        Mockito.verify(notificationService, Mockito.times(1)).sendOutOfStockNotification("Grapes");
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
    }

    @Test
    public void seasonalProductInSeasonOutOfStockWithLeadTimeWithinSeason_shouldNotifyDelay() {
        // in season, available=0, leadTime=5, season ends in 30 days → delay notification
        Product product = new Product(null, 5, 0, ProductType.SEASONAL, "Watermelon", null,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(30));
        Mockito.when(productRepository.save(product)).thenReturn(product);

        productService.processProduct(product);

        Mockito.verify(notificationService, Mockito.times(1)).sendDelayNotification(5, "Watermelon");
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
    }

    // --- EXPIRABLE product ---

    @Test
    public void expirableProductNotExpiredWithStock_shouldDecrementAvailable() {
        Product product = new Product(null, 0, 10, ProductType.EXPIRABLE, "Butter",
                LocalDate.now().plusDays(5), null, null);
        Mockito.when(productRepository.save(product)).thenReturn(product);

        productService.processProduct(product);

        assertEquals(9, product.getAvailable());
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
        Mockito.verifyNoInteractions(notificationService);
    }

    @Test
    public void expirableProductExpired_shouldSendExpirationNotificationAndSetAvailableToZero() {
        LocalDate expiryDate = LocalDate.now().minusDays(1);
        Product product = new Product(null, 0, 6, ProductType.EXPIRABLE, "Milk", expiryDate, null, null);
        Mockito.when(productRepository.save(product)).thenReturn(product);

        productService.processProduct(product);

        assertEquals(0, product.getAvailable());
        Mockito.verify(notificationService, Mockito.times(1)).sendExpirationNotification("Milk", expiryDate);
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
    }

    @Test
    public void expirableProductNotExpiredButOutOfStock_shouldSendExpirationNotification() {
        LocalDate expiryDate = LocalDate.now().plusDays(5);
        Product product = new Product(null, 0, 0, ProductType.EXPIRABLE, "Cheese", expiryDate, null, null);
        Mockito.when(productRepository.save(product)).thenReturn(product);

        productService.processProduct(product);

        assertEquals(0, product.getAvailable());
        Mockito.verify(notificationService, Mockito.times(1)).sendExpirationNotification("Cheese", expiryDate);
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
    }

    // --- notifyDelay (legacy, kept for backwards compatibility) ---

    @Test
    public void notifyDelay_shouldSaveProductAndSendDelayNotification() {
        Product product = new Product(null, 15, 0, ProductType.NORMAL, "RJ45 Cable", null, null, null);
        Mockito.when(productRepository.save(product)).thenReturn(product);

        productService.notifyDelay(product.getLeadTime(), product);

        assertEquals(0, product.getAvailable());
        assertEquals(15, product.getLeadTime());
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
        Mockito.verify(notificationService, Mockito.times(1))
                .sendDelayNotification(product.getLeadTime(), product.getName());
    }
}
