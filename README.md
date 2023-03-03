Kill Bill Braintree demo
=====================

Inspired from the [Braintree Drop-In implementation](https://developer.paypal.com/braintree/docs/start/drop-in).

Prerequisites
-------------

* Kill Bill is [already setup](https://docs.killbill.io/latest/getting_started.html)
* The default tenant (bob/lazar) has been created
* The [Braintree plugin](https://github.com/killbill/killbill-braintree) is installed and configured

Set up
------

Obtain Braintree credentials as explained [here](https://github.com/killbill/killbill-braintree#configuration) and set the corresponding values in the `application.properties` file.



Run
---

To run the app:

```
mvn spring-boot:run
```

Test 
----

1. Go to [http://localhost:8082/](http://localhost:8082/).
2. Enter amount as `20`.  Click on **Card** and enter the following card details: 
  * Card Number: 4111111111111111
  * Expiry Date: 12/29
![Screen 1](doc-assets/screen1.png)  
3. Click on **Checkout**:
4. This should display a successful payment page:
![Screen 2](doc-assets/screen2.png)
5. Verify that a new account is created in Kill Bill with a successful payment for the amount specified above.

Credits
----
Based on the [Braintree Java Dropin Integration](https://github.com/braintree/braintree_spring_example).