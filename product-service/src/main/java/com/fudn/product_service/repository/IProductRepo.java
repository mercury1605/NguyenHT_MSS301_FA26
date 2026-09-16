package com.fudn.product_service.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fudn.product_service.model.Product;

public interface IProductRepo extends MongoRepository<Product, String> {

}
