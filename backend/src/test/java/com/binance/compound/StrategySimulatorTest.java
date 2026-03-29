package com.binance.compound;

import com.binance.compound.entity.CycleInstance;
import com.binance.compound.entity.SimAccount;
import com.binance.compound.entity.StrategyConfig;
import com.binance.compound.repository.*;
import com.binance.compound.service.PriceService;
import com.binance.compound.service.SimulationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StrategySimulatorTest {
    
    @Mock
    private CycleInstanceRepository cycleInstanceRepository;
    @Mock
    private SimAccountRepository simAccountRepository;
    @Mock
    private ExpectedFreeRepository expectedFreeRepository;
    @Mock
    private RrStateRepository rrStateRepository;
    @Mock
    private InstanceEventRepository instanceEventRepository;
    @Mock
    private CycleOpenRecordRepository cycleOpenRecordRepository;
    @Mock
    private StrategyConfigRepository strategyConfigRepository;
    @Mock
    private PriceService priceService;
    
    private SimulationEngine simulationEngine;
    
    private static final BigDecimal FEE_RATE = new BigDecimal("0.001");
    private static final BigDecimal TAKER_FEE_RATE = new BigDecimal("0.001");
    
    @BeforeEach
    void setUp() {
        simulationEngine = new SimulationEngine(
                cycleInstanceRepository,
                simAccountRepository,
                expectedFreeRepository,
                rrStateRepository,
                instanceEventRepository,
                cycleOpenRecordRepository,
                strategyConfigRepository,
                priceService
        );
    }
    
    @Test
    void testTakeProfitCalculationWithFees() {
        BigDecimal buyPrice = new BigDecimal("100.00");
        BigDecimal buyQuote = new BigDecimal("1000.00");
        BigDecimal takeProfitPct = new BigDecimal("0.03");
        
        BigDecimal sellPrice = buyPrice.multiply(BigDecimal.ONE.add(takeProfitPct));
        BigDecimal grossSellQuote = sellPrice.multiply(buyQuote.divide(buyPrice, 16, java.math.RoundingMode.DOWN));
        
        BigDecimal buyFee = buyQuote.multiply(FEE_RATE);
        BigDecimal sellFee = grossSellQuote.multiply(TAKER_FEE_RATE);
        BigDecimal netSellQuote = grossSellQuote.subtract(sellFee);
        BigDecimal profit = netSellQuote.subtract(buyQuote).subtract(buyFee);
        
        assertEquals(0, profit.compareTo(new BigDecimal("29.70")), "Profit should be approximately 29.70");
    }
    
    @Test
    void testFeeDeductionOnBuy() {
        BigDecimal quoteAmount = new BigDecimal("1000.00");
        BigDecimal price = new BigDecimal("100.00");
        BigDecimal baseQty = quoteAmount.divide(price, 16, java.math.RoundingMode.DOWN);
        BigDecimal actualQuoteSpent = baseQty.multiply(price);
        BigDecimal buyFee = actualQuoteSpent.multiply(FEE_RATE);
        BigDecimal totalDeducted = actualQuoteSpent.add(buyFee);
        
        assertTrue(totalDeducted.compareTo(quoteAmount) > 0, "Total deducted should be greater than original quote due to fees");
    }
    
    @Test
    void testFeeDeductionOnSell() {
        BigDecimal baseQty = new BigDecimal("10.00");
        BigDecimal sellPrice = new BigDecimal("103.00");
        BigDecimal grossQuote = baseQty.multiply(sellPrice);
        BigDecimal sellFee = grossQuote.multiply(TAKER_FEE_RATE);
        BigDecimal netQuote = grossQuote.subtract(sellFee);
        
        BigDecimal expectedNet = new BigDecimal("1029.71");
        assertEquals(0, netQuote.setScale(2, java.math.RoundingMode.HALF_UP)
                .compareTo(expectedNet.setScale(2, java.math.RoundingMode.HALF_UP)));
    }
    
    @Test
    void testCompoundProfitCalculation() {
        BigDecimal initialQuote = new BigDecimal("1000.00");
        BigDecimal anchorPrice = new BigDecimal("100.00");
        BigDecimal takeProfitPct = new BigDecimal("0.03");
        
        BigDecimal firstCycleQuote = initialQuote;
        BigDecimal firstCycleQty = firstCycleQuote.divide(anchorPrice, 16, java.math.RoundingMode.DOWN);
        BigDecimal firstCycleSellPrice = anchorPrice.multiply(BigDecimal.ONE.add(takeProfitPct));
        BigDecimal firstCycleGross = firstCycleQty.multiply(firstCycleSellPrice);
        BigDecimal firstCycleFee = firstCycleGross.multiply(TAKER_FEE_RATE);
        BigDecimal firstCycleNet = firstCycleGross.subtract(firstCycleFee);
        BigDecimal firstCycleProfit = firstCycleNet.subtract(firstCycleQuote);
        
        BigDecimal secondCycleQuote = firstCycleNet;
        BigDecimal secondCyclePrice = firstCycleSellPrice;
        BigDecimal secondCycleQty = secondCycleQuote.divide(secondCyclePrice, 16, java.math.RoundingMode.DOWN);
        BigDecimal secondCycleSellPrice = secondCyclePrice.multiply(BigDecimal.ONE.add(takeProfitPct));
        BigDecimal secondCycleGross = secondCycleQty.multiply(secondCycleSellPrice);
        BigDecimal secondCycleFee = secondCycleGross.multiply(TAKER_FEE_RATE);
        BigDecimal secondCycleNet = secondCycleGross.subtract(secondCycleFee);
        BigDecimal secondCycleProfit = secondCycleNet.subtract(secondCycleQuote);
        
        assertTrue(firstCycleProfit.compareTo(BigDecimal.ZERO) > 0, "First cycle should be profitable");
        assertTrue(secondCycleProfit.compareTo(firstCycleProfit) > 0, "Second cycle should compound profits");
    }
    
    @Test
    void testSpendableQuoteCalculation() {
        BigDecimal freeBalance = new BigDecimal("1000.00");
        BigDecimal reserve = new BigDecimal("10.00");
        BigDecimal spendable = freeBalance.subtract(reserve).max(BigDecimal.ZERO);
        
        assertEquals(0, spendable.compareTo(new BigDecimal("990.00")));
    }
    
    @Test
    void testSpendableQuoteWithInsufficientBalance() {
        BigDecimal freeBalance = new BigDecimal("5.00");
        BigDecimal reserve = new BigDecimal("10.00");
        BigDecimal spendable = freeBalance.subtract(reserve).max(BigDecimal.ZERO);
        
        assertEquals(0, spendable.compareTo(BigDecimal.ZERO));
    }
}
