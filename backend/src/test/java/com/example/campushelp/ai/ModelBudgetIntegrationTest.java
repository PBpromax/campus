package com.example.campushelp.ai;

import com.example.campushelp.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"model-controls.user-calls-per-day=3","model-controls.global-calls-per-day=5"}) @ActiveProfiles("test")
class ModelBudgetIntegrationTest {
    @Autowired ModelBudgets budgets;@Autowired ModelBudgetTransactions transactions;@Autowired ModelControls controls;
    @Autowired ModelBudgetDayRepository days;@Autowired ModelInvocationRepository invocations;@Autowired ModelCosts costs;
    @BeforeEach void setup() {
        invocations.deleteAll();days.deleteAll();controls.setUserCallsPerDay(3);controls.setGlobalCallsPerDay(5);
        controls.setUserTokensPerDay(2000000);controls.setGlobalTokensPerDay(10000000);controls.setUserUsdPerDay(new BigDecimal("100"));controls.setGlobalUsdPerDay(new BigDecimal("100"));
    }
    @Test void successfulAndFailedRequestsBothOccupyDurableQuotaWithoutDoubleFinishing() {
        String one=budgets.reserve(1L,"deepseek-flash");budgets.finish(one,"SUCCEEDED",null,new DeepSeekClient.Usage(100,10,110,0L,100L));
        budgets.finish(one,"FAILED","NETWORK",null);assertEquals("SUCCEEDED",invocations.findById(one).orElseThrow().getState());
        String two=budgets.reserve(1L,"deepseek-flash");budgets.finish(two,"FAILED","TIMEOUT",null);budgets.reserve(1L,"deepseek-flash");
        assertEquals(429,assertThrows(BusinessException.class,()->budgets.reserve(1L,"deepseek-flash")).getCode());
        assertEquals(3,budgets.own(1L).calls());assertNull(invocations.findById(two).orElseThrow().getEstimatedUsd());
        var newFacade=new ModelBudgets(transactions,days,invocations,controls);assertEquals(3,newFacade.own(1L).calls());
        assertEquals(429,assertThrows(BusinessException.class,()->newFacade.reserve(1L,"deepseek-flash")).getCode());
    }
    @Test void concurrentFirstUseAndGlobalQuotaHaveOnlyFiveWinners() throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var start=new CountDownLatch(1);var futures=new ArrayList<Future<Boolean>>();
            for(long i=10;i<18;i++) {long owner=i;futures.add(pool.submit(()->{start.await();try {budgets.reserve(owner,"deepseek-flash");return true;}catch(BusinessException e) {assertEquals(429,e.getCode());return false;}}));}
            start.countDown();int successes=0;for(var result:futures) if(result.get(10,TimeUnit.SECONDS)) successes++;
            assertEquals(5,successes);assertEquals(5,invocations.count());
        }
    }
    @Test void tokenAndMoneyLimitsRejectBeforeCreatingAnInvocation() {
        controls.setUserTokensPerDay(ModelControls.RESERVED_TOKENS-1);
        assertEquals(429,assertThrows(BusinessException.class,()->budgets.reserve(1L,"deepseek-flash")).getCode());assertEquals(0,invocations.count());
        controls.setUserTokensPerDay(2000000);controls.setUserUsdPerDay(costs.reservation().subtract(new BigDecimal("0.00000001")));
        assertEquals(429,assertThrows(BusinessException.class,()->budgets.reserve(1L,"deepseek-flash")).getCode());assertEquals(0,invocations.count());
        controls.setUserUsdPerDay(new BigDecimal("100"));controls.setGlobalUsdPerDay(costs.reservation());
        budgets.reserve(1L,"deepseek-flash");assertEquals(429,assertThrows(BusinessException.class,()->budgets.reserve(2L,"deepseek-flash")).getCode());
    }
    @Test void unknownUsageAndRestartedReservationsAreNeverConsideredFree() {
        String id=budgets.reserve(1L,"deepseek-flash");var call=invocations.findById(id).orElseThrow();assertEquals("RESERVED",call.getState());assertNull(call.getEstimatedUsd());
        budgets.finish(id,"CANCELLED","CLIENT_CANCELLED",null);assertTrue(budgets.own(1L).reservedUsd().signum()>0);assertNull(invocations.findById(id).orElseThrow().getEstimatedUsd());
    }
    @Test void nextShanghaiDayHasASeparateBudgetAndPricingCannotSilentlyChangeModel() {
        LocalDate day=LocalDate.now(ZoneId.of("Asia/Shanghai"));transactions.ensureDay(day);transactions.ensureDay(day.plusDays(1));
        for(int i=0;i<3;i++) transactions.reserve(1L,day,"deepseek-flash");
        assertThrows(BusinessException.class,()->transactions.reserve(1L,day,"deepseek-flash"));assertNotNull(transactions.reserve(1L,day.plusDays(1),"deepseek-flash"));
        assertEquals(503,assertThrows(BusinessException.class,()->budgets.reserve(1L,"another-model")).getCode());
    }
    @Test void cacheAwareEstimatesAndUnknownCacheUpperEstimateAreExplicit() {
        var known=costs.estimate(new DeepSeekClient.Usage(1000000,1000000,2000000,1000000L,0L));
        assertEquals(0,new BigDecimal("1.20600000").compareTo(known.usd()));
        var fallback=costs.estimate(new DeepSeekClient.Usage(1000000,1000000,2000000,null,null));
        assertEquals(0,new BigDecimal("1.50000000").compareTo(fallback.usd()));assertEquals("PEAK_MISS_UPPER_ESTIMATE",fallback.basis());assertNull(costs.estimate(null).usd());
    }
}
