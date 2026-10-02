package com.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic-lock version. Hibernate turns every UPDATE into a
     * compare-and-swap (UPDATE ... WHERE id=? AND version=?), so two
     * concurrent stock mutations cannot silently overwrite each other —
     * the loser gets ObjectOptimisticLockingFailureException (mapped to 409).
     */
    @Version
    @Builder.Default
    private Long version = 0L;

    @Column(nullable = false, unique = true, length = 40)
    private String sku;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 80)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantityInStock = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer reorderLevel = 10;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
