package com.li.campuslife.controller;

import com.li.campuslife.dto.ItemOrderCreateDTO;
import com.li.campuslife.dto.Result;
import com.li.campuslife.service.IItemOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品订单接口。全部需要登录（不在 MvcConfig 白名单里）。
 */
@RestController
@RequestMapping("/item-order")
@RequiredArgsConstructor
public class ItemOrderController {

    private final IItemOrderService itemOrderService;

    @PostMapping
    public Result create(@RequestBody ItemOrderCreateDTO dto) {
        return itemOrderService.create(dto);
    }

    @GetMapping("/list")
    public Result list(@RequestParam(required = false, defaultValue = "1") Integer page,
                       @RequestParam(required = false, defaultValue = "10") Integer size) {
        return itemOrderService.listMine(page, size);
    }

    @GetMapping("/{orderNo}")
    public Result detail(@PathVariable("orderNo") String orderNo) {
        return itemOrderService.detail(orderNo);
    }

    @PostMapping("/{orderNo}/pay")
    public Result pay(@PathVariable("orderNo") String orderNo) {
        return itemOrderService.pay(orderNo);
    }

    @PostMapping("/{orderNo}/cancel")
    public Result cancel(@PathVariable("orderNo") String orderNo) {
        return itemOrderService.cancel(orderNo);
    }
}
