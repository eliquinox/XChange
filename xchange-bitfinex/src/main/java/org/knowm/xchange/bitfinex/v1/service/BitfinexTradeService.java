package org.knowm.xchange.bitfinex.v1.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitfinex.v1.BitfinexAdapters;
import org.knowm.xchange.bitfinex.v1.BitfinexOrderType;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexOrderFlags;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexOrderStatusResponse;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexTradeResponse;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.*;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.utils.DateUtils;

public class BitfinexTradeService extends BitfinexTradeServiceRaw implements TradeService {

  private static final OpenOrders noOpenOrders = new OpenOrders(new ArrayList<LimitOrder>());

  public BitfinexTradeService(Exchange exchange) {

    super(exchange);
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return getOpenOrders(createOpenOrdersParams());
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    BitfinexOrderStatusResponse[] activeOrders = getBitfinexOpenOrders();

    if (activeOrders.length <= 0) {
      return noOpenOrders;
    } else {
      return BitfinexAdapters.adaptOrders(activeOrders);
    }
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {

    BitfinexOrderStatusResponse newOrder = placeBitfinexMarketOrder(marketOrder, BitfinexOrderType.MARKET);

    return String.valueOf(newOrder.getId());
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {

    BitfinexOrderStatusResponse newOrder;
    if (limitOrder.hasFlag(BitfinexOrderFlags.FILL_OR_KILL)) {
      newOrder = placeBitfinexLimitOrder(limitOrder, BitfinexOrderType.FILL_OR_KILL);
    } else {
      newOrder = placeBitfinexLimitOrder(limitOrder, BitfinexOrderType.LIMIT);
    }

    return String.valueOf(newOrder.getId());
  }

  @Override
  public boolean cancelOrder(String orderId) throws IOException {

    return cancelBitfinexOrder(orderId);
  }

  /**
   * @param params Implementation of {@link TradeHistoryParamCurrencyPair} is mandatory. Can optionally implement {@link TradeHistoryParamPaging} and
   *        {@link TradeHistoryParamsTimeSpan#getStartTime()}. All other TradeHistoryParams types will be ignored.
   */
  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {

    final String symbol;
    if (params instanceof TradeHistoryParamCurrencyPair && ((TradeHistoryParamCurrencyPair) params).getCurrencyPair() != null) {
      symbol = BitfinexAdapters.adaptCurrencyPair(((TradeHistoryParamCurrencyPair) params).getCurrencyPair());
    } else {
      // Exchange will return the errors below if CurrencyPair is not provided.
      // field not on request: "Key symbol was not present."
      // field supplied but blank: "Key symbol may not be the empty string"
      throw new ExchangeException("CurrencyPair must be supplied");
    }

    final long timestamp;
    if (params instanceof TradeHistoryParamsTimeSpan) {
      Date startTime = ((TradeHistoryParamsTimeSpan) params).getStartTime();
      timestamp = DateUtils.toUnixTime(startTime);
    } else {
      timestamp = 0;
    }

    final int limit;
    if (params instanceof TradeHistoryParamPaging) {
      TradeHistoryParamPaging pagingParams = (TradeHistoryParamPaging) params;
      Integer pageLength = pagingParams.getPageLength();
      Integer pageNum = pagingParams.getPageNumber();
      limit = (pageLength != null && pageNum != null) ? pageLength * (pageNum + 1) : 50;
    } else {
      limit = 50;
    }

    final BitfinexTradeResponse[] trades = getBitfinexTradeHistory(symbol, timestamp, limit);
    return BitfinexAdapters.adaptTradeHistory(trades, symbol);
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {

    return new BitfinexTradeHistoryParams(new Date(0), 50, CurrencyPair.BTC_USD);
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {
    return null;
  }

  public static class BitfinexTradeHistoryParams extends DefaultTradeHistoryParamsTimeSpan
      implements TradeHistoryParamCurrencyPair, TradeHistoryParamPaging {

    private int count;
    private CurrencyPair pair;
    private Integer pageNumber;

    public BitfinexTradeHistoryParams(Date startTime, int count, CurrencyPair pair) {

      super(startTime);

      this.count = count;
      this.pair = pair;
    }

    @Override
    public void setPageLength(Integer count) {

      this.count = count;
    }

    @Override
    public Integer getPageLength() {

      return count;
    }

    @Override
    public void setPageNumber(Integer pageNumber) {

      this.pageNumber = pageNumber;
    }

    @Override
    public Integer getPageNumber() {

      return pageNumber;
    }

    @Override
    public CurrencyPair getCurrencyPair() {

      return pair;
    }

    @Override
    public void setCurrencyPair(CurrencyPair pair) {

      this.pair = pair;
    }
  }

  @Override
  public Collection<Order> getOrder(String... orderIds)
      throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    throw new NotYetImplementedForExchangeException();
  }

  public BigDecimal getMakerFee() throws IOException {
    return getBitfinexAccountInfos()[0].getMakerFees();
  }

  public BigDecimal getTakerFee() throws IOException {
    return getBitfinexAccountInfos()[0].getTakerFees();
  }
}
