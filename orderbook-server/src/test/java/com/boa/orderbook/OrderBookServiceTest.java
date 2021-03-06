package com.boa.orderbook;

import com.boa.orderbook.client.OrderBookClientHandle;
import org.junit.Before;
import org.junit.Test;

import java.rmi.RemoteException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class OrderBookServiceTest {

    private static final String SELLER1 = "seller1";
    private static final String SELLER2 = "seller2";
    private static final String BUYER1 = "buyer1";
    private static final String BUYER2 = "buyer2";

    private static final String SECURITY = "ABC";
    private OrderBookClientHandle clientHandler;
    private PriorityOrderBook book;

    @Before
    public final void before() {
        clientHandler = mock(OrderBookClientHandle.class);
        book = new PriorityOrderBook();
    }

    /**
     * Two identical sales are queued while a valid buyer arrives.
     * Expected: The first sale order placed should be favored (via timestamp).
     */
    @Test
    public void testTwoIdenticalSales() throws RemoteException {
        Order saleOrder1 = new Order(SELLER1, SECURITY, 1, 10.0,
                "SELL", System.currentTimeMillis(), clientHandler);
        Order saleOrder2 = new Order(SELLER2, SECURITY, 1, 10.0,
                "SELL", System.currentTimeMillis(), clientHandler);

        book.sell(saleOrder1);
        book.sell(saleOrder2);

        Order buyOrder = new Order(BUYER1, SECURITY, 1, 10.0,
                "BUY", System.currentTimeMillis(), clientHandler);
        book.buy(buyOrder);

        List<Order> remainingOrders = book.getAllOrders();
        //Only one order should be in the book
        assertEquals(1, remainingOrders.size());
        //and it should be order2 (because we favored the first one).
        assertEquals(saleOrder2, remainingOrders.iterator().next());

    }

    /**
     * Two identical purchase orders are queued while a valid seller arrives.
     * Expected: The first buy order placed should be favored (via time stamp).
     */
    @Test
    public void testTwoIdenticalPurchases() throws RemoteException {
        Order buyOrder1 = new Order(BUYER1, SECURITY, 1, 10.0,
                "BUY", System.currentTimeMillis(), clientHandler);
        Order buyOrder2 = new Order(BUYER2, SECURITY, 1, 10.0,
                "BUY", System.currentTimeMillis(), clientHandler);

        book.buy(buyOrder1);
        book.buy(buyOrder2);

        Order sellOrder = new Order(SELLER1, SECURITY, 1, 10.0,
                "SELL", System.currentTimeMillis(), clientHandler);
        book.sell(sellOrder);
        List<Order> remainingOrders = book.getAllOrders();
        //Only one order should be in the book
        assertEquals(remainingOrders.size(), 1);
        //and it should be order2 (because we favored the first one).
        assertEquals(remainingOrders.iterator().next(), buyOrder2);
    }

    /**
     * A buyer places an order for more than the highest offer queued.
     * Expected: The transaction should occur at the highest offer
     * (i.e less than what the buyer actually placed.)
     */
    @Test
    public void testBuyerPaysLessThanExpected() throws RemoteException {
        //BUYS and pays less than expected (20.10)
        //		SIDE=SELL SECURITY=ABC QTY=1 PRICE=20.10  AT="2020-03-08 11:36:00"
        //		SIDE=BUY SECURITY=ABC QTY=1 PRICE=40  AT="2020-03-08 11:36:01"
        Order sellOrder = new Order(SELLER1, SECURITY, 1, 20.10,
                "SELL", System.currentTimeMillis(), clientHandler);

        assertEquals(new Double(0.0), book.sell(sellOrder));

        Order buyOrder = new Order(BUYER1, SECURITY, 1, 40.0,
                "BUY", System.currentTimeMillis(), clientHandler);

        Double transactionValue = book.buy(buyOrder);
        assertEquals(new Double(20.10), transactionValue);
    }

    /**
     * A seller places an order for less than the lowest bid queued.
     * Expected: The transaction should occur for the HIGHEST bidder
     * at the sellers price (LOWEST)
     */
    @Test
    public void testSellerGetsMoreThanExpected() throws RemoteException {
        //Sells and gets more than expected (20.21)
        //		SIDE=BUY SECURITY=ABC QTY=1 PRICE=20.21  AT="2020-03-08 11:36:00"
        //		SIDE=SELL SECURITY=ABC QTY=1 PRICE=20.10  AT="2020-03-08 11:36:01"
        Order buyOrder = new Order(BUYER1, SECURITY, 1, 20.21,
                "BUY", System.currentTimeMillis(), clientHandler);

        assertEquals(new Double(0.0), book.buy(buyOrder));

        Order sellOrder = new Order(SELLER1, SECURITY, 1, 20.10,
                "SELL", System.currentTimeMillis(), clientHandler);

        Double transactionValue = book.sell(sellOrder);
        assertEquals(new Double(20.21), transactionValue);
    }

    /**
     * A sell order arrives for less units than a queued applicable buy order.
     * Expected: We partially fulfill the order thus satisfying the buyer
     * and partially completing the sellers order.
     */
    @Test
    public void partialSale() throws RemoteException {
        Order buyOrder = new Order(BUYER1, SECURITY, 2, 10.0,
                "BUY", System.currentTimeMillis(), clientHandler);

        book.buy(buyOrder);

        Order sellOrder = new Order(SELLER1, SECURITY, 1, 10.0,
                "SELL", System.currentTimeMillis(), clientHandler);
        book.sell(sellOrder);

        List<Order> remainingOrders = book.getAllOrders();
        //The buy order remains
        assertEquals(1, remainingOrders.size());

        Order remainingOrder = remainingOrders.iterator().next();
        // The same one we have.
        assertEquals(buyOrder, remainingOrder);

        //but with less units
        assertEquals(new Integer(1), remainingOrder.getOrderQty());
    }

    /**
     * A buy order arrives for less units than a queued applicable sale order.
     * Expected: We partially fulfill the order thus satisfying the buyer
     * and partially completing the sellers order.
     */
    @Test
    public void partialBuy() throws RemoteException {
        Order sellOrder = new Order(SELLER1, SECURITY, 2, 9.0,
                "SELL", System.currentTimeMillis(), clientHandler);

        book.sell(sellOrder);

        Order buyOrder = new Order(BUYER1, SECURITY, 1, 10.0,
                "BUY", System.currentTimeMillis(), clientHandler);
        book.buy(buyOrder);
        List<Order> remainingOrders = book.getAllOrders();
        //The sale order remains
        assertEquals(1, remainingOrders.size());

        Order remainingOrder = remainingOrders.iterator().next();
        // The same one we have.
        assertEquals(sellOrder, remainingOrder);

        //but with less units
        assertEquals(new Integer(1), remainingOrder.getOrderQty());
    }

    /**
     * Users aren't allowed to make transactions with themselves.
     * They could mess up the market!
     */
    @Test(expected = IllegalArgumentException.class)
    public void cantBuyToYourself() throws RemoteException {
        Order sellOrder = new Order(SELLER1, SECURITY, 1, 10.0,
                "SELL", System.currentTimeMillis(), clientHandler);
        book.sell(sellOrder);

        Order buyOrder = new Order(SELLER1, SECURITY, 1, 10.0,
                "BUY", System.currentTimeMillis(), clientHandler);
        book.buy(buyOrder);
    }

    /**
     * Users aren't allowed to make transactions with themselves.
     * They could mess up the market!
     */
    @Test(expected = IllegalArgumentException.class)
    public void cantSellToYourself() throws RemoteException {
        Order buyOrder = new Order(SELLER1, SECURITY, 1, 10.0,
                "BUY", System.currentTimeMillis(), clientHandler);
        book.buy(buyOrder);

        Order sellOrder = new Order(SELLER1, SECURITY, 1, 9.0,
                "SELL", System.currentTimeMillis(), clientHandler);
        book.sell(sellOrder);
    }

    /**
     * Testing that the SellSideComparator sorts from less value to great value.
     * (i.e Natural Ordering)
     */
    @Test
    public void sellingComparator() throws RemoteException {
        //The "cheapest" sell order in value is the BEST candidate to get
        //a buyer, put it first in the pq.
        PriorityOrderBook.SellSideComparator comp = new PriorityOrderBook.SellSideComparator();

        Order one = new Order(SELLER1, SECURITY, 1, 9.0,
                "SELL", 1, clientHandler);

        Order two = new Order(SELLER1, SECURITY, 1, 10.0,
                "SELL", 1, clientHandler);

        int lessThan = comp.compare(one, two);

        //same time, lowest value goes first in the pq
        assertEquals(true, new Boolean(lessThan < 0));

        one = new Order(SELLER1, SECURITY, 1, 10.0,
                "SELL", 1, clientHandler);

        two = new Order(SELLER2, SECURITY, 1, 10.0,
                "SELL", 2, clientHandler);

        int equalButTimeWins = comp.compare(one, two);
        //same value, earliest to arrive goes first in the pq
        assertEquals(true, new Boolean(equalButTimeWins < 0));


        one = new Order(SELLER1, SECURITY, 1, 10.0,
                "SELL", 1, clientHandler);

        two = new Order(SELLER1, SECURITY, 1, 9.0,
                "SELL", 1, clientHandler);

        int greaterThan = comp.compare(one, two);
        //same time, lowest value wins
        assertEquals(true, new Boolean(greaterThan > 0));
    }

    /**
     * Testing that the BuySideComparator sorts from greater value to less value.
     */
    @Test
    public void buyingComparator() throws RemoteException {
        PriorityOrderBook.BuySideComparator comp = new PriorityOrderBook.BuySideComparator();
        Order one = new Order(BUYER1, SECURITY, 1, 10.0,
                "BUY", 1, clientHandler);

        Order two = new Order(BUYER2, SECURITY, 1, 10.0,
                "BUY", 2, clientHandler);

        int equalButTimeWins = comp.compare(one, two);

        assertEquals(true, new Boolean(equalButTimeWins < 0));


        one = new Order(BUYER1, SECURITY, 1, 10.0,
                "BUY", 1, clientHandler);

        two = new Order(BUYER2, SECURITY, 1, 9.0,
                "BUY", 1, clientHandler);

        int greaterThan = comp.compare(one, two);
        //same time, highest value wins
        assertEquals(true, new Boolean(greaterThan < 0));


        one = new Order(BUYER1, SECURITY, 1, 9.0,
                "BUY", 1, clientHandler);

        two = new Order(BUYER2, SECURITY, 1, 10.0,
                "BUY", 1, clientHandler);

        int lessThan = comp.compare(one, two);
        //same time, highest value wins
        assertEquals(true, new Boolean(lessThan > 0));

    }

    /**
     * A sell order arrives for less units than several queued applicable buy orders.
     * Expected: We partially fulfill the order thus satisfying the buyer
     * and partially completing the sellers order.
     */
    @Test
    public void advancedPartialSell() throws RemoteException {
        //		SECURITY=ABC QTY=500 PRICE=430.0 SIDE=BUY
        //		SECURITY=ABC QTY=1000 PRICE=435.5 SIDE=BUY
        //		SECURITY=ABC QTY=1200 PRICE=429.0 SIDE=SELL

        Order one = new Order(BUYER1, SECURITY, 500, 430.0,
                "BUY", 1, clientHandler);

        book.buy(one);

        Order two = new Order(BUYER2, SECURITY, 1000, 435.5,
                "BUY", 2, clientHandler);
        book.buy(two);

        Order three = new Order(SELLER1, SECURITY, 1200, 429.0,
                "SELL", 3, clientHandler);

        Double transactionValue = book.sell(three);

        List<Order> remainingOrders = book.getAllOrders();
        //Only one order should be in the book
        assertEquals(1, remainingOrders.size());
        //it should be order1 because we satisfied order2 first
        Order remainingOrder = remainingOrders.iterator().next();
        assertEquals(one, remainingOrder);
        //and it still has 300 units to place.
        assertEquals(new Integer(300), remainingOrder.getOrderQty());
        //the transaction ran at this value, considering the units traded.
        assertEquals(new Double(435.5 * 1000 + 430.0 * 200), transactionValue);
    }

}
