package org.mengyun.tcctransaction.sample.http.order.service;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mengyun.tcctransaction.CancellingException;
import org.mengyun.tcctransaction.ConfirmingException;
import org.mengyun.tcctransaction.sample.order.domain.entity.Order;
import org.mengyun.tcctransaction.sample.order.domain.entity.Shop;
import org.mengyun.tcctransaction.sample.order.domain.repository.ShopRepository;
import org.mengyun.tcctransaction.sample.order.domain.service.OrderServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Created by changming.xie on 4/1/16.
 */
@Service
public class PlaceOrderServiceImpl {

    @Autowired
    ShopRepository shopRepository;

    @Autowired
    OrderServiceImpl orderService;

    @Autowired
    PaymentServiceImpl paymentService;
    final static private Log logger = LogFactory.getLog(PlaceOrderServiceImpl.class);

    public String placeOrder(long payerUserId, long shopId, List<Pair<Long, Integer>> productQuantities,
        BigDecimal redPacketPayAmount) {
        Shop shop = shopRepository.findById(shopId);

        Order order = orderService.createOrder(payerUserId, shop.getOwnerUserId(), productQuantities);
        logger.info("完成创建订单:" + JSON.toJSONString(order, true));
        Boolean result = false;

        try {
            String orderNo = order.getMerchantOrderNo();
            logger.info("开始支付");
            paymentService.makePayment(orderNo, order, redPacketPayAmount,
                order.getTotalAmount().subtract(redPacketPayAmount));

        } catch (ConfirmingException confirmingException) {
            // exception throws with the tcc transaction status is CONFIRMING,
            // when tcc transaction is confirming status,
            // the tcc transaction recovery will try to confirm the whole transaction to ensure eventually consistent.

            result = true;
        } catch (CancellingException cancellingException) {
            // exception throws with the tcc transaction status is CANCELLING,
            // when tcc transaction is under CANCELLING status,
            // the tcc transaction recovery will try to cancel the whole transaction to ensure eventually consistent.
        } catch (Throwable e) {
            // other exceptions throws at TRYING stage.
            // you can retry or cancel the operation.
            e.printStackTrace();
        }

        return order.getMerchantOrderNo();
    }
}
