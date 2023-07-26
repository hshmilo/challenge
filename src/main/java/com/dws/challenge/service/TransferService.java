package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.TransferException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class TransferService {
    public void transfer(Account from, Account to, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransferException("The amount to transfer should be a positive number!");
        }

        if (from.getAccountId().equals(to.getAccountId())) {
            throw new TransferException(String.format("Account \"from\" %s and Account \"to\" %s should be different!",
                    from.getAccountId(), to.getAccountId()));
        }

        // Acquire locks by Account id to avoid deadlock
        Account account1 = null;
        Account account2 = null;

        if (from.getAccountId().compareTo(to.getAccountId()) > 0) {
            account1 = from;
            account2 = to;
        } else {
            account1 = to;
            account2 = from;
        }

        synchronized (account1) {
            synchronized (account2) {
                BigDecimal fromBalance = from.getBalance();
                if (fromBalance.compareTo(amount) < 0) {
                    throw new TransferException(String.format("The account %s will end" +
                            "up with negative balance. Overdrafts do not supported!)", from.getAccountId()));
                }
                from.setBalance(fromBalance.add(amount.negate()));

                BigDecimal toBalance = to.getBalance();
                to.setBalance(toBalance.add(amount));
            }
        }

        log.debug("Transfer completed: from={}, to={},  amount='{}'", amount, from.getAccountId(), to.getAccountId());


    }
}
