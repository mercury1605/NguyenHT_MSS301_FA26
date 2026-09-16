package com.fudn.product_service.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fudn.product_service.dto.ProductRequest;
import com.fudn.product_service.dto.ProductResponse;
import com.fudn.product_service.model.Product;
import com.fudn.product_service.repository.IProductRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final IProductRepo productRepository;

    public ProductResponse createProduct(ProductRequest req) {
        Product product = Product.builder()
                .id(req.id())
                .name(req.name())
                .description(req.description())
                .price(req.price())
                .build();

        productRepository.save(product);
        log.info("Product {} saved..", product.getId());
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice());
    }

    public List<ProductResponse> getAllProducts() {
        var products = productRepository.findAll();
        return products.stream().map(product -> new ProductResponse(product.getId(), product.getName(),
                product.getDescription(), product.getPrice())).toList();
    }


    

}
