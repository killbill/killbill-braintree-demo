/*
 * Copyright 2020-2023 Equinix, Inc
 * Copyright 2014-2023 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.killbill.billing.plugin.payment.braintree.demo;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.KillBillHttpClient;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.RequestOptions.RequestOptionsBuilder;
import org.killbill.billing.client.api.gen.AccountApi;
import org.killbill.billing.client.api.gen.InvoiceApi;
import org.killbill.billing.client.model.InvoiceItems;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.InvoiceItem;
import org.killbill.billing.client.model.gen.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Customer;
import com.braintreegateway.CustomerRequest;
import com.braintreegateway.Result;


@Service
public class CheckoutService {

	@Value("${killbill.client.url}")
	private String killbillClientUrl;

	@Value("${killbill.client.disable-ssl-verification}")
	private String killbillClientDisableSSL;

	@Value("${killbill.username}")
	private String username;

	@Value("${killbill.password}")
	private String password;

	@Value("${killbill.api-key}")
	private String apiKey;

	@Value("${killbill.api-secret}")
	private String apiSecret;

	@Value("${plugin.name}")
	private String pluginName;

	@Value("${checkoutUrl}")
	private String checkoutUrl;
	
	@Value("${braintree.environment}")
	private String environment;

	@Value("${braintree.merchantId}")
	private String merchantId;

	@Value("${braintree.publicKey}")
	private String publicKey;	
	
	@Value("${braintree.privateKey}")
	private String privateKey;			
	
	public static final String PROPERTY_BT_CUSTOMER_ID = "bt_customer_id";
	public static final String PROPERTY_BT_NONCE = "bt_nonce";

	private AccountApi accountApi;
	private InvoiceApi invoiceApi;
	private KillBillHttpClient httpClient;
	private RestTemplate restTemplate = new RestTemplate();
	
	private BraintreeGateway gateway;

	private static final Logger logger = LoggerFactory.getLogger(CheckoutService.class);

	@PostConstruct
	public void init() {
		httpClient = new KillBillHttpClient(killbillClientUrl, username, password, apiKey, apiSecret);
		accountApi = new AccountApi(httpClient);
		invoiceApi = new InvoiceApi(httpClient);
		gateway = new BraintreeGateway(environment, merchantId, publicKey, privateKey);
	}

	private RequestOptions getOptions() {
		RequestOptionsBuilder builder = new RequestOptionsBuilder();
		builder.withComment("Braintree Demo").withCreatedBy("demo").withReason("Demonstrating Braintree Drop In");
		return builder.build();
	}

	public String getBraintreeToken() {
		ResponseEntity<String> token = restTemplate.exchange(checkoutUrl, HttpMethod.GET,
				new HttpEntity<Object>(getHeaders()), String.class);
		String tokenStr = token.getBody();
		if (tokenStr.startsWith("\"") && tokenStr.endsWith("\"")) {
			String trimmedToken = tokenStr.substring(1, tokenStr.length() - 1);
			return trimmedToken;
		}
		return tokenStr;
	}
	
	public void addPaymentMethodAndChargeCustomer(BigDecimal amount, String nonce) {
		try {
			String braintreeCustomerId = createBraintreeCustomer();
			logger.info("Braintree customerId: {}",braintreeCustomerId);
			
			Account account = createKBAccount();
			logger.info("Kill Bill AccountId:{}", account.getAccountId());
			
			Map<String, String> pluginProperties = new HashMap<String, String>();
			pluginProperties.put(PROPERTY_BT_NONCE, nonce);
			pluginProperties.put(PROPERTY_BT_CUSTOMER_ID, braintreeCustomerId);
			PaymentMethod paymentMethod = createKBPaymentMethod(account.getAccountId(), pluginProperties);
			logger.info("Payment Method Id: {}", paymentMethod.getPaymentMethodId());
			
			InvoiceItems createdCharges = createExternalCharge(account.getAccountId(), amount);
			logger.info("Invoice Id: {}", createdCharges.get(0).getInvoiceId());
			
		} catch (KillBillClientException e) {
			logger.error("Error while creating account/payment method/external charge", e);
		}
	}
	
	
	private String createBraintreeCustomer() {
		CustomerRequest request = new CustomerRequest().firstName("John").lastName("Doe");
		Result<Customer> result = gateway.customer().create(request);
		String customerId = result.getTarget().getId();
		return customerId;
	}	
	
	/**
	 * @return
	 * @throws KillBillClientException
	 */
	private Account createKBAccount() throws KillBillClientException {
		Account body = new Account();
		body.setEmail("john@laposte.com");
		body.setName("John Doe");
		body.setCurrency(Currency.USD);
		return accountApi.createAccount(body, getOptions());
	}	

	private PaymentMethod createKBPaymentMethod(UUID accountId, Map<String, String> pluginProperties)
			throws KillBillClientException {
		PaymentMethod pm = new PaymentMethod();
		pm.setAccountId(accountId);
		pm.setPluginName(pluginName);
		PaymentMethod paymentMethod = accountApi.createPaymentMethod(accountId, pm, null, pluginProperties, getOptions());
		Map<String, String> NULL_PLUGIN_PROPERTIES = null;
		accountApi.setDefaultPaymentMethod(accountId, paymentMethod.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, getOptions());
		return paymentMethod;
	}
	
	private InvoiceItems createExternalCharge(UUID accountId, BigDecimal amount) throws KillBillClientException {
		InvoiceItem externalCharge = new InvoiceItem();
		externalCharge.setAccountId(accountId);
		externalCharge.setAmount(amount);
		externalCharge.setDescription("Braintree Demo Charge");

		InvoiceItems externalCharges = new InvoiceItems();
		externalCharges.add(externalCharge);			
		
		Map<String, String> NULL_PLUGIN_PROPERTIES = null;
		InvoiceItems createdCharges = invoiceApi.createExternalCharges(accountId, externalCharges, null, true, NULL_PLUGIN_PROPERTIES, getOptions());
		return createdCharges;
		
	}
	
	private HttpHeaders getHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("X-Killbill-ApiKey", apiKey);
		headers.add("X-Killbill-ApiSecret", apiSecret);
		headers.add("X-Killbill-CreatedBy", "test");
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBasicAuth(username, password);
		return headers;
	}

}
