package dev.hoangtuan.shopflow.model.chat;

import java.util.List;

public record ModelReply(String reply, List<Long> productIds) {}
