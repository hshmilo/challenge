package com.dws.challenge.web;

import com.dws.challenge.domain.Account;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.NotificationService;
import com.dws.challenge.service.TransferService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.math.BigDecimal;

@RestController
@RequestMapping("/v1/transfer")
@Slf4j
@AllArgsConstructor
public class TransferController {

    public static final String TRANSFERRED_SUCCESSFULLY = "Transferred successfully!";
    public static final String TRANSFERRED_NOTIFICATION = "Transferred amount %s to account %s";
    public static final String RECEIVED_NOTIFICATION = "Received amount %s from account %s";

    private final TransferService transferService;
    private final NotificationService notificationService;
    private final AccountsService accountsService;

    @PostMapping
    public ResponseEntity<String> transfer(@RequestParam @NotEmpty String from,
                                           @RequestParam @NotEmpty String to,
                                           @RequestParam @Min(0) BigDecimal amount) {

        Account fromAccount = accountsService.getAccount(from);
        if (fromAccount == null) {
            return ResponseEntity.badRequest().body("Account \"from\" " + from + " does not exist");
        }
        Account toAccount = accountsService.getAccount(to);
        if (toAccount == null) {
            return ResponseEntity.badRequest().body("Account \"to\" "  + to + " does not exist");
        }

        transferService.transfer(fromAccount, toAccount, amount);

        // Send notification about transfer
        try {
            notificationService.notifyAboutTransfer(fromAccount, String.format(TRANSFERRED_NOTIFICATION, amount, toAccount));
        } catch (Exception e) {
            log.error("Notification error while transferring from account {}", from, e);
        }
        try {
            notificationService.notifyAboutTransfer(toAccount, String.format(RECEIVED_NOTIFICATION, amount, fromAccount));
        } catch (Exception e) {
            log.error("Notification error while transferring to account {}", to, e);
        }

        return ResponseEntity.ok(TRANSFERRED_SUCCESSFULLY);
    }
}
