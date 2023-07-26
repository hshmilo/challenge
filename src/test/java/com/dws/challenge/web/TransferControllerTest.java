package com.dws.challenge.web;

import com.dws.challenge.domain.Account;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.NotificationService;
import com.dws.challenge.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dws.challenge.web.TransferController.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@WebAppConfiguration
class TransferControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountsService accountsService;

    @MockBean
    private NotificationService notificationService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    void prepareMockMvc() {
        this.mockMvc = webAppContextSetup(this.webApplicationContext).build();
        // Reset the existing accounts before each test.
        accountsService.getAccountsRepository().clearAccounts();
    }

    @Test
    void fromAccountNotExists() throws Exception {
        mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"to-Id-456\",\"balance\":1000}")).andExpect(status().isCreated());

        mockMvc.perform(post("/v1/transfer").contentType(MediaType.APPLICATION_JSON)
                        .param("fromId", "from-Id-123")
                        .param("toId", "to-Id-456")
                        .param("amount", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void toAccountNotExists() throws Exception {
        mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"from-Id-456\",\"balance\":1000}")).andExpect(status().isCreated());

        mockMvc.perform(post("/v1/transfer").contentType(MediaType.APPLICATION_JSON)
                        .param("from", "from-Id-123")
                        .param("to", "to-Id-456")
                        .param("amount", "10"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({".", "0", "-1", "1000000"})
    void amountIsWrong(String amount) throws Exception {
        mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"from-Id-456\",\"balance\":1000}")).andExpect(status().isCreated());
        mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"to-Id-456\",\"balance\":1000}")).andExpect(status().isCreated());

        mockMvc.perform(post("/v1/transfer").contentType(MediaType.APPLICATION_JSON)
                        .param("from", "from-Id-123")
                        .param("to", "to-Id-456")
                        .param("amount", amount))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"1000,1000,10", "1000,1000,1000", "1,0,1"})
    void transferEndpoint_AllowedParams(BigDecimal fromAccountBalance, BigDecimal toAccountBalance, BigDecimal amount) throws Exception {

        // Create accounts
        Account fromAccount = new Account("from-Id-123");
        fromAccount.setBalance(fromAccountBalance);
        accountsService.createAccount(fromAccount);

        Account toAccount = new Account("to-Id-456");
        toAccount.setBalance(toAccountBalance);
        accountsService.createAccount(toAccount);

        // Collect notifications in the NotificationsService mock
        Map<String, List<String>> notifications = new HashMap<>();
        Mockito.doAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            String message = invocation.getArgument(1);

            List<String> messages = notifications.get(account.getAccountId());
            if (messages == null) {
                messages = new ArrayList<>();
                notifications.put(account.getAccountId(), messages);
            }
            messages.add(message);
            return null;
        }).when(notificationService).notifyAboutTransfer(Mockito.any(Account.class), Mockito.anyString());

        // Perform the transfer
        MvcResult result = mockMvc.perform(post("/v1/transfer").contentType(MediaType.APPLICATION_JSON)
                        .param("from", "from-Id-123")
                        .param("to", "to-Id-456")
                        .param("amount", amount.toString()))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(TRANSFERRED_SUCCESSFULLY, result.getResponse().getContentAsString());

        // Check the balances
        assertEquals(fromAccount.getBalance(), fromAccountBalance.subtract(amount));
        assertEquals(toAccount.getBalance(), toAccountBalance.add(amount));

        // Check the notifications
        assertThat(notifications).hasSize(2);

        assertThat(notifications.get("from-Id-123")).hasSize(1);
        assertEquals(String.format(TRANSFERRED_NOTIFICATION, amount, toAccount), notifications.get("from-Id-123").get(0));

        assertThat(notifications.get("to-Id-456")).hasSize(1);
        assertEquals(String.format(RECEIVED_NOTIFICATION, amount, fromAccount), notifications.get("to-Id-456").get(0));
    }
}