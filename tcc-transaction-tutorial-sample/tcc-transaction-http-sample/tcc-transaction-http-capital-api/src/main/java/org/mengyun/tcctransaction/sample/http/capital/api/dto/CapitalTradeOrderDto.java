package org.mengyun.tcctransaction.sample.http.capital.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 转账 DTO
 * Created by changming.xie on 4/1/16.
 */
public class CapitalTradeOrderDto implements Serializable {

    private static final long serialVersionUID = 6627401903410124642L;

    /**
     * from 账号
     */
    private long selfUserId;

    /**
     * to 账号
     */
    private long oppositeUserId;

    private String orderTitle;

    /**
     * 订单编号
     */
    private String merchantOrderNo;

    /**
     * 金额
     *
     */
    private BigDecimal amount;

    public long getSelfUserId() {
        return selfUserId;
    }

    public void setSelfUserId(long selfUserId) {
        this.selfUserId = selfUserId;
    }

    public long getOppositeUserId() {
        return oppositeUserId;
    }

    public void setOppositeUserId(long oppositeUserId) {
        this.oppositeUserId = oppositeUserId;
    }

    public String getOrderTitle() {
        return orderTitle;
    }

    public void setOrderTitle(String orderTitle) {
        this.orderTitle = orderTitle;
    }

    public String getMerchantOrderNo() {
        return merchantOrderNo;
    }

    public void setMerchantOrderNo(String merchantOrderNo) {
        this.merchantOrderNo = merchantOrderNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
