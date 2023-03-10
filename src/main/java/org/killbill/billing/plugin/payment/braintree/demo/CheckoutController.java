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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
public class CheckoutController {

	@Autowired
	CheckoutService checkoutService;

	private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);

	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String root(Model model) {
		return "redirect:checkouts";
	}

	@RequestMapping(value = "/checkouts", method = RequestMethod.GET)
	public String checkout(Model model) {
		String clientToken = checkoutService.getBraintreeToken();
		logger.info("Token {}", clientToken);
		model.addAttribute("clientToken", clientToken);
		return "checkouts/new";
	}

	@RequestMapping(value = "/checkouts", method = RequestMethod.POST)
	public String postForm(@RequestParam("amount") String amount, @RequestParam("payment_method_nonce") String nonce,
			Model model, final RedirectAttributes redirectAttributes) {
		BigDecimal decimalAmount;
		try {
			decimalAmount = new BigDecimal(amount);
		} catch (NumberFormatException e) {
			redirectAttributes.addFlashAttribute("errorDetails", "Error: 81503: Amount is an invalid format.");
			return "redirect:checkouts";
		}

		logger.info("Amount: {}, Nonce: {}", amount, nonce);
		checkoutService.addPaymentMethodAndChargeCustomer(decimalAmount, nonce);
		return "checkouts/successful";
	}
}
