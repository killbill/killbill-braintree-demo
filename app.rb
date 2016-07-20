require 'json'
require 'yaml'

require 'sinatra'
require 'killbill_client'

set :kb_url, ENV['KB_URL'] || 'http://127.0.0.1:8080'

#
# Kill Bill configuration and helpers
#

KillBillClient.url = settings.kb_url

# Multi-tenancy and RBAC credentials
options = {
    :username => 'admin',
    :password => 'password',
    :api_key => 'bob',
    :api_secret => 'lazar'
}

# Audit log data
user = 'demo'
reason = 'New subscription'
comment = 'Trigger by Sinatra'

def create_kb_account(user, reason, comment, options)
  account = KillBillClient::Model::Account.new
  account.name = 'John Doe'
  account.currency = 'USD'
  account.create(user, reason, comment, options)
end

def create_kb_payment_method(account, bt_nonce, user, reason, comment, options)
  pm = KillBillClient::Model::PaymentMethod.new
  pm.account_id = account.account_id
  pm.plugin_name = 'killbill-braintree_blue'
  pm.plugin_info = {'token' => bt_nonce, 'address1' => '5th Street', 'city' => 'San Francisco', 'zip' => '94111', 'state' => 'CA', 'country' => 'US'}
  pm.create(true, user, reason, comment, options)
end

def create_subscription(account, user, reason, comment, options)
  subscription = KillBillClient::Model::Subscription.new
  subscription.account_id = account.account_id
  subscription.product_name = 'Sports'
  subscription.product_category = 'BASE'
  subscription.billing_period = 'MONTHLY'
  subscription.price_list = 'DEFAULT'
  subscription.price_overrides = []

  # For the demo to be interesting, override the trial price to be non-zero so we trigger a charge in Braintree
  override_trial = KillBillClient::Model::PhasePriceOverrideAttributes.new
  override_trial.phase_type = 'TRIAL'
  override_trial.fixed_price = 10.0
  subscription.price_overrides << override_trial

  subscription.create(user, reason, comment, nil, true, options.dup.merge({ :callTimeoutSec => 10 }))
end

def generate_bt_token(options)
  JSON.parse(KillBillClient::Model::Resource.get('/plugins/killbill-braintree_blue/token', {}, options).response.body)['client_token']
end

def get_bt_merchant_id(options)
  YAML.load(KillBillClient::Model::Tenant.get_tenant_plugin_config('killbill-braintree_blue', options).values.first)[:braintree_blue][:merchant_id]
end

#
# Sinatra handlers
#

get '/' do
  @client_token = generate_bt_token(options)

  erb :index
end

post '/charge' do
  # Create an account
  account = create_kb_account(user, reason, comment, options)

  # Add a payment method associated with the Braintree token
  create_kb_payment_method(account, params[:payment_method_nonce], user, reason, comment, options)

  # Add a subscription
  create_subscription(account, user, reason, comment, options)

  # Retrieve the invoice
  @invoice = account.invoices(true, options).first

  # And the Braintree authorization
  transaction = @invoice.payments(true, 'NONE', options).first.transactions.first
  @authorization = (transaction.properties.find { |p| p.key == 'authorization' }).value

  @merchant_id = get_bt_merchant_id(options)

  erb :charge
end

__END__

@@ layout
  <!DOCTYPE html>
  <html>
  <head></head>
  <body>
    <%= yield %>
  </body>
  </html>

@@index
  <span class="image"><img src="https://drive.google.com/uc?&amp;id=0Bw8rymjWckBHT3dKd0U3a1RfcUE&amp;w=960&amp;h=480" alt="uc?&amp;id=0Bw8rymjWckBHT3dKd0U3a1RfcUE&amp;w=960&amp;h=480"></span>
  <form action="/charge" method="post" style="max-width: 25em;">
    <article>
      <label class="amount">
        <span>Sports car, 30 days trial for only $10.00!</span>
      </label>
      <br/>
      <div id="dropin-container" style="padding-top: 1em;"></div>
      <button class="button" type="submit"><span>Pay with card or PayPal</span></button>
    </article>
    <script src="https://js.braintreegateway.com/v2/braintree.js"></script>
    <script>
      (function () {
        var client_token = "<%= @client_token %>";
        braintree.setup(client_token, "dropin", {
          container: "dropin-container"
        });
      })()
    </script>
  </form>

@@charge
  <h2>Thanks! Here is your invoice:</h2>
  <ul>
    <% @invoice.items.each do |item| %>
      <li><%= "subscription_id=#{item.subscription_id}, amount=#{item.amount}, phase=sports-monthly-trial, start_date=#{item.start_date}" %></li>
    <% end %>
  </ul>
  You can verify the payment at <a href="<%= "https://sandbox.braintreegateway.com/merchants/#{@merchant_id}/transactions/#{@authorization}" %>"><%= "https://sandbox.braintreegateway.com/merchants/#{@merchant_id}/transactions/#{@authorization}" %></a>.
