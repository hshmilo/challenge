package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.TransferException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class TransferServiceTest {
    @Autowired
    private AccountsService accountsService;

    @Autowired
    private TransferService transferService;

    @MockBean
    private NotificationService notificationService;

    @BeforeEach
    void clearAccounts() {
        // Reset the existing accounts before each test.
        accountsService.getAccountsRepository().clearAccounts();
    }

    @ParameterizedTest
    @CsvSource({"1000,1000,1000", "1000,0,1000", "1000,1000,1"})
    void testTransfer_AllowedParams(BigDecimal fromAccountBalance, BigDecimal toAccountBalance, BigDecimal amount) {

        // Create accounts
        Account from = new Account("Id-123");
        from.setBalance(fromAccountBalance);
        accountsService.createAccount(from);

        Account to = new Account("Id-456");
        to.setBalance(toAccountBalance);
        accountsService.createAccount(to);

        // Transfer the amount
        transferService.transfer(from, to, amount);

        // Check the balances
        assertEquals(from.getBalance(), fromAccountBalance.subtract(amount));
        assertEquals(to.getBalance(), toAccountBalance.add(amount));
    }

    @ParameterizedTest
    @CsvSource({"0,0,-1", "0,1000,1000", "1000,1000,0", "1000,1000,10000"})
    void testTransfer_NotAllowedParams(BigDecimal fromAccountBalance, BigDecimal toAccountBalance, BigDecimal amount) {

        // Create accounts
        Account from = new Account("Id-123");
        from.setBalance(fromAccountBalance);
        accountsService.createAccount(from);

        Account to = new Account("Id-456");
        to.setBalance(toAccountBalance);
        accountsService.createAccount(to);

        // Transfer the amount throws exception
        assertThrows(TransferException.class, () -> transferService.transfer(from, to, amount));
    }

    @ParameterizedTest(name = "Cover deadlock")
    @CsvSource({"1000,1000,1,1000"})
    void testTransferMoney_CoverDeadlock(BigDecimal account1Balance, BigDecimal account2Balance, BigDecimal amount, Integer numberOfTreads) throws InterruptedException {

        // Let's create 2 accounts and try to emulate deadlock
        Account account1 = new Account("account1-Id");
        account1.setBalance(account1Balance);
        accountsService.createAccount(account1);

        Account account2 = new Account("account2-Id");
        account2.setBalance(account2Balance);
        accountsService.createAccount(account2);

        // Transfer money in parallel mode: account1 -> account2, account2 -> account1
        CountDownLatch latch = new CountDownLatch(numberOfTreads * 2);
        ThreadPoolExecutor from1To2Executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numberOfTreads);
        for (int i = 0; i < numberOfTreads; i++) {
            from1To2Executor.submit(() -> {
                transferService.transfer(account1, account2, amount);
                latch.countDown();
            });
        }
        ThreadPoolExecutor from2To1Executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numberOfTreads);
        for (int i = 0; i < numberOfTreads; i++) {
            from2To1Executor.submit(() -> {
                transferService.transfer(account2, account1, amount);
                latch.countDown();
            });
        }

        // Wait for all threads to finish
        from1To2Executor.shutdown();
        from2To1Executor.shutdown();

        // Set up wait timeout
        assertTrue(latch.await(numberOfTreads, TimeUnit.SECONDS), "Deadlock detected");

        // Final balances equal to initial balances
        assertEquals(account1Balance, account1.getBalance(), "account1 balance is not equal to the initial (account1.getBalance()=" + account1.getBalance() + ", account2.getBalance()=" + account2.getBalance() + ")");
        assertEquals(account2Balance, account2.getBalance(), "account2 balance is not equal to the initial (account1.getBalance()=" + account1.getBalance() + ", account2.getBalance()=" + account2.getBalance() + ")");
    }

    @ParameterizedTest(name = "Transfer money between 3 different accounts in parallel mode")
    @CsvSource({"2000,2000,2000,1,1000"})
    void testTransferMoney_ConcurrentTransfer(BigDecimal account1Balance, BigDecimal account2Balance, BigDecimal account3Balance, BigDecimal amount, Integer numberOfTreads) throws InterruptedException {

        // Let's create 3 different accounts and transfer money between them in parallel
        Account account1 = new Account("account1-Id");
        account1.setBalance(account1Balance);
        accountsService.createAccount(account1);

        Account account2 = new Account("account2-Id");
        account2.setBalance(account2Balance);
        accountsService.createAccount(account2);

        Account account3 = new Account("account3-Id");
        account3.setBalance(account3Balance);
        accountsService.createAccount(account3);

        // account1 -> account2, account1 -> account3
        CountDownLatch latch = new CountDownLatch(numberOfTreads * 6);
        ThreadPoolExecutor from1To2Executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numberOfTreads);
        for (int i = 0; i < numberOfTreads; i++) {
            from1To2Executor.submit(() -> {
                transferService.transfer(account1, account2, amount);
                latch.countDown();
            });
        }
        ThreadPoolExecutor from1To3Executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numberOfTreads);
        for (int i = 0; i < numberOfTreads; i++) {
            from1To3Executor.submit(() -> {
                transferService.transfer(account1, account3, amount);
                latch.countDown();
            });
        }

        // account2 -> account1, account2 -> account3
        ThreadPoolExecutor from2To1Executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numberOfTreads);
        for (int i = 0; i < numberOfTreads; i++) {
            from2To1Executor.submit(() -> {
                transferService.transfer(account2, account1, amount);
                latch.countDown();
            });
        }
        ThreadPoolExecutor from2To3Executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numberOfTreads);
        for (int i = 0; i < numberOfTreads; i++) {
            from2To3Executor.submit(() -> {
                transferService.transfer(account2, account3, amount);
                latch.countDown();
            });
        }

        // account3 -> account1, account3 -> account2
        ThreadPoolExecutor from3To1Executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numberOfTreads);
        for (int i = 0; i < numberOfTreads; i++) {
            from3To1Executor.submit(() -> {
                transferService.transfer(account3, account1, amount);
                latch.countDown();
            });
        }
        ThreadPoolExecutor from3To2Executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numberOfTreads);
        for (int i = 0; i < numberOfTreads; i++) {
            from3To2Executor.submit(() -> {
                transferService.transfer(account3, account2, amount);
                latch.countDown();
            });
        }

        // Wait for all threads to finish
        from1To2Executor.shutdown();
        from1To3Executor.shutdown();
        from2To1Executor.shutdown();
        from2To3Executor.shutdown();
        from3To1Executor.shutdown();
        from3To2Executor.shutdown();

        // Set up wait timeout
        assertTrue(latch.await(numberOfTreads, java.util.concurrent.TimeUnit.SECONDS), "Deadlock detected");

        // Final balances equal to initial balances
        assertEquals(account1Balance, account1.getBalance(), "account1 balance is not equal to the initial (account1.getBalance()=" + account1.getBalance() + ", account2.getBalance()=" + account2.getBalance() + ", account3.getBalance()=" + account3.getBalance() + ")");
        assertEquals(account2Balance, account2.getBalance(), "account2 balance is not equal to the initial (account1.getBalance()=" + account1.getBalance() + ", account2.getBalance()=" + account2.getBalance() + ", account3.getBalance()=" + account3.getBalance() + ")");
        assertEquals(account3Balance, account3.getBalance(), "account3 balance is not equal to the initial (account1.getBalance()=" + account1.getBalance() + ", account2.getBalance()=" + account2.getBalance() + ", account3.getBalance()=" + account3.getBalance() + ")");
    }

}