package com.fudn.order_service.dto;


import java.math.BigDecimal;

public record OrderRequest(String skuCode, BigDecimal price, Integer quantity) {
}