package dev.hoangtuan.shopflow.chat;

import dev.hoangtuan.shopflow.catalog.ProductDto;
import java.util.List;

record ChatResponse(String reply, List<ProductDto.ListItem> products) {}
