package dev.hoangtuan.shopflow.dto.chat;

import dev.hoangtuan.shopflow.dto.catalog.ProductDto;
import java.util.List;

public record ChatResponse(String reply, List<ProductDto.ListItem> products) {}
